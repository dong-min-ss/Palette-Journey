import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes,randomUUID} from 'node:crypto';
import {mkdtempSync,rmSync} from 'node:fs';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {AccountStore} from '../backend/store.mjs';
import {DiaryStore} from '../backend/diaries.mjs';
import {VoiceStore} from '../backend/voice.mjs';
import {createApp} from '../backend/server.mjs';
const token=()=>randomBytes(32).toString('hex');
function wav(ms=1000){const n=ms*32,b=Buffer.alloc(n+44);b.write('RIFF');b.writeUInt32LE(n+36,4);b.write('WAVEfmt ',8);b.writeUInt32LE(16,16);b.writeUInt16LE(1,20);b.writeUInt16LE(1,22);b.writeUInt32LE(16000,24);b.writeUInt32LE(32000,28);b.writeUInt16LE(2,32);b.writeUInt16LE(16,34);b.write('data',36);b.writeUInt32LE(n,40);return b;}
function body(ms=1000){return {revision:0,mime:'audio/wav',audio:wav(ms).toString('base64'),duration:ms,uploadId:randomUUID(),cues:[{question:0,at:0,questionText:'시험 질문',answerText:'합성 답변',skipped:false}]};}
function setup(file=':memory:'){const store=new AccountStore(file),owner=token(),member=token();store.register(owner);const session=store.createSession(owner);store.joinSession(member,session.code);const diaries=new DiaryStore(store),voice=new VoiceStore(diaries),page=diaries.library(member).pages[0].id;return {store,owner,member,session,diaries,voice,page};}
test('vehicle upload retains original PCM, metadata and ownership; exact retry does not increment revision',()=>{
 const {store,member,session,diaries,voice,page}=setup();try{const before=diaries.page(member,page),value=body(),result=voice.write(member,page,value,false,true);assert.equal(result.recording.mode,'vehicle-answer-original');assert.throws(()=>voice.write(member,page,{...value,cues:[{...value.cues[0],answerText:'수정된 답변'}]},false,true),/VOICE_CONFLICT/);assert.deepEqual(Buffer.from(voice.audio(member,page).audio),wav());assert.deepEqual(result.recording.cues,value.cues);assert.equal(voice.write(member,page,value,false,true).revision,1);assert.deepEqual(diaries.page(member,page),before);
 assert.throws(()=>voice.write(member,page,{...value,uploadId:randomUUID(),revision:1},false,true),/VOICE_CONFLICT/);
 const owner=token(),foreign=token();store.register(owner);store.joinSession(foreign,store.createSession(owner).code);assert.throws(()=>voice.audio(foreign,page),/DIARY_NOT_FOUND/);
 voice.write(member,page,{revision:1},true);assert.throws(()=>voice.write(member,page,value,false,true),/VOICE_CONFLICT/);assert.throws(()=>voice.audio(member,page),/VOICE_NOT_FOUND/);
 }finally{store.close();}
});
test('vehicle WAV validation rejects corrupt lengths, unsupported channels and duration without replacing a web recording',()=>{
 const {store,member,voice,page}=setup();try{const base=body();assert.throws(()=>voice.write(member,page,base),/INVALID_VOICE/);for(const mutate of [b=>b.writeUInt32LE(99,4),b=>b.writeUInt16LE(2,22),b=>b.writeUInt32LE(44100,24),b=>b.write('JUNK',36)]){const b=wav();mutate(b);assert.throws(()=>voice.write(member,page,{...base,audio:b.toString('base64')},false,true),/INVALID_VOICE/);}
 for(const bad of [{duration:2000},{uploadId:'invalid'},{cues:[]},{cues:[{question:0,at:0}]}])assert.throws(()=>voice.write(member,page,{...base,...bad},false,true),/INVALID_VOICE/);
 const web=Buffer.alloc(20);web.write('OggS');voice.write(member,page,{revision:0,mime:'audio/ogg',audio:web.toString('base64'),duration:1000,cues:[]});assert.throws(()=>voice.write(member,page,{...base,revision:1},false,true),/VOICE_CONFLICT/);assert.equal(voice.get(member,page).recording.mode,'guided-parent-demo');
 }finally{store.close();}
});
test('vehicle recording and retry receipt survive database reopening; deleted recording stays deleted',()=>{
 const dir=mkdtempSync(join(tmpdir(),'pj-vehicle-voice-'));let store;try{const file=join(dir,'test.sqlite'),fixture=setup(file);({store}=fixture);const {member,page}=fixture,value=body();fixture.voice.write(member,page,value,false,true);store.close();store=new AccountStore(file);const voice=new VoiceStore(new DiaryStore(store));assert.equal(voice.write(member,page,value,false,true).revision,1);assert.deepEqual(Buffer.from(voice.audio(member,page).audio),wav());voice.write(member,page,{revision:1},true);store.close();store=new AccountStore(file);const reopened=new VoiceStore(new DiaryStore(store));assert.equal(reopened.get(member,page).recording,null);assert.throws(()=>reopened.write(member,page,value,false,true),/VOICE_CONFLICT/);}finally{store?.close();rmSync(dir,{recursive:true,force:true});}
});
test('HTTP accepts four full original answers, serves authorized WAV bytes and rejects cross-family reads',async()=>{
 const {store,owner,member,session,page}=setup(),server=createApp(store);await new Promise(r=>server.listen(0,'127.0.0.1',r));const base='http://127.0.0.1:'+server.address().port;
 try{const value=body(120000);value.cues=Array.from({length:4},(_,i)=>({question:i,at:i*30000,questionText:'시험 질문',answerText:'합성 답변',skipped:false}));const headers={Authorization:'Bearer '+member,'Content-Type':'application/json'};const put=()=>fetch(base+'/api/session/pages/'+page+'/voice/vehicle',{method:'PUT',headers,body:JSON.stringify(value)});
 assert.equal((await put()).status,200);assert.equal((await(await put()).json()).revision,1);
 const audio=await fetch(base+'/api/session/pages/'+page+'/voice/audio',{headers});assert.equal(audio.headers.get('content-type'),'audio/wav');assert.deepEqual(Buffer.from(await audio.arrayBuffer()),wav(120000));
 assert.equal((await fetch(base+'/api/session/pages/'+page+'/voice/audio',{headers:{Authorization:'Bearer '+token()}})).status,401);
 store.revokeSession(owner,session.id);assert.equal((await put()).status,401);
 }finally{await new Promise(r=>server.close(r));store.close();}
});

test('legacy six-column voice table migrates without changing existing recording',()=>{
 const store=new AccountStore(':memory:');try{store.db.exec('CREATE TABLE page_voice (page_id TEXT PRIMARY KEY, revision INTEGER NOT NULL,mime TEXT,audio BLOB,duration INTEGER,cues TEXT)');store.db.prepare('INSERT INTO page_voice VALUES (?,?,?,?,?,?)').run('legacy',3,'audio/ogg',Buffer.from('OggS123456789012'),1000,'[]');new VoiceStore(new DiaryStore(store));const row=store.db.prepare('SELECT * FROM page_voice WHERE page_id=?').get('legacy');assert.equal(row.revision,3);assert.equal(row.mime,'audio/ogg');assert.equal(row.mode,null);assert.equal(row.audio.length,16);}finally{store.close();}
});
