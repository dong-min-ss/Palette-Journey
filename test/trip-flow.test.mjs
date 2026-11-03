import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes,randomUUID} from 'node:crypto';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {AccountStore} from '../backend/store.mjs';
import {createApp} from '../backend/server.mjs';

const token=()=>randomBytes(32).toString('hex');
function wav(){
 const b=Buffer.alloc(32044);b.write('RIFF');b.writeUInt32LE(b.length-8,4);b.write('WAVEfmt ',8);b.writeUInt32LE(16,16);b.writeUInt16LE(1,20);b.writeUInt16LE(1,22);b.writeUInt32LE(16000,24);b.writeUInt32LE(32000,28);b.writeUInt16LE(2,32);b.writeUInt16LE(16,34);b.write('data',36);b.writeUInt32LE(32000,40);
 for(let i=44;i<b.length;i+=2)b.writeInt16LE(500,i);return b;
}

test('one family trip links invitation, visit rewards, colored stickers, shared drawing and vehicle conversation across reconnect',async()=>{
 const dir=mkdtempSync(join(tmpdir(),'pj-trip-flow-')),file=join(dir,'trip.sqlite');
 let now=Date.parse('2026-11-03T03:00:00Z'),store=new AccountStore(file,()=>now),server,base;
 const owner=token(),vehicle=token(),phone=token(),original=wav();
 const start=async()=>{server=createApp(store,{synthesizeQuestions:async questions=>questions.map(()=>Buffer.alloc(3200))});store.mockLocationAccess=true;store.developmentStickerAccess=true;await new Promise(r=>server.listen(0,'127.0.0.1',r));base=`http://127.0.0.1:${server.address().port}`;};
 const request=(who,path,method='GET',body)=>fetch(base+path,{method,headers:{Authorization:`Bearer ${who}`,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
 const call=async(...args)=>{const r=await request(...args);assert.equal(r.status,200);return r.json();};
 try{
  await start();await call(owner,'/api/account/demo','PUT',{});const session=await call(owner,'/api/sessions','POST',{});await call(vehicle,'/api/sessions/join','POST',{code:session.code});
  const invitation=await call(vehicle,'/api/session/invitations','POST',{});await call(phone,'/api/sessions/join','POST',{code:invitation.code});
  const library=await call(phone,'/api/session/library'),id=library.pages[0].id,path=`/api/session/pages/${id}`,blank=await call(phone,path);
  const artwork={...blank,title:'경주에서 별을 본 날',entryDate:'2026-11-03',text:'첨성대와 금관을 그렸어요.',strokes:[{tool:'paint',color:'#293C70',width:20,points:[[20,30],[550,30]]}],stickers:[{id:'tower',stickerId:'gyeongju-cheomseongdae',x:100,y:100,width:120,height:150,strokes:[]}]};
  assert.equal((await request(phone,path,'PUT',artwork)).status,400); // Locked rewards cannot be used yet.
  for(let i=0;i<=8;i++){await call(vehicle,'/api/session/visits/vehicle','POST',{source:'pleos-emulator',valid:true,latitude:35.8347,longitude:129.219,measuredAt:now});now+=15000;}
  const catalog=await call(phone,'/api/session/catalog');assert.equal(catalog.colors.filter(c=>c.unlocked&&c.rewardSource==='simulation').length,3);assert.equal(catalog.stickers.filter(c=>c.unlocked&&c.rewardSource==='simulation').length,2);
  artwork.stickers[0].strokes=[{tool:'paint',color:'#D9AA38',width:10,points:[[100,100],[150,150]]}];
  const saved=await call(phone,path,'PUT',artwork);assert.deepEqual(await call(vehicle,path),saved);
  const focus=await call(phone,'/api/session/connection');await call(phone,'/api/session/connection','PUT',{pageId:id,revision:focus.revision});assert.equal((await call(vehicle,'/api/session/connection')).page.id,id);
  const upload={revision:0,mime:'audio/wav',audio:original.toString('base64'),duration:1000,uploadId:randomUUID(),cues:[{question:0,at:0,questionText:'경주에서 무엇을 봤어?',answerText:'첨성대와 금관을 봤어.',skipped:false}]};
  assert.equal((await call(vehicle,path+'/voice/vehicle','PUT',upload)).revision,1);assert.equal((await call(vehicle,path+'/voice/vehicle','PUT',upload)).revision,1);
  const voice=await call(phone,path+'/voice');assert.equal(voice.recording.cues[0].answerText,upload.cues[0].answerText);
  const audio=await request(phone,path+'/voice/audio');assert.deepEqual(Buffer.from(await audio.arrayBuffer()),original);
  const conversation=await request(phone,path+'/voice/conversation');assert.equal(conversation.status,200);const bytes=Buffer.from(await conversation.arrayBuffer());assert.equal(bytes.toString('ascii',8,12),'WAVE');assert.ok(bytes.includes(original.subarray(44)));
  assert.deepEqual(await call(phone,path),saved); // Attaching a voice never rewrites the drawing.
  const otherPage=await call(phone,`/api/session/diaries/${library.diaries[0].id}/pages`,'POST',{title:'다른 날',template:'blank'});assert.equal((await call(phone,`/api/session/pages/${otherPage.id}/voice`)).recording,null);
  const strangerOwner=token(),stranger=token();await call(strangerOwner,'/api/account','PUT',{});const foreign=await call(strangerOwner,'/api/sessions','POST',{});await call(stranger,'/api/sessions/join','POST',{code:foreign.code});assert.equal((await request(stranger,path)).status,404);assert.equal((await request(stranger,path+'/voice/conversation')).status,404);
  await call(owner,`/api/sessions/${session.id}`,'DELETE');const next=await call(owner,'/api/sessions','POST',{}),reconnected=token();await call(reconnected,'/api/sessions/join','POST',{code:next.code});
  await new Promise(r=>server.close(r));server=null;store.close();store=new AccountStore(file,()=>now);await start();
  assert.deepEqual(await call(reconnected,path),saved);assert.equal((await call(reconnected,path+'/voice')).revision,1);assert.equal((await call(reconnected,'/api/session/visits')).rewards.length,1);assert.equal((await call(reconnected,'/api/session/palette')).colors.length,14);
  assert.deepEqual(Buffer.from(await(await request(reconnected,path+'/voice/audio')).arrayBuffer()),original);
 }finally{if(server)await new Promise(r=>server.close(r));store.close();rmSync(dir,{recursive:true,force:true});}
});
