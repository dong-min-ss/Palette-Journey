import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { once } from 'node:events';
import { AccountStore } from '../backend/store.mjs';
import { createApp } from '../backend/server.mjs';

test('revisit cards match earlier, nonempty pages by family, child and city and can be dismissed', async () => {
  let now = Date.parse('2026-11-04T01:00:00Z');
  const accounts = new AccountStore(':memory:', () => now);
  accounts.mockLocationAccess = true;
  const token = () => randomBytes(32).toString('hex');
  const owner = token(), phone = token(), vehicle = token(), outsider = token();
  accounts.registerDemo(owner);
  const session = accounts.createSession(owner);
  accounts.joinSession(phone, session.code);
  accounts.joinSession(vehicle, session.code);
  const other = token();
  accounts.register(other);
  accounts.joinSession(outsider, accounts.createSession(other).code);
  const server = createApp(accounts);
  server.listen(0, '127.0.0.1');
  try {
    await once(server, 'listening');
    async function api(path, body, actor = phone, method = body ? 'PUT' : 'GET') {
      const response = await fetch('http://127.0.0.1:'+server.address().port+'/api/session/'+path, {
        method, headers: {Authorization: 'Bearer '+actor, 'Content-Type': 'application/json'},
        body: body && JSON.stringify(body),
      });
      assert.equal(response.status, 200, path);
      return response.json();
    }
    const library = await api('library');
    const child = library.children[0].id, diary = library.diaries[0].id;
    const state = 'revisit?childId='+child;
    const original = await api('pages/'+library.pages[0].id);
    const old = await api('pages/'+original.id, {...original, title:'경주 여행', regionId:'gyeongju',
      entryDate:'2026-11-02', text:'첨성대를 봤다'});
    const {regionId, ...legacySave} = old;
    assert.equal((await api('pages/'+old.id, legacySave)).regionId, regionId);
    for (const [entryDate, regionId, text] of [
      ['2026-11-03','gyeongju',''], ['2026-11-03','busan','바다'], ['2026-11-04','gyeongju','오늘'],
    ]) {
      const page = await api('diaries/'+diary+'/pages', {title:'페이지',template:'blank'},phone,'POST');
      await api('pages/'+page.id, {...page, entryDate, regionId, text});
    }
    const visit = async start => {
      for (let elapsed=0;elapsed<=120000;elapsed+=15000)
        await api('visits/mock', {source:'simulation',latitude:35.8347,longitude:129.219,timestamp:start+elapsed},vehicle,'POST');
    };
    assert.deepEqual(await api(state),{enabled:false,memory:null});
    await visit(0);
    await api('revisit',{enabled:true});
    assert.equal((await api(state)).memory,null);
    await visit(160000);
    const {memory} = await api(state);
    assert.equal(memory.pageId,old.id);
    assert.equal(memory.entryDate,'2026-11-02');
    assert.equal(memory.source,'simulation');
    const sibling = await api('children',{name:'동생'},phone,'POST');
    assert.equal((await api('revisit?childId='+sibling.id)).memory,null);
    assert.equal((await api(state,undefined,outsider)).memory,null);
    await api('revisit',memory,outsider);
    assert.equal((await api(state)).memory.visitId,memory.visitId);
    await api('revisit',memory);
    await api('visits/mock',{source:'simulation',latitude:35.8347,longitude:129.219,timestamp:295000},vehicle,'POST');
    assert.equal((await api(state)).memory,null);
    const route = await api('visits/drive',{action:'select',regionId:'gyeongju'},vehicle,'POST');
    async function tick(speedMps,gear) {
      now+=1000;
      await api('visits/drive',{action:'tick',source:'console-drive',tripId:route.tripId,
        valid:true,speedMps,gear,measuredAt:now},vehicle,'POST');
    }
    for(let i=0;i<=30;i++)await tick(10,8);
    for(let i=0;i<5;i++)await tick(0,4);
    assert.equal((await api(state)).memory,null);
    await tick(0,4);
    const next = (await api(state)).memory;
    assert.equal(next.pageId,old.id);
    assert.notEqual(next.visitId,memory.visitId);
    await api('revisit',{enabled:false});
    await api('revisit',{enabled:true});
    await tick(0,4);
    assert.equal((await api(state)).memory,null);
  } finally {
    await new Promise(resolve => server.close(resolve));
    accounts.close();
  }
});
