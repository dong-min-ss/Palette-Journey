import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {contentCatalog} from '../core/catalog.mjs';
import {createPalette} from '../core/palette.mjs';
import {AccountStore} from '../backend/store.mjs';
import {createApp} from '../backend/server.mjs';

test('catalog distinguishes basics and locked Gyeongju rewards by content id rather than HEX',()=>{
  const palette=createPalette(),catalog=contentCatalog(palette);
  assert.equal(catalog.stickers.filter(s=>s.kind==='basic'&&s.unlocked).length,2);
  assert.equal(catalog.stickers.filter(s=>s.kind==='regional'&&!s.unlocked).length,2);
  assert.equal(catalog.colors.length,3);
  assert.ok(catalog.colors.every(c=>c.regionId==='gyeongju'&&!c.unlocked));
  palette.colors.push({id:'personal-gold',hex:catalog.colors[1].hex});
  assert.equal(contentCatalog(palette).colors[1].unlocked,false);
  palette.colors.push({id:'region-gyeongju-gold',hex:catalog.colors[1].hex});
  assert.equal(contentCatalog(palette).colors[1].unlocked,true);
  assert.equal(palette.tickets,1);
});
test('session catalog and SVG assets are served without granting regional rewards',async()=>{
  const store=new AccountStore(':memory:'),owner=randomBytes(32).toString('hex'),member=randomBytes(32).toString('hex');
  store.register(owner);store.joinSession(member,store.createSession(owner).code);
  const server=createApp(store);await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const base=`http://127.0.0.1:${server.address().port}`;
  try{
    assert.equal((await fetch(base+'/api/session/catalog')).status,401);
    const response=await fetch(base+'/api/session/catalog',{headers:{Authorization:`Bearer ${member}`}});
    assert.equal(response.status,200);const catalog=await response.json();
    for(const sticker of catalog.stickers){
      const asset=await fetch(base+sticker.asset);assert.equal(asset.status,200);
      assert.equal(asset.headers.get('content-type'),'image/svg+xml');assert.match(await asset.text(),/<svg/);
    }
    assert.deepEqual(store.get(owner),createPalette());
    assert.equal((await fetch(base+'/assets/stickers/missing.svg')).status!==200,true);
  }finally{await new Promise(resolve=>server.close(resolve));store.close();}
});
