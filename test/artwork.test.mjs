import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { AccountStore } from '../backend/store.mjs';
import { createApp } from '../backend/server.mjs';
const token = () => randomBytes(32).toString('hex');

test('artwork survives DB restart and rejects stale version, locked colors and invalid regions', () => {
  const dir=mkdtempSync(join(tmpdir(),'palette-art-')), path=join(dir,'test.sqlite');
  const owner=token(), member=token(), other=token();
  let store=new AccountStore(path);
  try {
    store.register(owner); const session=store.createSession(owner);
    store.joinSession(member,session.code);
    assert.deepEqual(store.artwork(member),{regions:{},revision:0});
    const saved=store.saveArtwork(member,{regions:{sky:'#2196F3',sun:'#FFEB3B'},revision:0});
    assert.equal(saved.revision,1);
    assert.throws(()=>store.saveArtwork(member,{regions:{sky:'#F44336'},revision:0}),/ARTWORK_CONFLICT/);
    assert.throws(()=>store.saveArtwork(member,{regions:{sky:'#123ABC'},revision:1}),/INVALID_ARTWORK/);
    assert.throws(()=>store.saveArtwork(member,{regions:{unknown:'#F44336'},revision:1}),/INVALID_ARTWORK/);
    assert.throws(()=>store.saveArtwork(member,{regions:[],revision:1}),/INVALID_ARTWORK/);
    assert.deepEqual(store.artwork(member),saved);
    store.register(other); const otherSession=store.createSession(other), outsider=token();
    store.joinSession(outsider,otherSession.code);
    assert.deepEqual(store.artwork(outsider),{regions:{},revision:0});
    store.close();store=new AccountStore(path);
    assert.deepEqual(store.artwork(member),saved);
    store.revokeSession(owner,session.id);
    assert.throws(()=>store.artwork(member),/UNAUTHORIZED/);
    assert.throws(()=>store.saveArtwork(member,{regions:{},revision:1}),/UNAUTHORIZED/);
  } finally {store.close();rmSync(dir,{recursive:true});}
});

test('HTTP serves web assets and authenticated artwork; rejects invalid and conflicting saves',async()=>{
  const store=new AccountStore(':memory:'), owner=token(), member=token();
  store.register(owner);store.joinSession(member,store.createSession(owner).code);
  const server=createApp(store);await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base=`http://127.0.0.1:${server.address().port}`;
  const headers={Authorization:`Bearer ${member}`,'Content-Type':'application/json'};
  try {
    const home=await fetch(base+'/');assert.equal(home.status,200);
    assert.match(await home.text(),/여행 연결 코드/);
    assert.match(home.headers.get('content-security-policy'),/script-src 'self'/);
    for(const path of ['/app.mjs','/style.css'])assert.equal((await fetch(base+path)).status,200);
    assert.equal((await fetch(base+'/../backend/store.mjs')).status!==200,true);
    assert.equal((await fetch(base+'/api/session/artwork')).status,401);
    const response=await fetch(base+'/api/session/artwork',{method:'PUT',headers,body:JSON.stringify({regions:{field:'#4CAF50'},revision:0})});
    assert.equal(response.status,200);
    const read=await(await fetch(base+'/api/session/artwork',{headers})).json();assert.equal(read.regions.field,'#4CAF50');
    const stale=await fetch(base+'/api/session/artwork',{method:'PUT',headers,body:JSON.stringify({regions:{},revision:0})});assert.equal(stale.status,409);
    const invalid=await fetch(base+'/api/session/artwork',{method:'PUT',headers,body:'null'});assert.equal(invalid.status,400);
  }finally{await new Promise(resolve=>server.close(resolve));store.close();}
});
