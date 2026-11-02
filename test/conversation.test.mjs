import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes,randomUUID} from 'node:crypto';
import {AccountStore} from '../backend/store.mjs';
import {DiaryStore} from '../backend/diaries.mjs';
import {VoiceStore} from '../backend/voice.mjs';
import {ConversationStore,composeConversation} from '../backend/conversation.mjs';
import {createApp} from '../backend/server.mjs';
const token=()=>randomBytes(32).toString('hex');
function pcm(ms,value){const b=Buffer.alloc(ms*32);for(let i=0;i<b.length;i+=2)b.writeInt16LE(value,i);return b;}
function wav(data){const b=Buffer.alloc(44);b.write('RIFF');b.writeUInt32LE(data.length+36,4);b.write('WAVEfmt ',8);b.writeUInt32LE(16,16);b.writeUInt16LE(1,20);b.writeUInt16LE(1,22);b.writeUInt32LE(16000,24);b.writeUInt32LE(32000,28);b.writeUInt16LE(2,32);b.writeUInt16LE(16,34);b.write('data',36);b.writeUInt32LE(data.length,40);return Buffer.concat([b,data]);}
function fixture(){const store=new AccountStore(':memory:'),owner=token(),member=token();store.register(owner);const session=store.createSession(owner);store.joinSession(member,session.code);const diaries=new DiaryStore(store),voice=new VoiceStore(diaries),library=diaries.library(member),page=library.pages[0].id,original=wav(pcm(1000,1000));voice.write(member,page,{revision:0,mime:'audio/wav',audio:original.toString('base64'),duration:1000,uploadId:randomUUID(),cues:[{question:0,at:0,questionText:'시험 질문',answerText:'가상 답변',skipped:false}]},false,true);return {store,owner,member,session,diaries,voice,page,original};}
test('single conversation WAV alternates quiet questions and byte-identical answers including skipped cues',()=>{
 const first=pcm(500,1000),second=pcm(500,500),original=wav(Buffer.concat([first,second])),question=pcm(1000,10000);
 const recording={duration:1000,cues:[{at:0,skipped:false},{at:500,skipped:true},{at:500,skipped:false}]};const result=composeConversation(recording,original,[question,question,question]);
 assert.equal(result.audio.toString('ascii',8,12),'WAVE');assert.equal(result.audio.readUInt32LE(4),result.audio.length-8);
 assert.equal(result.audio.readInt16LE(44),1000);assert.deepEqual(result.audio.subarray(44+result.timeline[0].answerAt*32,44+result.timeline[0].answerAt*32+first.length),first);
 assert.deepEqual(result.audio.subarray(44+result.timeline[2].answerAt*32,44+result.timeline[2].answerAt*32+second.length),second);assert.equal(question.readInt16LE(0),10000);
});
test('conversation is cached per page revision, authorized on every read and removed with the recording',async()=>{
 const f=fixture();let calls=0;const conversation=new ConversationStore(f.voice,async()=>{calls++;return [pcm(100,10000)];});try{
 const [a,b]=await Promise.all([conversation.audio(f.member,f.page),conversation.audio(f.member,f.page)]);assert.equal(calls,1);assert.deepEqual(a.audio,b.audio);assert.deepEqual(Buffer.from(f.voice.audio(f.member,f.page).audio),f.original);await conversation.audio(f.member,f.page);assert.equal(calls,1);
 const other=token();await assert.rejects(conversation.audio(other,f.page),/UNAUTHORIZED/);
 f.voice.write(f.member,f.page,{revision:1},true);assert.equal(f.store.db.prepare('SELECT count(*) n FROM page_conversation').get().n,0);await assert.rejects(conversation.audio(f.member,f.page),/VOICE_NOT_FOUND/);
 }finally{f.store.close();}
});
test('deletion or expired session while synthesizing cannot restore cached private audio',async()=>{
 for(const revoke of [false,true]){const f=fixture();let release;const conversation=new ConversationStore(f.voice,()=>new Promise(r=>release=r));try{const pending=conversation.audio(f.member,f.page);await new Promise(r=>setImmediate(r));if(revoke)f.store.revokeSession(f.owner,f.session.id);else f.voice.write(f.member,f.page,{revision:1},true);release([pcm(100,10000)]);await assert.rejects(pending,revoke?/UNAUTHORIZED/:/VOICE_CONFLICT/);assert.equal(f.store.db.prepare('SELECT count(*) n FROM page_conversation').get().n,0);}finally{f.store.close();}}
});
test('dated entries preserve old artwork and date across old-client saves and reject impossible dates',()=>{
 const f=fixture();try{const before=f.diaries.page(f.member,f.page);assert.match(before.entryDate,/^\d{4}-\d{2}-\d{2}$/);const saved=f.diaries.savePage(f.member,f.page,{...before,entryDate:'2026-11-02'});assert.equal(saved.entryDate,'2026-11-02');const {entryDate,...oldClient}=saved;assert.equal(f.diaries.savePage(f.member,f.page,oldClient).entryDate,entryDate);assert.deepEqual(f.diaries.page(f.member,f.page).strokes,before.strokes);assert.equal(f.diaries.library(f.member).pages[0].entryDate,entryDate);const diary=f.diaries.library(f.member).diaries[0].id;assert.throws(()=>f.diaries.createPage(f.member,diary,{title:'시험',template:'blank',entryDate:'2026-02-30'}),/INVALID_DIARY/);assert.throws(()=>f.diaries.savePage(f.member,f.page,{...f.diaries.page(f.member,f.page),entryDate:'2026-13-01'}),/INVALID_DIARY/);
 }finally{f.store.close();}
});
test('HTTP serves one private conversation WAV and removes the separate voice destination',async()=>{
 const f=fixture(),server=createApp(f.store,{synthesizeQuestions:async()=>[pcm(100,10000)]});await new Promise(r=>server.listen(0,'127.0.0.1',r));const base='http://127.0.0.1:'+server.address().port;try{
 const html=await(await fetch(base)).text();assert.equal(html.includes('data-panel="voice"'),false);assert.equal(html.includes('voice-answer-only'),false);assert.ok(html.includes('id="entry-conversation"'));assert.ok(html.includes('id="entry-date-display"'));
 const route=base+'/api/session/pages/'+f.page+'/voice/conversation';assert.equal((await fetch(route)).status,401);const response=await fetch(route,{headers:{Authorization:'Bearer '+f.member}});assert.equal(response.status,200);assert.equal(response.headers.get('x-voice-revision'),'1');const bytes=Buffer.from(await response.arrayBuffer());assert.equal(bytes.toString('ascii',0,4),'RIFF');assert.ok(bytes.length>f.original.length);
 }finally{await new Promise(r=>server.close(r));f.store.close();}
});
