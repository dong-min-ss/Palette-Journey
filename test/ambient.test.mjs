import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {AccountStore} from '../backend/store.mjs';
import {AmbientStore} from '../backend/ambient.mjs';
import {createApp} from '../backend/server.mjs';
const token=()=>randomBytes(32).toString('hex');
function setup(){let now=100000;const a=new AccountStore(':memory:',()=>now),owner=token(),phone=token(),car=token();a.register(owner);const session=a.createSession(owner);a.joinSession(phone,session.code);a.joinSession(car,session.code);return {a,phone,car,owner,session,advance:n=>now+=n};}
test('ambient commands are opt-in, session-scoped, palette-authorized and expire without heartbeat',()=>{
 const {a,phone,car,advance}=setup();try{
  const s=new AmbientStore(a);assert.equal(s.get(car).enabled,false);
  assert.throws(()=>s.set(phone,{enabled:true,colorId:'region-locked'}),/INVALID_COLOR/);
  const c=s.set(phone,{enabled:true,colorId:'blue'});assert.equal(c.color.hex,'#2196F3');assert.equal(s.get(car).revision,c.revision);
  assert.throws(()=>s.ack(car,{revision:c.revision,status:'applied',actualHex:'#000000'}),/INVALID_LIGHT/);
  s.ack(car,{revision:c.revision,status:'applied',actualHex:'#2196F3'});assert.equal(s.get(phone).status,'applied');
  advance(2000);assert.equal(s.set(phone,{enabled:true,colorId:'blue'}).revision,c.revision);
  const next=s.set(phone,{enabled:true,colorId:'red'});assert.equal(next.status,'waiting');assert.throws(()=>s.ack(car,{revision:c.revision,status:'blocked'}),/LIGHT_CONFLICT/);
  const foreignOwner=token(),foreign=token();a.register(foreignOwner);a.joinSession(foreign,a.createSession(foreignOwner).code);assert.equal(s.get(foreign).enabled,false);assert.throws(()=>s.ack(foreign,{revision:next.revision,status:'blocked'}),/LIGHT_CONFLICT/);
  advance(10000);assert.equal(s.get(car).enabled,false);assert.throws(()=>s.ack(car,{revision:next.revision,status:'blocked'}),/LIGHT_CONFLICT/);
  const renewed=s.set(phone,{enabled:true,colorId:'red'});assert.ok(renewed.revision>next.revision);
  s.set(phone,{enabled:false});assert.equal(s.get(car).enabled,false);assert.equal(new AmbientStore(a).get(car).enabled,false);
 }finally{a.close();}
});
test('HTTP ambient routes enforce authentication, exact readback, cancellation and session revocation',async()=>{
 const {a,phone,car,owner,session}=setup(),server=createApp(a);await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const call=async(path,method,body,credential=phone)=>{const r=await fetch('http://127.0.0.1:'+server.address().port+path,{method,headers:{Authorization:'Bearer '+credential,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});return {status:r.status,data:await r.json()};};
 try{
  assert.equal((await call('/api/session/ambient','GET',null,token())).status,401);
  const c=(await call('/api/session/ambient','PUT',{enabled:true,colorId:'white'})).data;
  assert.equal((await call('/api/session/ambient/ack','POST',{revision:c.revision,status:'applied',actualHex:'#FFFFFF'},car)).status,200);
  assert.equal((await call('/api/session/ambient','GET')).data.status,'applied');
  await call('/api/session/ambient','PUT',{enabled:false});assert.equal((await call('/api/session/ambient/ack','POST',{revision:c.revision,status:'blocked'},car)).status,409);
  a.revokeSession(owner,session.id);assert.equal((await call('/api/session/ambient','GET',null,car)).status,401);
 }finally{await new Promise(r=>server.close(r));a.close();}
});
