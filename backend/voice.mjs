// Audio has its own revision: painting must never silently overwrite a recording.
export class VoiceStore {
  constructor(diaries) {
    this.diaries=diaries;this.db=diaries.db;
    this.db.exec(`CREATE TABLE IF NOT EXISTS page_voice (
      page_id TEXT PRIMARY KEY REFERENCES pages(id), revision INTEGER NOT NULL,
      mime TEXT, audio BLOB, duration INTEGER, cues TEXT);`);
  }
  get(token,id) {
    this.diaries.page(token,id);
    const row=this.db.prepare('SELECT revision,mime,duration,cues,length(audio) AS bytes FROM page_voice WHERE page_id=?').get(id);
    return {revision:row?.revision??0,recording:row?.mime?{mime:row.mime,duration:row.duration,bytes:row.bytes,cues:JSON.parse(row.cues),mode:'guided-parent-demo',visibility:'family-private'}:null};
  }
  audio(token,id) {
    this.diaries.page(token,id);
    const row=this.db.prepare('SELECT mime,audio FROM page_voice WHERE page_id=?').get(id);
    if(!row?.mime)throw new Error('VOICE_NOT_FOUND');return row;
  }
  write(token,id,body,remove=false) {
    this.diaries.page(token,id);
    if(!Number.isSafeInteger(body?.revision)||body.revision<0)throw new Error('INVALID_VOICE');
    let bytes=null;
    if(!remove){
      if(!['audio/webm','audio/ogg','audio/mp4'].includes(body.mime)||typeof body.audio!=='string'||body.audio.length>2796204||(body.audio.length%4!==0||!/^[A-Za-z0-9+/]*={0,2}$/.test(body.audio)))throw new Error('INVALID_VOICE');
      bytes=Buffer.from(body.audio,'base64');
      if(bytes.length<12||bytes.length>2097152||bytes.toString('base64')!==body.audio)throw new Error('INVALID_VOICE');
      const signature=body.mime==='audio/webm'?bytes.subarray(0,4).toString('hex')==='1a45dfa3':body.mime==='audio/ogg'?bytes.subarray(0,4).toString()==='OggS':bytes.subarray(4,8).toString()==='ftyp';
      if(!signature||!Number.isInteger(body.duration)||body.duration<1||body.duration>120000||!Array.isArray(body.cues)||body.cues.length>4)throw new Error('INVALID_VOICE');
      let previous=-1;
      for(const cue of body.cues){if(!Number.isInteger(cue?.question)||cue.question<0||cue.question>3||!Number.isInteger(cue.at)||cue.at<previous||cue.at<0||cue.at>body.duration)throw new Error('INVALID_VOICE');previous=cue.at;}
    }
    this.db.exec('BEGIN IMMEDIATE');
    try{
      const current=this.get(token,id);if(current.revision!==body.revision)throw new Error('VOICE_CONFLICT');
      this.db.prepare(`INSERT INTO page_voice VALUES (?,?,?,?,?,?) ON CONFLICT(page_id) DO UPDATE SET
        revision=excluded.revision,mime=excluded.mime,audio=excluded.audio,duration=excluded.duration,cues=excluded.cues`)
        .run(id,current.revision+1,remove?null:body.mime,bytes,remove?null:body.duration,remove?null:JSON.stringify(body.cues));
      this.db.exec('COMMIT');return this.get(token,id);
    }catch(error){this.db.exec('ROLLBACK');throw error;}
  }
}
