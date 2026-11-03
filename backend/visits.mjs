import { accountKey } from './store.mjs';
import { REGIONAL_COLORS, STICKERS, CATALOG_CITIES } from '../core/catalog.mjs';
import { collectRegionColor } from '../core/palette.mjs';

// Simulation policy only, not the administrative boundary or production policy.
export const MOCK_VISIT_POLICIES=Object.freeze(CATALOG_CITIES.map(city=>Object.freeze({source:'simulation',regionId:city.id,name:city.name,dwellMs:120000,maxGapMs:30000,center:{latitude:city.latitude,longitude:city.longitude},radiusMeters:5000})));
export const MOCK_VISIT_POLICY=MOCK_VISIT_POLICIES[0];
function inside(s,policy) {
  const rad=Math.PI/180, p=policy.center;
  const a=Math.sin((s.latitude-p.latitude)*rad/2)**2+Math.cos(p.latitude*rad)*Math.cos(s.latitude*rad)*Math.sin((s.longitude-p.longitude)*rad/2)**2;
  return 6371000*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a))<=policy.radiusMeters;
}
export class VisitStore {
  constructor(accounts){this.accounts=accounts;accounts.db.exec(`
    CREATE TABLE IF NOT EXISTS visit_progress(owner_hash TEXT PRIMARY KEY, entered INTEGER, last_sample INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS visit_rewards(owner_hash TEXT NOT NULL, region_id TEXT NOT NULL, source TEXT NOT NULL, acquired_at TEXT NOT NULL, PRIMARY KEY(owner_hash,region_id,source));`);
    if(!accounts.db.prepare('PRAGMA table_info(visit_progress)').all().some(c=>c.name==='region_id')){accounts.db.exec("ALTER TABLE visit_progress ADD COLUMN region_id TEXT; UPDATE visit_progress SET region_id='gyeongju' WHERE entered IS NOT NULL");}
  }
  state(token){const a=this.accounts,owner=a.participantOwner(token);return {policy:MOCK_VISIT_POLICY,policies:MOCK_VISIT_POLICIES,simulationEnabled:a.mockLocationAccess===true,rewards:a.db.prepare('SELECT region_id AS regionId, source, acquired_at AS acquiredAt FROM visit_rewards WHERE owner_hash=?').all(owner)};}
  vehicle(token,s){
    const a=this.accounts; a.participantOwner(token);
    if(a.mockLocationAccess!==true)throw new Error('SIMULATION_DISABLED');
    if(!s||s.source!=='pleos-emulator')throw new Error('INVALID_LOCATION');
    const now=a.now();
    const fresh=s.valid===true&&Number.isSafeInteger(s.measuredAt)&&s.measuredAt<=now+5000&&now-s.measuredAt<=30000;
    if(fresh&&(!Number.isFinite(s.latitude)||Math.abs(s.latitude)>90||!Number.isFinite(s.longitude)||Math.abs(s.longitude)>180))throw new Error('INVALID_LOCATION');
    const result=this.sample(token,{source:'simulation',timestamp:now,latitude:fresh?s.latitude:90,longitude:fresh?s.longitude:0},'vehicle-'+accountKey(token));
    return {...result,locationFresh:fresh,inRegion:fresh&&!!result.regionId,input:'pleos-emulator'};
  }
  sample(token,s,stream=null){
    const a=this.accounts,owner=a.participantOwner(token);
    if(a.mockLocationAccess!==true)throw new Error('SIMULATION_DISABLED');
    if(!s||s.source!=='simulation'||!Number.isFinite(s.latitude)||Math.abs(s.latitude)>90||!Number.isFinite(s.longitude)||Math.abs(s.longitude)>180||!Number.isSafeInteger(s.timestamp)||s.timestamp<0)throw new Error('INVALID_LOCATION');
    const progressKey=stream??owner;
    const db=a.db;db.exec('BEGIN IMMEDIATE');
    try{
      const previous=db.prepare('SELECT * FROM visit_progress WHERE owner_hash=?').get(progressKey);
      if(previous&&s.timestamp<=previous.last_sample){db.exec('COMMIT');return {...this.state(token),ignored:true};}
      const policy=MOCK_VISIT_POLICIES.find(p=>inside(s,p));let entered=null;
      if(policy)entered=previous&&previous.entered!==null&&previous.region_id===policy.regionId&&s.timestamp-previous.last_sample<=policy.maxGapMs?previous.entered:s.timestamp;
      db.prepare('INSERT INTO visit_progress(owner_hash,entered,last_sample,region_id) VALUES (?,?,?,?) ON CONFLICT(owner_hash) DO UPDATE SET entered=excluded.entered,last_sample=excluded.last_sample,region_id=excluded.region_id').run(progressKey,entered,s.timestamp,policy?.regionId??null);
      let awarded=false;
      if(entered!==null&&s.timestamp-entered>=policy.dwellMs){
        awarded=this.award(owner,policy);
      }
      db.exec('COMMIT');return {...this.state(token),regionId:policy?.regionId??null,regionName:policy?.name??null,awarded,dwellMs:entered===null?0:s.timestamp-entered};
    }catch(e){db.exec('ROLLBACK');throw e;}
  }
  // Caller holds the same SQLite transaction as its visit/drive progress update.
  award(owner,policy){
    const a=this.accounts,db=a.db;
    const acquiredAt=new Date(a.now()).toISOString();
    const awarded=!!db.prepare('INSERT OR IGNORE INTO visit_rewards VALUES (?,?,?,?)').run(owner,policy.regionId,'simulation',acquiredAt).changes;
    if(awarded){
      let palette=a.getByKey(owner);const regionColors=REGIONAL_COLORS.filter(c=>c.regionId===policy.regionId);
      for(const color of regionColors)palette=collectRegionColor(palette,color,acquiredAt);
      palette={...palette,colors:palette.colors.map(c=>c.source==='region'&&regionColors.some(r=>'region-'+r.id===c.id)?{...c,visitSource:'simulation'}:c)};
      db.prepare('UPDATE accounts SET palette=? WHERE token_hash=?').run(JSON.stringify(palette),owner);
      for(const sticker of STICKERS.filter(s=>s.regionId===policy.regionId))db.prepare('INSERT OR IGNORE INTO sticker_ownership VALUES (?,?,?)').run(owner,sticker.id,'simulation-'+policy.regionId);
    }
    return awarded;
  }
}
