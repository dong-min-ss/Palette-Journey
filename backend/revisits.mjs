import { randomUUID } from 'node:crypto';
import { CATALOG_CITIES } from '../core/catalog.mjs';
import { selectRevisitMemory } from '../core/journey.mjs';

export class RevisitStore {
  constructor(accounts) {
    this.accounts = accounts;
    this.db = accounts.db;
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS revisit_settings(owner_hash TEXT PRIMARY KEY, enabled INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS revisit_arrivals(owner_hash TEXT PRIMARY KEY, id TEXT NOT NULL, region_id TEXT NOT NULL, entered_at TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS revisit_dismissed(visit_id TEXT NOT NULL, child_id TEXT NOT NULL, PRIMARY KEY(visit_id,child_id));
    `);
  }

  // Called inside the transaction that confirms the simulated arrival.
  arrive(owner, regionId) {
    if (!this.enabled(owner)) return;
    this.clear(owner);
    this.db.prepare('INSERT INTO revisit_arrivals VALUES (?,?,?,?)')
      .run(owner, randomUUID(), regionId, new Date(this.accounts.now()).toISOString());
  }

  enabled(owner) {
    return this.db.prepare('SELECT enabled FROM revisit_settings WHERE owner_hash=?').get(owner)?.enabled === 1;
  }

  clear(owner) {
    this.db.prepare('DELETE FROM revisit_dismissed WHERE visit_id IN (SELECT id FROM revisit_arrivals WHERE owner_hash=?)').run(owner);
    this.db.prepare('DELETE FROM revisit_arrivals WHERE owner_hash=?').run(owner);
  }

  state(token, childId) {
    const owner = this.accounts.participantOwner(token);
    const result = { enabled: this.enabled(owner), memory: null };
    if (!result.enabled || !childId) return result;
    const arrival = this.db.prepare('SELECT * FROM revisit_arrivals WHERE owner_hash=?').get(owner);
    if (!arrival || this.db.prepare('SELECT 1 FROM revisit_dismissed WHERE visit_id=? AND child_id=?').get(arrival.id,childId)) return result;
    const pages = this.db.prepare(`
      SELECT p.id, d.trip_id AS tripId, f.id AS familyId, d.child_id AS childId, p.data,
        coalesce(json_extract(p.data,'$.entryDate'),strftime('%Y-%m-%d',d.created_at,'+9 hours')) AS entryDate,
        v.audio IS NOT NULL AS hasAudio
      FROM pages p JOIN diaries d ON d.id=p.diary_id JOIN trips t ON t.id=d.trip_id
      JOIN families f ON f.id=t.family_id LEFT JOIN page_voice v ON v.page_id=p.id
      WHERE f.owner_hash=? AND d.child_id=? AND json_extract(p.data,'$.regionId')=?
    `).all(owner,childId,arrival.region_id).map(row => {
      const page = JSON.parse(row.data);
      return { ...row, regionId: arrival.region_id, createdAt: row.entryDate+'T00:00:00+09:00',
        title: page.title, hasContent: !!(page.text?.trim() || page.strokes?.length ||
          page.stickers?.length || Object.keys(page.legacyRegions ?? {}).length || row.hasAudio) };
    });
    if (!pages.length) return result;
    // Location streams have their own arrival IDs, independent of user-created travel books.
    const memory = selectRevisitMemory({ diaries: pages, familyId: pages[0].familyId, childId,
      visit: { id: arrival.id, tripId: arrival.id, regionId: arrival.region_id, enteredAt: arrival.entered_at, verified: true },
      enabled: true });
    if (memory) {
      const page = pages.find(item => item.id === memory.diaryId);
      result.memory = { visitId: arrival.id, pageId: page.id, childId, entryDate: page.entryDate,
        title: page.title, regionName: CATALOG_CITIES.find(city => city.id === arrival.region_id).name,
        source: 'simulation' };
    }
    return result;
  }

  update(token, body) {
    const owner = this.accounts.participantOwner(token);
    if (typeof body?.enabled === 'boolean') {
      this.db.exec('BEGIN IMMEDIATE');
      try {
        this.db.prepare('INSERT INTO revisit_settings VALUES (?,?) ON CONFLICT(owner_hash) DO UPDATE SET enabled=excluded.enabled')
          .run(owner, Number(body.enabled));
        if (!body.enabled) this.clear(owner);
        this.db.exec('COMMIT');
      } catch (error) {
        this.db.exec('ROLLBACK');
        throw error;
      }
      return this.state(token);
    }
    if (typeof body?.visitId !== 'string' || typeof body?.childId !== 'string') throw Error('INVALID_REVISIT');
    this.db.prepare(`INSERT OR IGNORE INTO revisit_dismissed
      SELECT a.id,c.id FROM revisit_arrivals a JOIN families f ON f.owner_hash=a.owner_hash
      JOIN children c ON c.family_id=f.id WHERE a.owner_hash=? AND a.id=? AND c.id=?`)
      .run(owner,body.visitId,body.childId);
    return this.state(token,body.childId);
  }
}
