import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {AccountStore} from '../backend/store.mjs';
import {createApp} from '../backend/server.mjs';
import {VisitStore} from '../backend/visits.mjs';
import {ownedStickers} from '../backend/stickers.mjs';
const token=()=>randomBytes(32).toString('hex');
const sample=(timestamp,latitude=35.8347)=>({source:'simulation',timestamp,latitude,longitude:129.219});
function setup(path=':memory:'){const a=new AccountStore(path);createApp(a);a.mockLocationAccess=true;const owner=token(),member=token();a.register(owner);a.joinSession(member,a.createSession(owner).code);return {a,owner,member,v:new VisitStore(a)};}
test('entry/dwell resets on exit or gap; rejects invalid samples and ignores replay',()=>{
 const {a,member,v}=setup();try{
 assert.throws(()=>v.sample(member,sample(NaN)),/INVALID_LOCATION/);
 for(const t of [0,30000,60000])assert.equal(v.sample(member,sample(t)).awarded,false);
 assert.equal(v.sample(member,sample(90000,37)).dwellMs,0);
 v.sample(member,sample(120000));assert.equal(v.sample(member,sample(151000)).dwellMs,0);
 assert.equal(v.sample(member,sample(150000)).ignored,true);
 assert.equal(v.state(member).rewards.length,0);
 }finally{a.close();}
});
test('one atomic family award survives reentry, session recreation and DB restart; dev access is separate',()=>{
 const dir=mkdtempSync(join(tmpdir(),'palette-visits-')),path=join(dir,'db');let {a,owner,member,v}=setup(path);
 try{
 a.developmentStickerAccess=true;assert.equal(ownedStickers(a,member).length,4);assert.equal(v.state(member).rewards.length,0);
 for(let t=0;t<=120000;t+=15000)v.sample(member,sample(t));
 assert.equal(v.state(member).rewards.length,1);assert.equal(a.sessionPalette(member).colors.length,14);assert.equal(a.sessionPalette(member).tickets,1);
 const other=token();a.joinSession(other,a.invite(member).code);assert.deepEqual(a.sessionPalette(other),a.sessionPalette(member));
 assert.equal(ownedStickers(a,other).filter(s=>s.visitId).length,2);
 v.sample(member,sample(150000,37));for(let t=180000;t<=300000;t+=15000)assert.equal(v.sample(member,sample(t)).awarded,false);
 a.close();a=new AccountStore(path);createApp(a);a.mockLocationAccess=true;v=new VisitStore(a);member=token();a.joinSession(member,a.createSession(owner).code);
 assert.equal(v.state(member).rewards.length,1);assert.equal(a.sessionPalette(member).colors.length,14);assert.equal(ownedStickers(a,member).filter(s=>s.visitId).length,2);
 const unrelated=token(),p=token();a.register(unrelated);a.joinSession(p,a.createSession(unrelated).code);assert.equal(v.state(p).rewards.length,0);
 }finally{a.close();rmSync(dir,{recursive:true,force:true});}
});
test('mock HTTP endpoint requires active family and explicit simulation opt-in',async()=>{
 const {a,member}=setup(),server=createApp(a);await new Promise(r=>server.listen(0,'127.0.0.1',r));const url=`http://127.0.0.1:${server.address().port}/api/session/visits/mock`;
 try{const send=auth=>fetch(url,{method:'POST',headers:{Authorization:`Bearer ${auth}`,'Content-Type':'application/json'},body:JSON.stringify(sample(0))});
 assert.equal((await send(token())).status,401);a.mockLocationAccess=false;assert.equal((await send(member)).status,403);a.mockLocationAccess=true;assert.equal((await send(member)).status,200);
 }finally{await new Promise(r=>server.close(r));a.close();}
});

test('vehicle samples use server time, isolate accelerated routes and reset stale/missing positions',()=>{
 const {a,member,v}=setup();let now=1000000;a.now=()=>now;
 const position=()=>({source:'pleos-emulator',valid:true,latitude:35.8347,longitude:129.219,measuredAt:now});
 try{
  // A future sample on the accelerated channel cannot poison the GPS clock.
  v.sample(member,sample(now+999999));
  assert.equal(v.vehicle(member,position()).inRegion,true);
  now+=30000;assert.equal(v.vehicle(member,position()).dwellMs,30000);
  now+=30001;assert.equal(v.vehicle(member,{...position(),measuredAt:now-30001}).locationFresh,false);
  now+=1000;assert.equal(v.vehicle(member,position()).dwellMs,0);
  now+=1000;assert.equal(v.vehicle(member,{source:'pleos-emulator',valid:false}).dwellMs,0);
  now+=1000;
  for(let i=0;i<=8;i++){const result=v.vehicle(member,position());assert.equal(result.awarded,i===8);now+=15000;}
  assert.equal(a.sessionPalette(member).colors.length,14);
  assert.equal(v.vehicle(member,position()).awarded,false);
  assert.throws(()=>v.vehicle(member,{...position(),source:'real-vehicle'}),/INVALID_LOCATION/);
  assert.throws(()=>v.vehicle(member,{...position(),latitude:NaN}),/INVALID_LOCATION/);
 }finally{a.close();}
});

test('fresh demo family starts locked despite development access and never changes existing families',()=>{
 const {a,member,v}=setup();const demoOwner=token(),demoMember=token();a.developmentStickerAccess=true;
 try{
  assert.equal(ownedStickers(a,member).length,4);
  assert.throws(()=>a.registerDemo(member),/INVALID_DEMO_ACCOUNT|UNAUTHORIZED/); // participant is not an owner account
  a.registerDemo(demoOwner);a.registerDemo(demoOwner);a.joinSession(demoMember,a.createSession(demoOwner).code);
  assert.equal(a.developmentStickers(demoMember),false);assert.equal(ownedStickers(a,demoMember).length,2);
  assert.equal(a.sessionPalette(demoMember).colors.length,11);assert.equal(v.state(demoMember).rewards.length,0);
  for(let t=0;t<=120000;t+=15000)v.sample(demoMember,sample(t));
  assert.equal(ownedStickers(a,demoMember).length,4);assert.equal(a.sessionPalette(demoMember).colors.length,14);
  assert.equal(a.sessionPalette(member).colors.length,11);assert.equal(v.state(member).rewards.length,0);assert.equal(a.developmentStickers(member),true);
  const realOwner=token();a.register(realOwner);assert.throws(()=>a.registerDemo(realOwner),/INVALID_DEMO_ACCOUNT/);
  a.mockLocationAccess=false;assert.throws(()=>a.registerDemo(token()),/SIMULATION_DISABLED/);
 }finally{a.close();}
});
