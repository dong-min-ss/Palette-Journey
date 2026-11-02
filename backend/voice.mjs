import { createHash } from 'node:crypto';
// Audio has its own revision: painting must never silently overwrite a recording.
export class VoiceStore {
  constructor(diaries) {
    this.diaries=diaries;this.db=diaries.db;
    this.db.exec(`CREATE TABLE IF NOT EXISTS page_voice (
      page_id TEXT PRIMARY KEY REFERENCES pages(id), revision INTEGER NOT NULL,
      mime TEXT, audio BLOB, duration INTEGER, cues TEXT);`);
    this.db.exec('CREATE TABLE IF NOT EXISTS page_conversation (page_id TEXT PRIMARY KEY,voice_revision INTEGER NOT NULL,version INTEGER NOT NULL,audio BLOB NOT NULL)');
    const columns=this.db.prepare('PRAGMA table_info(page_voice)').all().map(c=>c.name);
    for(const [name,type] of [['mode','TEXT'],['upload_id','TEXT'],['upload_hash','TEXT']])if(!columns.includes(name))this.db.exec(`ALTER TABLE page_voice ADD COLUMN ${name} ${type}`);
  }
  get(token,id) {
    this.diaries.page(token,id);
    const row=this.db.prepare('SELECT revision,mime,duration,cues,mode,upload_id,length(audio) AS bytes FROM page_voice WHERE page_id=?').get(id);
    return {revision:row?.revision??0,recording:row?.mime?{mime:row.mime,duration:row.duration,bytes:row.bytes,cues:JSON.parse(row.cues),mode:row.mode??'guided-parent-demo',...(row.upload_id?{uploadId:row.upload_id}:{}),visibility:'family-private'}:null};
  }
  audio(token,id) {
    this.diaries.page(token,id);
    const row=this.db.prepare('SELECT revision,mime,audio FROM page_voice WHERE page_id=?').get(id);
    if(!row?.mime)throw new Error('VOICE_NOT_FOUND');return row;
  }
  write(token,id,body,remove=false,vehicle=false) {
    this.diaries.page(token,id);
    if(!Number.isSafeInteger(body?.revision)||body.revision<0)throw new Error('INVALID_VOICE');
    let bytes=null,uploadHash=null;
    if(!remove){
      if(!(vehicle?body.mime==='audio/wav':['audio/webm','audio/ogg','audio/mp4'].includes(body.mime))||typeof body.audio!=='string'||body.audio.length>(vehicle?5120060:2796204)||(body.audio.length%4!==0||!/^[A-Za-z0-9+/]*={0,2}$/.test(body.audio)))throw new Error('INVALID_VOICE');
      bytes=Buffer.from(body.audio,'base64');
      if(bytes.length<12||bytes.length>(vehicle?3840044:2097152)||bytes.toString('base64')!==body.audio)throw new Error('INVALID_VOICE');
      const signature=vehicle?validVehicleWav(bytes,body):body.mime==='audio/webm'?bytes.subarray(0,4).toString('hex')==='1a45dfa3':body.mime==='audio/ogg'?bytes.subarray(0,4).toString()==='OggS':bytes.subarray(4,8).toString()==='ftyp';
      if(!signature||!Number.isInteger(body.duration)||body.duration<1||body.duration>120000||!Array.isArray(body.cues)||body.cues.length>4)throw new Error('INVALID_VOICE');
      let previous=-1;
      for(const cue of body.cues){if(!Number.isInteger(cue?.question)||cue.question<0||cue.question>3||!Number.isInteger(cue.at)||cue.at<previous||cue.at<0||cue.at>body.duration)throw new Error('INVALID_VOICE');previous=cue.at;
        if(vehicle&&(typeof cue.questionText!=='string'||!cue.questionText.trim()||cue.questionText.length>300||typeof cue.answerText!=='string'||cue.answerText.length>2000||typeof cue.skipped!=='boolean'))throw new Error('INVALID_VOICE');
      }
      if(vehicle){
        if(typeof body.uploadId!=='string'||!/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(body.uploadId)||body.cues.length<1||body.cues[0].at!==0||body.cues.every(c=>c.skipped))throw new Error('INVALID_VOICE');
        for(let i=0;i<body.cues.length;i++){const cue=body.cues[i],end=body.cues[i+1]?.at??body.duration;if(cue.question!==i||(cue.skipped?end-cue.at>1:!cue.answerText.trim()||end-cue.at>30001))throw new Error('INVALID_VOICE');}
        uploadHash=createHash('sha256').update(bytes).update(JSON.stringify({duration:body.duration,cues:body.cues})).digest('hex');
      }
    }
    this.db.exec('BEGIN IMMEDIATE');
    try{
      const current=this.get(token,id);
      if(vehicle){const prior=this.db.prepare('SELECT upload_id,upload_hash FROM page_voice WHERE page_id=?').get(id);if(prior?.upload_id===body.uploadId&&prior.upload_hash===uploadHash){this.db.exec('COMMIT');return current;}if(current.recording)throw new Error('VOICE_CONFLICT');}
      if(current.revision!==body.revision)throw new Error('VOICE_CONFLICT');
      this.db.prepare(`INSERT INTO page_voice (page_id,revision,mime,audio,duration,cues,mode,upload_id,upload_hash) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(page_id) DO UPDATE SET
        revision=excluded.revision,mime=excluded.mime,audio=excluded.audio,duration=excluded.duration,cues=excluded.cues,mode=excluded.mode,upload_id=excluded.upload_id,upload_hash=excluded.upload_hash`)
        .run(id,current.revision+1,remove?null:body.mime,bytes,remove?null:body.duration,remove?null:JSON.stringify(body.cues),remove?null:vehicle?'vehicle-answer-original':'guided-parent-demo',vehicle?body.uploadId:null,uploadHash);
      this.db.prepare('DELETE FROM page_conversation WHERE page_id=?').run(id);
      this.db.exec('COMMIT');return this.get(token,id);
    }catch(error){this.db.exec('ROLLBACK');throw error;}
  }
}

// Only the app's canonical 16 kHz mono PCM16 WAV is accepted; no arbitrary RIFF chunks.
function validVehicleWav(bytes,body){
 if(bytes.length<46||bytes.toString('ascii',0,4)!=='RIFF'||bytes.readUInt32LE(4)!==bytes.length-8||bytes.toString('ascii',8,16)!=='WAVEfmt '||bytes.readUInt32LE(16)!==16||bytes.readUInt16LE(20)!==1||bytes.readUInt16LE(22)!==1||bytes.readUInt32LE(24)!==16000||bytes.readUInt32LE(28)!==32000||bytes.readUInt16LE(32)!==2||bytes.readUInt16LE(34)!==16||bytes.toString('ascii',36,40)!=='data'||bytes.readUInt32LE(40)!==bytes.length-44||(bytes.length-44)%2!==0)return false;
 return body.duration===Math.max(1,Math.floor((bytes.length-44)*1000/32000));
}
