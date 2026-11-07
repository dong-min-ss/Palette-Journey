import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {readFileSync,mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {AccountStore} from '../backend/store.mjs';
import {DiaryStore} from '../backend/diaries.mjs';
import {ownedStickers} from '../backend/stickers.mjs';
import {contentCatalog,STICKERS} from '../core/catalog.mjs';
const credential=()=>randomBytes(32).toString('hex');
const placement=(id,stickerId='basic-star')=>({id,stickerId,x:100,y:100,width:120,height:150,strokes:[{tool:'paint',color:'#F44336',width:12,points:[[100,100],[200,200]]}]});
test('reusable sticker instances keep independent paint, geometry, restart and backwards compatibility',()=>{
  const dir=mkdtempSync(join(tmpdir(),'palette-stickers-')),path=join(dir,'db.sqlite');let accounts=new AccountStore(path);
  try{
    const owner=credential(),member=credential();accounts.register(owner);accounts.joinSession(member,accounts.createSession(owner).code);
    let diaries=new DiaryStore(accounts),page=diaries.page(member,diaries.library(member).pages[0].id);
    const first=placement('first'),second={...placement('second'),x:300,strokes:[]};
    const saved=diaries.savePage(member,page.id,{...page,stickers:[first,second]});
    assert.deepEqual(saved.stickers,[first,second]);
    const moved={...saved.stickers[0],x:50,width:230,height:60};
    const updated=diaries.savePage(member,page.id,{...saved,stickers:[moved,second]});
    assert.deepEqual(updated.stickers[0].strokes,first.strokes);
    const oldClient={...updated};delete oldClient.stickers;
    assert.deepEqual(diaries.savePage(member,page.id,oldClient).stickers,updated.stickers);
    accounts.close();accounts=new AccountStore(path);diaries=new DiaryStore(accounts);
    assert.deepEqual(diaries.page(member,page.id).stickers,updated.stickers);
  }finally{accounts.close();rmSync(dir,{recursive:true});}
});
test('server denies locked regional, duplicate, overflow, unknown and unowned sticker paint',()=>{
  const accounts=new AccountStore(':memory:'),diaries=new DiaryStore(accounts),owner=credential(),member=credential();
  try{
    accounts.register(owner);accounts.joinSession(member,accounts.createSession(owner).code);
    const page=diaries.page(member,diaries.library(member).pages[0].id);
    for(const stickers of [[placement('a','gyeongju-cheomseongdae')],[placement('a','unknown')],[placement('a'),placement('a')],[{...placement('a'),x:599}],[{...placement('a'),width:NaN}]]){
      assert.throws(()=>diaries.savePage(member,page.id,{...page,stickers}),/INVALID_STICKER_PLACEMENT/);
    }
    assert.throws(()=>diaries.savePage(member,page.id,{...page,stickers:[{...placement('a'),strokes:[{tool:'paint',color:'#123ABC',width:12,points:[[1,1]]}]}]}),/INVALID_ARTWORK/);
    // Test-only verified entitlement fixture, no HTTP endpoint can self-award it.
    accounts.db.prepare('INSERT INTO sticker_ownership VALUES (?,?,?)').run(accounts.participantOwner(member),'gyeongju-cheomseongdae','verified-fixture');
    assert.ok(contentCatalog(accounts.get(owner),ownedStickers(accounts,member)).stickers.find(s=>s.id==='gyeongju-cheomseongdae').unlocked);
    assert.equal(diaries.savePage(member,page.id,{...page,stickers:[placement('tower','gyeongju-cheomseongdae')]}).stickers.length,1);
  }finally{accounts.close();}
});
test('all sticker assets are monochrome and outline overlays have no opaque fills',()=>{
  for(const asset of STICKERS.map(s=>s.asset.split('/').at(-1).slice(0,-4))){
    const svg=readFileSync(new URL(`../web/assets/stickers/${asset}.svg`,import.meta.url),'utf8');
    assert.ok([...svg.matchAll(/#[0-9A-Fa-f]{6}/g)].every(m=>['#FFFFFF','#111111'].includes(m[0])));
    const outline=readFileSync(new URL(`../web/assets/stickers/${asset}-outline.svg`,import.meta.url),'utf8');
    assert.equal(/fill="#FFFFFF"/.test(outline),false);
    assert.match(svg,/preserveAspectRatio="none"/);
  }
});


test('corner resize anchors the opposite corner and clamps every edge without changing paint',async()=>{
  const {resizeSticker}=await import('../web/stickers.mjs');
  const box={x:100,y:80,width:140,height:120};
  for(const corner of ['nw','ne','sw','se'])for(const [dx,dy] of [[35,50],[-1000,-1000],[1000,1000]]){
    const result=resizeSticker(box,corner,dx,dy);
    assert.ok(result.x>=0&&result.y>=0&&result.width>=20&&result.height>=20);
    assert.ok(result.x+result.width<=600&&result.y+result.height<=400);
    assert.equal(corner.includes('w')?result.x+result.width:result.x,corner.includes('w')?240:100);
    assert.equal(corner.includes('n')?result.y+result.height:result.y,corner.includes('n')?200:80);
  }
});
test('explicit development unlock permits regional save without fabricating visit ownership',()=>{
  const accounts=new AccountStore(':memory:'),diaries=new DiaryStore(accounts),owner=credential(),member=credential();
  try{
    accounts.register(owner);accounts.joinSession(member,accounts.createSession(owner).code);
    accounts.developmentStickerAccess=true;
    const catalog=contentCatalog(accounts.get(owner),ownedStickers(accounts,member),true);
    assert.equal(catalog.stickers.filter(s=>s.developmentUnlocked&&s.unlocked).length,10);
    let page=diaries.page(member,diaries.library(member).pages[0].id);
    page=diaries.savePage(member,page.id,{...page,stickers:[placement('tower','gyeongju-cheomseongdae'),placement('crown','gyeongju-silla-crown')]});
    assert.equal(page.stickers.length,2);
    assert.equal(accounts.db.prepare('SELECT count(*) AS n FROM sticker_ownership').get().n,0);
    accounts.developmentStickerAccess=false;
    assert.equal(ownedStickers(accounts,member).length,2);
    assert.throws(()=>diaries.savePage(member,page.id,page),/INVALID_STICKER_PLACEMENT/);
  }finally{accounts.close();}
});
