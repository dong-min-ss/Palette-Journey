import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {AccountStore} from '../backend/store.mjs';
import {DiaryStore} from '../backend/diaries.mjs';
import {VoiceStore} from '../backend/voice.mjs';
import {createApp} from '../backend/server.mjs';
const token=()=>randomBytes(32).toString('hex');
function participant(store){const owner=token(),member=token();store.register(owner);store.joinSession(member,store.createSession(owner).code);return {owner,member};}
// Minimal container fixture; tests validate storage, not codec decoding.
const audio=Buffer.concat([Buffer.from('1a45dfa3','hex'),Buffer.alloc(16)]);
const body=revision=>({revision,mime:'audio/webm',audio:audio.toString('base64'),duration:1500,cues:[{question:0,at:0},{question:1,at:1000}]});

test('voice survives restart, keeps separate revisions and tombstones, and enforces family/session access',()=>{
  const dir=mkdtempSync(join(tmpdir(),'palette-voice-')),path=join(dir,'db.sqlite');let store=new AccountStore(path);
  try{
    const {owner,member}=participant(store),outsider=participant(store).member;let diaries=new DiaryStore(store),voice=new VoiceStore(diaries);
    const page=diaries.page(member,diaries.library(member).pages[0].id);
    assert.deepEqual(voice.get(member,page.id),{revision:0,recording:null});
    assert.equal(voice.write(member,page.id,body(0)).revision,1);
    assert.throws(()=>voice.get(outsider,page.id),/DIARY_NOT_FOUND/);
    assert.throws(()=>voice.audio(outsider,page.id),/DIARY_NOT_FOUND/);
    assert.throws(()=>voice.write(outsider,page.id,{revision:1},true),/DIARY_NOT_FOUND/);
    diaries.savePage(member,page.id,{...page,text:'음성과 독립된 글'});
    assert.equal(voice.get(member,page.id).revision,1);
    assert.throws(()=>voice.write(member,page.id,body(0)),/VOICE_CONFLICT/);
    store.close();store=new AccountStore(path);diaries=new DiaryStore(store);voice=new VoiceStore(diaries);
    assert.deepEqual(Buffer.from(voice.audio(member,page.id).audio),audio);
    assert.equal(voice.get(member,page.id).recording.mode,'guided-parent-demo');
    assert.deepEqual(voice.write(member,page.id,{revision:1},true),{revision:2,recording:null});
    assert.throws(()=>voice.audio(member,page.id),/VOICE_NOT_FOUND/);
    assert.throws(()=>voice.write(member,page.id,body(0)),/VOICE_CONFLICT/);
    voice.write(member,page.id,body(2));store.createSession(owner);
    assert.throws(()=>voice.audio(member,page.id),/UNAUTHORIZED/);
  }finally{store.close();rmSync(dir,{recursive:true});}
});

test('voice rejects invalid data and oversized/corrupt media without mutating existing recording',()=>{
  const store=new AccountStore(':memory:'),diaries=new DiaryStore(store),voice=new VoiceStore(diaries),{member}=participant(store);
  try{
    const id=diaries.library(member).pages[0].id;
    for(const bad of [{duration:0},{duration:120001},{duration:NaN},{mime:'text/html'},{audio:'a==='},{audio:Buffer.alloc(30).toString('base64')},{audio:Buffer.alloc(2097153).toString('base64')},{cues:[{question:4,at:0}]},{cues:[{question:0,at:1501}]},{cues:[{question:0,at:1000},{question:1,at:50}]}]){
      assert.throws(()=>voice.write(member,id,{...body(0),...bad}),/INVALID_VOICE/);
      assert.equal(voice.get(member,id).revision,0);
    }
    voice.write(member,id,body(0));assert.throws(()=>voice.write(member,id,{revision:0},true),/VOICE_CONFLICT/);
    assert.equal(voice.get(member,id).recording.bytes,audio.length);
  }finally{store.close();}
});

test('HTTP voice requires authentication for metadata and bytes and supports guarded deletion',async()=>{
  const store=new AccountStore(':memory:'),diaries=new DiaryStore(store),{member}=participant(store),outsider=participant(store).member;
  const page=diaries.library(member).pages[0].id,server=createApp(store);await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const base=`http://127.0.0.1:${server.address().port}`,path=`/api/session/pages/${page}/voice`;
  const request=(path,method='GET',data,credential=member)=>fetch(base+path,{method,headers:{Authorization:`Bearer ${credential}`,'Content-Type':'application/json'},...(data?{body:JSON.stringify(data)}:{})});
  try{
    assert.equal((await fetch(base+path+'/audio')).status,401);
    assert.equal((await request(path,'PUT',body(0))).status,200);
    assert.equal((await request(path+'/audio','GET',null,outsider)).status,404);
    const response=await request(path+'/audio');assert.equal(response.headers.get('content-type'),'audio/webm');assert.equal(response.headers.get('cache-control'),'no-store');assert.deepEqual(Buffer.from(await response.arrayBuffer()),audio);
    assert.equal((await request(path,'DELETE',{revision:0})).status,409);
    assert.equal((await request(path,'DELETE',{revision:1})).status,200);
    assert.equal((await request(path+'/audio')).status,404);
    assert.equal((await request(path,'GET')).status,200);
    assert.equal((await request('/voice.mjs')).status,200);
    const pageData=await (await request(`/api/session/pages/${page}`)).json();assert.equal(pageData.revision,0);
  }finally{await new Promise(r=>server.close(r));store.close();}
});
