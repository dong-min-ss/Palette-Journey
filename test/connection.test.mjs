import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {AccountStore} from '../backend/store.mjs';
import {DiaryStore} from '../backend/diaries.mjs';
import {ConnectionStore} from '../backend/connection.mjs';
import {createApp} from '../backend/server.mjs';
const token=()=>randomBytes(32).toString('hex');
function setup(){let now=1000000;const store=new AccountStore(':memory:',()=>now),owner=token(),member=token();store.register(owner);const session=store.createSession(owner);store.joinSession(member,session.code);return {store,owner,member,session,setNow:value=>now=value};}
test('participant invites expire, renew, retry and revoke without granting owner access',()=>{
 const {store,owner,member,session,setNow}=setup();try{
 const first=store.invite(member),other=token();store.joinSession(other,first.code);assert.throws(()=>store.createSession(other),/UNAUTHORIZED/);
 const second=store.invite(member);assert.throws(()=>store.joinSession(token(),first.code),/INVALID_SESSION/);
 const third=token();store.joinSession(third,second.code);setNow(second.joinExpiresAt);assert.throws(()=>store.joinSession(token(),second.code),/INVALID_SESSION/);assert.equal(store.joinSession(third,second.code).id,session.id);
 store.revokeSession(owner,session.id);assert.throws(()=>store.invite(member),/UNAUTHORIZED/);assert.throws(()=>store.joinSession(token(),second.code),/INVALID_SESSION/);
 }finally{store.close();}
});
test('shared page enforces family boundaries and stale focus cannot overwrite selection or drawing',()=>{
 const {store,member,session,setNow}=setup();try{
 const diaries=new DiaryStore(store),connection=new ConnectionStore(store,diaries),library=diaries.library(member),page=diaries.page(member,library.pages[0].id),other=token();store.joinSession(other,store.invite(member).code);
 assert.equal(connection.get(member).page,null);connection.set(member,{pageId:page.id,revision:0});assert.equal(connection.get(other).page.id,page.id);
 assert.throws(()=>connection.set(other,{pageId:page.id,revision:0}),/CONNECTION_CONFLICT/);assert.deepEqual(diaries.page(member,page.id),page);
 const foreignOwner=token(),foreign=token();store.register(foreignOwner);store.joinSession(foreign,store.createSession(foreignOwner).code);assert.throws(()=>connection.set(foreign,{pageId:page.id,revision:0}),/DIARY_NOT_FOUND/);
 setNow(session.expiresAt);assert.throws(()=>connection.get(other),/UNAUTHORIZED/);
 }finally{store.close();}
});
test('HTTP vehicle and mobile share session, library and selected page',async()=>{
 const {store,member}=setup(),server=createApp(store);await new Promise(r=>server.listen(0,'127.0.0.1',r));const base='http://127.0.0.1:'+server.address().port;
 const call=async(path,method='GET',body,credential=member)=>{const response=await fetch(base+path,{method,headers:{Authorization:'Bearer '+credential,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});return {status:response.status,data:await response.json()};};
 try{const invite=await call('/api/session/invitations','POST',{}),mobile=token();assert.equal((await call('/api/sessions/join','POST',{code:invite.data.code},mobile)).status,200);
 const library=(await call('/api/session/library')).data;assert.deepEqual((await call('/api/session/library','GET',undefined,mobile)).data,library);
 assert.equal((await call('/api/session/connection','PUT',{pageId:library.pages[0].id,revision:0})).status,200);
 assert.equal((await call('/api/session/connection','GET',undefined,mobile)).data.page.id,library.pages[0].id);
 assert.equal((await call('/api/session/connection','PUT',{pageId:library.pages[0].id,revision:0},mobile)).status,409);
 assert.equal((await call('/api/session/connection','GET',undefined,token())).status,401);
 }finally{await new Promise(r=>server.close(r));store.close();}
});

test('invitations and selected page survive reopening without storing raw invitation',async()=>{
 const {mkdtempSync,rmSync}=await import('node:fs'),{tmpdir}=await import('node:os'),{join}=await import('node:path');const dir=mkdtempSync(join(tmpdir(),'pj-connection-'));let store;
 try{const file=join(dir,'test.sqlite'),owner=token(),member=token();store=new AccountStore(file);store.register(owner);store.joinSession(member,store.createSession(owner).code);const diaries=new DiaryStore(store),connection=new ConnectionStore(store,diaries),page=diaries.library(member).pages[0];connection.set(member,{pageId:page.id,revision:0});const invite=store.invite(member);assert.notEqual(store.db.prepare('SELECT code_hash FROM invitations').get().code_hash,invite.code);store.close();store=new AccountStore(file);const mobile=token();store.joinSession(mobile,invite.code);assert.equal(new ConnectionStore(store,new DiaryStore(store)).get(mobile).page.id,page.id);
 }finally{store?.close();rmSync(dir,{recursive:true,force:true});}
});
