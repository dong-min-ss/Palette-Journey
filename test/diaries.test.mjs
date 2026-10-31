import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {AccountStore} from '../backend/store.mjs';
import {DiaryStore} from '../backend/diaries.mjs';
import {createApp} from '../backend/server.mjs';
const token=()=>randomBytes(32).toString('hex');
function participant(accounts){const owner=token(),member=token();accounts.register(owner);const session=accounts.createSession(owner);accounts.joinSession(member,session.code);return {owner,member,session};}

test('family bootstrap preserves original artwork and is repeatable across reconnection/restart',()=>{
  const dir=mkdtempSync(join(tmpdir(),'palette-books-')),path=join(dir,'db.sqlite');let accounts=new AccountStore(path);
  try{
    const {owner,member}=participant(accounts);
    const original=accounts.saveArtwork(member,{strokes:[{tool:'paint',color:'#F44336',width:12,points:[[1,2],[3,4]]}],legacyRegions:{},revision:0});
    let diaries=new DiaryStore(accounts),library=diaries.library(member),page=diaries.page(member,library.pages[0].id);
    assert.equal(library.visibility,'family-private');assert.deepEqual(page.strokes,original.strokes);
    assert.deepEqual(accounts.artwork(member),original);assert.equal(diaries.library(member).pages.length,1);
    const saved=diaries.savePage(member,page.id,{...page,text:'바다에서 조개를 봤어요.'});
    accounts.close();accounts=new AccountStore(path);diaries=new DiaryStore(accounts);
    const second=token();accounts.joinSession(second,accounts.createSession(owner).code);
    assert.equal(diaries.library(second).familyId,library.familyId);
    assert.equal(diaries.page(second,page.id).text,saved.text);
    assert.equal(diaries.library(second).pages.length,1);
  }finally{accounts.close();rmSync(dir,{recursive:true});}
});
test('siblings, trips and pages retain independent content and block other families',()=>{
  const accounts=new AccountStore(':memory:'),diaries=new DiaryStore(accounts);
  try{
    const {member,owner,session}=participant(accounts),outsider=participant(accounts).member;
    const library=diaries.library(member),first=diaries.page(member,library.pages[0].id);
    const child=diaries.createChild(member,{name:'둘째'}),trip=diaries.createTrip(member,{title:'바다 여행'});
    const diary=diaries.createDiary(member,{title:'둘째 일기',childId:child.id,tripId:trip.id});
    const blank=diaries.createPage(member,diary.id,{title:'내가 본 바다',template:'blank'});
    const second=diaries.createPage(member,diary.id,{title:'다음 날',template:'landscape'});
    assert.equal(second.position,1);assert.equal(blank.template,'blank');
    const saved=diaries.savePage(member,blank.id,{...blank,text:'파도가 반짝였다.'});
    assert.equal(diaries.page(member,first.id).text,'');assert.equal(diaries.page(member,second.id).text,'');
    assert.throws(()=>diaries.savePage(member,blank.id,{...blank,text:'덮어쓰기'}),/ARTWORK_CONFLICT/);
    assert.throws(()=>diaries.savePage(member,blank.id,{...saved,template:'landscape'}),/INVALID_DIARY/);
    assert.throws(()=>diaries.savePage(member,blank.id,{...saved,text:'가'.repeat(2001)}),/INVALID_DIARY/);
    assert.throws(()=>diaries.page(outsider,blank.id),/DIARY_NOT_FOUND/);
    assert.throws(()=>diaries.savePage(outsider,blank.id,saved),/DIARY_NOT_FOUND/);
    assert.throws(()=>diaries.createPage(outsider,diary.id,{title:'침입',template:'blank'}),/DIARY_NOT_FOUND/);
    assert.throws(()=>diaries.createDiary(outsider,{title:'침입',childId:child.id,tripId:trip.id}),/DIARY_NOT_FOUND/);
    accounts.revokeSession(owner,session.id);assert.throws(()=>diaries.page(member,blank.id),/UNAUTHORIZED/);
  }finally{accounts.close();}
});
test('HTTP library/page creation, plain text save, reload and invalid input',async()=>{
  const accounts=new AccountStore(':memory:'),{member}=participant(accounts),server=createApp(accounts);
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base=`http://127.0.0.1:${server.address().port}`;
  const request=(path,method='GET',body)=>fetch(base+path,{method,headers:{Authorization:`Bearer ${member}`,'Content-Type':'application/json'},...(body===undefined?{}:{body:JSON.stringify(body)})});
  try{
    assert.equal((await fetch(base+'/api/session/library')).status,401);
    const library=await(await request('/api/session/library')).json();
    const page=await(await request(`/api/session/diaries/${library.diaries[0].id}/pages`,'POST',{title:'글만 쓰는 페이지',template:'blank'})).json();
    const response=await request('/api/session/pages/'+page.id,'PUT',{...page,text:'<script>이 글은 텍스트입니다</script>'});assert.equal(response.status,200);
    const read=await(await request('/api/session/pages/'+page.id)).json();assert.equal(read.text,'<script>이 글은 텍스트입니다</script>');
    assert.equal((await request('/api/session/pages/'+page.id,'PUT',page)).status,409);
    assert.equal((await request('/api/session/children','POST',{name:''})).status,400);
  }finally{await new Promise(resolve=>server.close(resolve));accounts.close();}
});
