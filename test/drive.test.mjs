import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {AccountStore} from '../backend/store.mjs';
import {VisitStore} from '../backend/visits.mjs';
import {DriveStore} from '../backend/drive.mjs';
import {initializeStickers} from '../backend/stickers.mjs';
import {createApp} from '../backend/server.mjs';
const token=()=>randomBytes(32).toString('hex');
function setup(){let now=100000;const a=new AccountStore(':memory:',()=>now);a.mockLocationAccess=true;initializeStickers(a);const v=new VisitStore(a),d=new DriveStore(a,v),owner=token(),member=token(),phone=token();a.registerDemo(owner);const session=a.createSession(owner);for(const t of [member,phone])a.joinSession(t,session.code);let trip;
 return {a,v,d,member,phone,select:id=>trip=d.select(member,id).tripId,advance:ms=>now+=ms,tick:(speed=10,gear=8,valid=true,age=0)=>{now+=1000;return d.tick(member,{source:'console-drive',tripId:trip,valid,speedMps:speed,gear,measuredAt:now-age});}};
}
test('console route requires movement then continuous stopped P; two cities sync and reset in the same family',()=>{
 const {a,v,d,member,phone,select,tick}=setup();try{
  const finish=id=>{select(id);for(let i=0;i<=30;i++)assert.equal(tick().awarded,false);assert.equal(d.state(member).distanceMeters,300);assert.equal(v.state(phone).rewards.length,id==='busan'?0:1);for(let i=0;i<6;i++)assert.equal(tick(0,4).awarded,i===5);};
  finish('busan');finish('jeju');assert.equal(v.state(phone).rewards.length,2);assert.equal(a.sessionPalette(phone).colors.length,17);
  for(let i=0;i<6;i++)assert.equal(tick(0,4).awarded,false);
  const old=d.state(member).tripId;d.reset(member);assert.equal(v.state(phone).rewards.length,0);assert.equal(a.sessionPalette(phone).colors.length,11);assert.throws(()=>d.tick(member,{source:'console-drive',tripId:old}),/DRIVE_NOT_STARTED/);
  finish('busan');assert.equal(v.state(phone).rewards.length,1);
 }finally{a.close();}
});
test('stale signals, gaps, non-D and destination changes do not add route distance or parking time',()=>{
 const {a,d,member,select,tick,advance}=setup();try{
  select('busan');tick();tick();assert.equal(d.state(member).distanceMeters,10);tick(10,1);tick(10,8);assert.equal(d.state(member).distanceMeters,10);tick(10,8,true,31000);tick();assert.equal(d.state(member).distanceMeters,10);advance(6000);tick();assert.equal(d.state(member).distanceMeters,10);
  select('jeju');assert.equal(d.state(member).distanceMeters,0);for(let i=0;i<=30;i++)tick();for(let i=0;i<4;i++)tick(0,4);tick(1,4);for(let i=0;i<4;i++)assert.equal(tick(0,4).awarded,false);advance(6000);assert.equal(d.state(member).parkMs,0);assert.equal(tick(0,4).parkMs,0);assert.equal(tick(0,4,false).parkMs,0);
  assert.throws(()=>d.select(member,'unknown'),/INVALID_DRIVE/);
 }finally{a.close();}
});
test('developer controls reject normal families, disabled simulation and unknown participants',()=>{
 const {a,d,member}=setup();try{const owner=token(),normal=token();a.register(owner);a.joinSession(normal,a.createSession(owner).code);for(const f of [()=>d.state(normal),()=>d.select(normal,'busan'),()=>d.reset(normal)])assert.throws(f,/DEMO_ONLY/);a.mockLocationAccess=false;assert.throws(()=>d.reset(member),/SIMULATION_DISABLED/);assert.throws(()=>d.state(token()));}finally{a.close();}
});
test('reset preserves previously saved colors, artwork and another family; clears all family visit streams',()=>{
 const {a,v,d,member,phone,select,tick}=setup();try{
  createApp(a);const key=a.participantOwner(member);
  const saved = a.sessionPalette(phone);
  saved.colors.push({id:'personal-123456',hex:'#123456',source:'personal'});
  a.db.prepare('UPDATE accounts SET palette=? WHERE token_hash=?').run(JSON.stringify(saved),key);
  const otherOwner=token(),other=token();a.registerDemo(otherOwner);a.joinSession(other,a.createSession(otherOwner).code);
  for(let i=0;i<=8;i++)v.sample(other,{source:'simulation',timestamp:i*15000,latitude:35.8347,longitude:129.219});
  const foreign=a.sessionPalette(other);a.db.prepare('INSERT INTO artworks VALUES (?,?,?)').run(key,'{"saved":"unchanged"}',7);
  select('busan');for(let i=0;i<=30;i++)tick();for(let i=0;i<=5;i++)tick(0,4);
  v.sample(phone,{source:'simulation',timestamp:0,latitude:35.8347,longitude:129.219});
  // Separate vehicle streams belong to participants of the same family.
  for(const row of a.db.prepare('SELECT token_hash FROM participants').all())if(row.token_hash)a.db.prepare('INSERT OR REPLACE INTO visit_progress VALUES (?,?,?,?)').run('vehicle-'+row.token_hash,0,1000,'gyeongju');
  d.reset(member);const palette=a.sessionPalette(phone);assert.equal(palette.colors.length,12);assert.ok(palette.colors.some(c=>c.id==='personal-123456'));assert.deepEqual(a.sessionPalette(other),foreign);assert.equal(a.db.prepare('SELECT revision FROM artworks WHERE owner_hash=?').get(key).revision,7);assert.equal(a.db.prepare('SELECT count(*) n FROM visit_progress').get().n,2);
 }finally{a.close();}
});
test('HTTP developer actions require a demo participant and reject stale trip after same-family reset',async()=>{
 const {a,member,phone}=setup();const server=createApp(a);await new Promise(r=>server.listen(0,'127.0.0.1',r));const url='http://127.0.0.1:'+server.address().port;
 const call=(t,path,body)=>fetch(url+path,{method:body?'POST':'GET',headers:{Authorization:'Bearer '+t,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});
 try{assert.equal((await call(token(),'/api/session/visits/drive')).status,401);const r=await call(member,'/api/session/visits/drive',{action:'select',regionId:'busan'});assert.equal(r.status,200);const selected=await r.json();assert.equal((await call(phone,'/api/session/visits/reset',{})).status,200);assert.equal((await call(member,'/api/session/visits/drive',{action:'tick',source:'console-drive',tripId:selected.tripId,valid:false})).status,409);assert.equal((await call(member,'/api/session/visits/drive',{action:'select',regionId:'bad'})).status,400);a.mockLocationAccess=false;assert.equal((await call(member,'/api/session/visits/reset',{})).status,403);}finally{await new Promise(r=>server.close(r));a.close();}
});
