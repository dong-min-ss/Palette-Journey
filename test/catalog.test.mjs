import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {contentCatalog,STICKERS,CATALOG_CITIES} from '../core/catalog.mjs';
import {createPalette} from '../core/palette.mjs';
import {AccountStore} from '../backend/store.mjs';
import {createApp} from '../backend/server.mjs';

test('catalog distinguishes basics and locked Gyeongju rewards by content id rather than HEX',()=>{
  const palette=createPalette(),catalog=contentCatalog(palette);
  assert.equal(catalog.stickers.filter(s=>s.kind==='basic'&&s.unlocked).length,2);
  assert.equal(catalog.stickers.filter(s=>s.kind==='regional'&&!s.unlocked).length,10);
  assert.equal(catalog.colors.length,15);
  assert.ok(catalog.colors.every(c=>!c.unlocked&&!c.acquired));
  assert.equal(catalog.basicColors.length,11);assert.equal(catalog.cities.length,5);
  for(const city of catalog.cities){assert.deepEqual(city.colors,{acquired:0,total:3});assert.deepEqual(city.stickers,{acquired:0,total:2});}
  assert.deepEqual(catalog.cities.filter(c=>c.groupId==='gyeongsang').map(c=>c.id),['gyeongju','busan']);
  assert.equal(new Set([...STICKERS,...catalog.colors].map(i=>(i.asset?'sticker:':'color:')+i.id)).size,27);
  palette.colors.push({id:'personal-gold',hex:catalog.colors[1].hex});
  assert.equal(contentCatalog(palette).colors[1].unlocked,false);
  palette.colors.push({id:'region-gyeongju-gold',hex:catalog.colors[1].hex});
  assert.equal(contentCatalog(palette).colors[1].unlocked,true);
});

test('development access is usable but never counted as collected city content',()=>{
 const catalog=contentCatalog(createPalette(),STICKERS.map(s=>({stickerId:s.id,visitId:null})),true);
 assert.equal(catalog.stickers.filter(i=>i.unlocked).length,12);assert.equal(catalog.stickers.filter(i=>i.acquired).length,2);
 assert.ok(catalog.cities.every(c=>c.colors.acquired===0&&c.stickers.acquired===0));
 assert.ok(catalog.stickers.filter(i=>i.kind==='regional').every(i=>i.developmentUnlocked&&!i.acquired));
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
      const outline=await fetch(base+sticker.asset.replace('.svg','-outline.svg'));assert.equal(outline.status,200);assert.match(await outline.text(),/<svg/);
    }
    assert.deepEqual(store.get(owner),createPalette());
    assert.equal((await fetch(base+'/assets/stickers/missing.svg')).status!==200,true);
  }finally{await new Promise(resolve=>server.close(resolve));store.close();}
});
