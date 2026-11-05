import { randomUUID } from 'node:crypto';
import { validateStrokes } from '../core/artwork.mjs';
import { validateStickerPlacements } from '../core/journey.mjs';
import { CATALOG_CITIES } from '../core/catalog.mjs';
import { initializeStickers, ownedStickers } from './stickers.mjs';

function name(value, max = 80) {
  if (typeof value !== 'string' || !value.trim() || value.length > max) throw new Error('INVALID_DIARY');
  return value.trim();
}
function entryDate(value,now=Date.now()){
 const day=value??new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Seoul'}).format(new Date(now));
 if(typeof day!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(day)||!Number.isFinite(Date.parse(day+'T00:00:00Z'))||new Date(day+'T00:00:00Z').toISOString().slice(0,10)!==day)throw new Error('INVALID_DIARY');return day;
}
export class DiaryStore {
  constructor(accounts) {
    this.accounts = accounts;
    initializeStickers(accounts);
    this.db = accounts.db;
    this.db.exec(`PRAGMA foreign_keys=ON;
      CREATE TABLE IF NOT EXISTS families(id TEXT PRIMARY KEY, owner_hash TEXT NOT NULL UNIQUE);
      CREATE TABLE IF NOT EXISTS children(id TEXT PRIMARY KEY, family_id TEXT NOT NULL REFERENCES families(id), name TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS trips(id TEXT PRIMARY KEY, family_id TEXT NOT NULL REFERENCES families(id), title TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS diaries(id TEXT PRIMARY KEY, trip_id TEXT NOT NULL REFERENCES trips(id), child_id TEXT NOT NULL REFERENCES children(id), title TEXT NOT NULL, created_at TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS pages(id TEXT PRIMARY KEY, diary_id TEXT NOT NULL REFERENCES diaries(id), position INTEGER NOT NULL, data TEXT NOT NULL, revision INTEGER NOT NULL, UNIQUE(diary_id,position));`);
  }
  family(token) {
    const owner = this.accounts.participantOwner(token);
    const family = this.db.prepare('SELECT id FROM families WHERE owner_hash=?').get(owner);
    if (family) return family.id;
    this.db.exec('BEGIN IMMEDIATE');
    try {
      // Recheck after obtaining the write lock, including across server processes.
      const existing = this.db.prepare('SELECT id FROM families WHERE owner_hash=?').get(owner);
      if (existing) { this.db.exec('COMMIT'); return existing.id; }
      const id=randomUUID(), child=randomUUID(), trip=randomUUID(), diary=randomUUID();
      this.db.prepare('INSERT INTO families VALUES (?,?)').run(id,owner);
      this.db.prepare('INSERT INTO children VALUES (?,?,?)').run(child,id,'우리 아이');
      this.db.prepare('INSERT INTO trips VALUES (?,?,?)').run(trip,id,'우리 가족의 여행');
      this.db.prepare('INSERT INTO diaries VALUES (?,?,?,?,?)').run(diary,trip,child,'첫 여행 그림일기',new Date(this.accounts.now()).toISOString());
      const original=this.accounts.artwork(token);
      this.db.prepare('INSERT INTO pages VALUES (?,?,?,?,?)').run(randomUUID(),diary,0,
        JSON.stringify({ title:'첫 페이지',text:'',template:'blank',strokes:original.strokes,legacyRegions:original.legacyRegions }),0);
      this.db.exec('COMMIT'); return id;
    } catch(error) {this.db.exec('ROLLBACK');throw error;}
  }
  library(token) {
    const familyId=this.family(token);
    return { familyId, visibility:'family-private',
      children:this.db.prepare('SELECT id,name FROM children WHERE family_id=? ORDER BY rowid').all(familyId),
      trips:this.db.prepare('SELECT id,title FROM trips WHERE family_id=? ORDER BY rowid').all(familyId),
      diaries:this.db.prepare(`SELECT d.id,d.trip_id AS tripId,d.child_id AS childId,d.title,d.created_at AS createdAt
        FROM diaries d JOIN trips t ON t.id=d.trip_id WHERE t.family_id=? ORDER BY d.rowid`).all(familyId),
      pages:this.db.prepare(`SELECT p.id,p.diary_id AS diaryId,p.position,p.revision,json_extract(p.data,'$.title') AS title,coalesce(json_extract(p.data,'$.entryDate'),strftime('%Y-%m-%d',d.created_at,'+9 hours')) AS entryDate
        FROM pages p JOIN diaries d ON d.id=p.diary_id JOIN trips t ON t.id=d.trip_id WHERE t.family_id=? ORDER BY p.position`).all(familyId) };
  }
  createChild(token, body) {
    const family=this.family(token), childName=name(body?.name,40), id=randomUUID();
    this.db.prepare('INSERT INTO children VALUES (?,?,?)').run(id,family,childName);
    return {id,name:childName};
  }
  createTrip(token,body) {
    const family=this.family(token), title=name(body?.title), id=randomUUID();
    this.db.prepare('INSERT INTO trips VALUES (?,?,?)').run(id,family,title);return {id,title};
  }
  createDiary(token,body) {
    const family=this.family(token), title=name(body?.title), id=randomUUID();
    if(typeof body?.childId!=='string'||typeof body?.tripId!=='string')throw new Error('INVALID_DIARY');
    const child=this.db.prepare('SELECT id FROM children WHERE id=? AND family_id=?').get(body?.childId??'',family);
    const trip=this.db.prepare('SELECT id FROM trips WHERE id=? AND family_id=?').get(body?.tripId??'',family);
    if(!child||!trip)throw new Error('DIARY_NOT_FOUND');
    this.db.prepare('INSERT INTO diaries VALUES (?,?,?,?,?)').run(id,trip.id,child.id,title,new Date(this.accounts.now()).toISOString());
    return {id,title,childId:child.id,tripId:trip.id};
  }
  diary(token,id) {
    const family=this.family(token);
    const row=this.db.prepare(`SELECT d.id FROM diaries d JOIN trips t ON t.id=d.trip_id WHERE d.id=? AND t.family_id=?`).get(id,family);
    if(!row)throw new Error('DIARY_NOT_FOUND');return row;
  }
  createPage(token,diaryId,body) {
    this.diary(token,diaryId);
    const title=name(body?.title),template=body?.template;
    if(!['blank','landscape'].includes(template))throw new Error('INVALID_DIARY');
    this.db.exec('BEGIN IMMEDIATE');
    try{
      const position=this.db.prepare('SELECT coalesce(max(position),-1)+1 AS n FROM pages WHERE diary_id=?').get(diaryId).n;
      const id=randomUUID(),data={title,entryDate:entryDate(body?.entryDate,this.accounts.now()),text:'',template,strokes:[],legacyRegions:{}};
      this.db.prepare('INSERT INTO pages VALUES (?,?,?,?,?)').run(id,diaryId,position,JSON.stringify(data),0);
      this.db.exec('COMMIT');return {id,diaryId,position,...data,revision:0};
    }catch(error){this.db.exec('ROLLBACK');throw error;}
  }
  page(token,id) {
    const family=this.family(token);
    const row=this.db.prepare(`SELECT p.*,d.created_at AS diary_created FROM pages p JOIN diaries d ON d.id=p.diary_id
      JOIN trips t ON t.id=d.trip_id WHERE p.id=? AND t.family_id=?`).get(id,family);
    if(!row)throw new Error('DIARY_NOT_FOUND');
    return {id:row.id,diaryId:row.diary_id,position:row.position,entryDate:entryDate(undefined,Date.parse(row.diary_created)),stickers:[],...JSON.parse(row.data),revision:row.revision};
  }
  savePage(token,id,body) {
    const title=name(body?.title);
    if(typeof body?.text!=='string'||body.text.length>2000||!Number.isSafeInteger(body.revision)||body.revision<0)throw new Error('INVALID_DIARY');
    // Resolve family before entering the transaction (first access may initialize it).
    this.family(token);
    this.db.exec('BEGIN IMMEDIATE');
    try{
      const current=this.page(token,id);
      const regionId=body.regionId===undefined?current.regionId??null:body.regionId;
      if(regionId!==null&&!CATALOG_CITIES.some(city=>city.id===regionId))throw new Error('INVALID_DIARY');
      if(current.revision!==body.revision)throw new Error('ARTWORK_CONFLICT');
      if(body.template!==current.template)throw new Error('INVALID_DIARY');
      const strokes=validateStrokes(body.strokes,this.accounts.sessionPalette(token));
      // Old clients must preserve new sticker data rather than silently removing it.
      const placements=body.stickers??current.stickers;
      const geometry=validateStickerPlacements(placements,ownedStickers(this.accounts,token));
      let totalPoints=strokes.reduce((sum,s)=>sum+s.points.length,0);
      const stickers=geometry.map((item,i)=>{
        const paint=validateStrokes(placements[i].strokes??[],this.accounts.sessionPalette(token));
        totalPoints+=paint.reduce((sum,s)=>sum+s.points.length,0);
        return {...item,strokes:paint};
      });
      if(totalPoints>20000)throw new Error('INVALID_ARTWORK');
      const data={title,entryDate:entryDate(body.entryDate??current.entryDate),regionId,text:body.text,template:current.template,strokes,stickers,legacyRegions:current.legacyRegions};
      this.db.prepare('UPDATE pages SET data=?,revision=revision+1 WHERE id=?').run(JSON.stringify(data),id);
      this.db.exec('COMMIT');return {...current,...data,revision:current.revision+1};
    }catch(error){this.db.exec('ROLLBACK');throw error;}
  }
}
