import {randomUUID} from 'node:crypto';
import {accountKey} from './store.mjs';
import {MOCK_VISIT_POLICIES} from './visits.mjs';
import {REGIONAL_COLORS,contentCatalog} from '../core/catalog.mjs';
import {ownedStickers} from './stickers.mjs';

export const DRIVE_POLICY=Object.freeze({routeMeters:300,parkMs:5000,maxGapMs:5000,stopSpeedMps:0.2});
// Explicit demo route, independent of the device GPS/120-second geofence stream.
export class DriveStore{
 constructor(accounts,visits){this.a=accounts;this.visits=visits;accounts.db.exec(`CREATE TABLE IF NOT EXISTS drive_progress(
  actor_hash TEXT PRIMARY KEY,owner_hash TEXT NOT NULL,trip_id TEXT NOT NULL,region_id TEXT NOT NULL,
  distance REAL NOT NULL,last_sample INTEGER,last_speed REAL,last_gear INTEGER,last_valid INTEGER NOT NULL DEFAULT 0,parked_at INTEGER);`);}
 owner(token){const a=this.a,owner=a.participantOwner(token);if(a.mockLocationAccess!==true)throw Error('SIMULATION_DISABLED');if(!a.db.prepare('SELECT 1 FROM demo_accounts WHERE owner_hash=?').get(owner))throw Error('DEMO_ONLY');return owner;}
 state(token){const owner=this.owner(token),a=this.a,row=a.db.prepare('SELECT * FROM drive_progress WHERE actor_hash=? AND owner_hash=?').get(accountKey(token),owner);const policy=MOCK_VISIT_POLICIES.find(p=>p.regionId===row?.region_id),distance=row?.distance??0;
  const collected=!!policy&&!!a.db.prepare("SELECT 1 FROM visit_rewards WHERE owner_hash=? AND region_id=? AND source='simulation'").get(owner,policy.regionId);
  const fraction=distance/DRIVE_POLICY.routeMeters;
  return {input:'console-drive',policy:DRIVE_POLICY,tripId:row?.trip_id??null,regionId:policy?.regionId??null,regionName:policy?.name??null,distanceMeters:distance,arrived:distance>=DRIVE_POLICY.routeMeters,parkMs:row?.parked_at==null||a.now()-row.last_sample>DRIVE_POLICY.maxGapMs?0:Math.min(DRIVE_POLICY.parkMs,Math.max(0,a.now()-row.parked_at)),collected,
   latitude:policy?policy.center.latitude+0.06*(1-fraction):null,longitude:policy?.center.longitude??null,
   catalog:contentCatalog(a.sessionPalette(token),ownedStickers(a,token),a.developmentStickers(token))};}
 select(token,regionId){const owner=this.owner(token),a=this.a;if(!MOCK_VISIT_POLICIES.some(p=>p.regionId===regionId))throw Error('INVALID_DRIVE');
  a.db.prepare(`INSERT INTO drive_progress(actor_hash,owner_hash,trip_id,region_id,distance) VALUES (?,?,?,?,0)
   ON CONFLICT(actor_hash) DO UPDATE SET owner_hash=excluded.owner_hash,trip_id=excluded.trip_id,region_id=excluded.region_id,distance=0,last_sample=NULL,last_speed=NULL,last_gear=NULL,last_valid=0,parked_at=NULL`).run(accountKey(token),owner,randomUUID(),regionId);return this.state(token);}
 tick(token,s){const owner=this.owner(token),a=this.a,db=a.db;if(!s||s.source!=='console-drive'||typeof s.tripId!=='string')throw Error('INVALID_DRIVE');
  if(s.valid===true&&(!Number.isFinite(s.speedMps)||s.speedMps<0||s.speedMps>100||![1,2,4,8].includes(s.gear)))throw Error('INVALID_DRIVE');
  db.exec('BEGIN IMMEDIATE');try{
   const row=db.prepare('SELECT * FROM drive_progress WHERE actor_hash=? AND owner_hash=?').get(accountKey(token),owner);if(!row||row.trip_id!==s.tripId)throw Error('DRIVE_NOT_STARTED');
   const now=a.now(),fresh=s.valid===true&&Number.isSafeInteger(s.measuredAt)&&now-s.measuredAt<=30000&&s.measuredAt<=now+5000;
   const elapsed=row.last_sample==null?0:now-row.last_sample,continuous=elapsed>0&&elapsed<=DRIVE_POLICY.maxGapMs;
   let distance=row.distance,parked=null;
   // Never reconstruct background travel; both ends must be fresh D samples.
   if(fresh&&continuous&&row.last_valid&&row.last_gear===8&&s.gear===8)distance=Math.min(DRIVE_POLICY.routeMeters,distance+Math.min(row.last_speed,s.speedMps)*elapsed/1000);
   if(distance>=DRIVE_POLICY.routeMeters&&fresh&&s.gear===4&&s.speedMps<=DRIVE_POLICY.stopSpeedMps){parked=continuous&&row.last_valid&&row.last_gear===4&&row.last_speed<=DRIVE_POLICY.stopSpeedMps&&row.parked_at!=null?row.parked_at:now;}
   db.prepare('UPDATE drive_progress SET distance=?,last_sample=?,last_speed=?,last_gear=?,last_valid=?,parked_at=? WHERE actor_hash=?').run(distance,now,fresh?s.speedMps:0,fresh?s.gear:0,fresh?1:0,parked,accountKey(token));
   const awarded=parked!=null&&now-parked>=DRIVE_POLICY.parkMs?this.visits.award(owner,MOCK_VISIT_POLICIES.find(p=>p.regionId===row.region_id)):false;
   db.exec('COMMIT');return {...this.state(token),awarded,signalValid:fresh};
  }catch(e){db.exec('ROLLBACK');throw e;}}
 reset(token){const owner=this.owner(token),a=this.a,db=a.db;db.exec('BEGIN IMMEDIATE');try{
  const palette=a.getByKey(owner),ids=new Set(REGIONAL_COLORS.map(c=>'region-'+c.id));
  db.prepare('UPDATE accounts SET palette=? WHERE token_hash=?').run(JSON.stringify({...palette,colors:palette.colors.filter(c=>!(c.source==='region'&&c.visitSource==='simulation'&&ids.has(c.id)))}),owner);
  for(const p of MOCK_VISIT_POLICIES)db.prepare('DELETE FROM sticker_ownership WHERE owner_hash=? AND visit_id=?').run(owner,'simulation-'+p.regionId);
  db.prepare("DELETE FROM visit_rewards WHERE owner_hash=? AND source='simulation'").run(owner);
  db.prepare("DELETE FROM visit_progress WHERE owner_hash=? OR owner_hash IN (SELECT 'vehicle-'||p.token_hash FROM participants p JOIN sessions s ON s.id=p.session_id WHERE s.owner_hash=?)").run(owner,owner);
  db.prepare('DELETE FROM drive_progress WHERE owner_hash=?').run(owner);
  db.exec('COMMIT');return {...this.state(token),reset:true};
 }catch(e){db.exec('ROLLBACK');throw e;}}
}
