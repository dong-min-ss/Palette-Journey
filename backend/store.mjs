import { DatabaseSync } from 'node:sqlite';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { createPalette } from '../core/palette.mjs';

export function accountKey(token) {
  if (typeof token !== 'string' || !/^[a-f0-9]{64}$/.test(token)) throw new Error('UNAUTHORIZED');
  return createHash('sha256').update(token).digest('hex');
}

export class AccountStore {
  constructor(path, now = Date.now) {
    this.now = now;
    this.db = new DatabaseSync(path);
    this.db.exec(`PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;
      CREATE TABLE IF NOT EXISTS demo_accounts (owner_hash TEXT PRIMARY KEY);
      CREATE TABLE IF NOT EXISTS accounts (
        token_hash TEXT PRIMARY KEY, palette TEXT NOT NULL
      );
      CREATE TABLE IF NOT EXISTS sessions (
        id TEXT PRIMARY KEY, owner_hash TEXT NOT NULL, code_hash TEXT UNIQUE NOT NULL,
        join_expires INTEGER NOT NULL, expires INTEGER NOT NULL, revoked INTEGER NOT NULL DEFAULT 0
      );
      CREATE TABLE IF NOT EXISTS participants (
        token_hash TEXT PRIMARY KEY, session_id TEXT NOT NULL
      );
      CREATE TABLE IF NOT EXISTS invitations (code_hash TEXT PRIMARY KEY, session_id TEXT NOT NULL, issuer TEXT NOT NULL, expires INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS session_focus (session_id TEXT PRIMARY KEY, page_id TEXT, revision INTEGER NOT NULL);
      CREATE TABLE IF NOT EXISTS artworks (
        owner_hash TEXT PRIMARY KEY, regions TEXT NOT NULL, revision INTEGER NOT NULL
      );`);
  }
  register(token) {
    const key = accountKey(token);
    this.db.prepare('INSERT OR IGNORE INTO accounts VALUES (?, ?)')
      .run(key, JSON.stringify(createPalette()));
    return this.get(token);
  }
  registerDemo(token) {
    if(this.mockLocationAccess!==true)throw new Error('SIMULATION_DISABLED');
    const key=accountKey(token);
    if(this.db.prepare('SELECT 1 FROM participants WHERE token_hash=?').get(key))throw new Error('INVALID_DEMO_ACCOUNT');
    const existing=this.db.prepare('SELECT 1 FROM accounts WHERE token_hash=?').get(key);
    if(existing&&!this.db.prepare('SELECT 1 FROM demo_accounts WHERE owner_hash=?').get(key))throw new Error('INVALID_DEMO_ACCOUNT');
    this.register(token);this.db.prepare('INSERT OR IGNORE INTO demo_accounts VALUES (?)').run(key);return {demo:true};
  }
  developmentStickers(token) {
    const owner=this.participantOwner(token);
    return this.developmentStickerAccess===true&&!this.db.prepare('SELECT 1 FROM demo_accounts WHERE owner_hash=?').get(owner);
  }
  get(token) {
    return this.getByKey(accountKey(token));
  }
  getByKey(key) {
    const row = this.db.prepare('SELECT palette FROM accounts WHERE token_hash = ?').get(key);
    if (!row) throw new Error('UNAUTHORIZED');
    return { colors: JSON.parse(row.palette).colors };
  }
  createSession(token) {
    this.get(token);
    const owner = accountKey(token), id = randomUUID(), code = randomBytes(12).toString('hex');
    const now = this.now(), joinExpiresAt = now + 5 * 60_000, expiresAt = now + 8 * 60 * 60_000;
    this.db.exec('BEGIN IMMEDIATE');
    try {
      this.db.prepare('UPDATE sessions SET revoked = 1 WHERE owner_hash = ?').run(owner);
      this.db.prepare('INSERT INTO sessions (id, owner_hash, code_hash, join_expires, expires) VALUES (?, ?, ?, ?, ?)')
        .run(id, owner, createHash('sha256').update(code).digest('hex'), joinExpiresAt, expiresAt);
      this.db.exec('COMMIT');
    } catch (error) { this.db.exec('ROLLBACK'); throw error; }
    return { id, code, joinExpiresAt, expiresAt };
  }
  joinSession(token, code) {
    const key = accountKey(token);
    if (typeof code !== 'string' || !/^[a-f0-9]{24}$/.test(code)) throw new Error('INVALID_SESSION');
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const hash=createHash('sha256').update(code).digest('hex');
      let session = this.db.prepare('SELECT * FROM sessions WHERE code_hash = ?').get(hash);
      if(!session){const invite=this.db.prepare('SELECT * FROM invitations WHERE code_hash = ?').get(hash);if(invite){session=this.db.prepare('SELECT * FROM sessions WHERE id = ?').get(invite.session_id);if(session)session.join_expires=invite.expires;}}
      if (!session || session.revoked || session.expires <= this.now()) throw new Error('INVALID_SESSION');
      const existing = this.db.prepare('SELECT session_id FROM participants WHERE token_hash = ?').get(key);
      if (existing && existing.session_id !== session.id) throw new Error('PARTICIPANT_IN_USE');
      // A successful participant can retry its join after the invitation expires.
      if (!existing && session.join_expires <= this.now()) throw new Error('INVALID_SESSION');
      this.db.prepare('INSERT OR IGNORE INTO participants VALUES (?, ?)').run(key, session.id);
      this.db.exec('COMMIT');
      return { id: session.id, expiresAt: session.expires };
    } catch (error) { this.db.exec('ROLLBACK'); throw error; }
  }
  participantOwner(token) {
    const session = this.db.prepare(`SELECT s.* FROM sessions s JOIN participants p ON s.id = p.session_id
      WHERE p.token_hash = ?`).get(accountKey(token));
    if (!session || session.revoked || session.expires <= this.now()) throw new Error('UNAUTHORIZED');
    return session.owner_hash;
  }
  participantSession(token) {
    this.participantOwner(token);
    return this.db.prepare('SELECT s.* FROM sessions s JOIN participants p ON s.id=p.session_id WHERE p.token_hash=?').get(accountKey(token));
  }
  invite(token) {
    const session=this.participantSession(token),code=randomBytes(12).toString('hex');
    const joinExpiresAt=Math.min(this.now()+300000,session.expires);
    this.db.prepare('DELETE FROM invitations WHERE issuer=?').run(accountKey(token));
    this.db.prepare('INSERT INTO invitations VALUES (?,?,?,?)').run(createHash('sha256').update(code).digest('hex'),session.id,accountKey(token),joinExpiresAt);
    return {code,joinExpiresAt,expiresAt:session.expires,serverNow:this.now()};
  }
  sessionPalette(token) { return this.getByKey(this.participantOwner(token)); }
  artwork(token) {
    const row = this.db.prepare('SELECT regions, revision FROM artworks WHERE owner_hash = ?').get(this.participantOwner(token));
    if(!row)return {strokes:[],legacyRegions:{},revision:0};
    const data=JSON.parse(row.regions);
    return Object.hasOwn(data,'strokes') ? {...data,revision:row.revision} : {strokes:[],legacyRegions:data,revision:row.revision};
  }
  saveArtwork(token, body) {
    if (!body || !Number.isSafeInteger(body.revision) || body.revision < 0 ||
        !Array.isArray(body.strokes) || body.strokes.length>1000 ||
        !body.legacyRegions || typeof body.legacyRegions!=='object' || Array.isArray(body.legacyRegions)) {
      throw new Error('INVALID_ARTWORK');
    }
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const owner = this.participantOwner(token);
      const palette = this.getByKey(owner);
      const current = this.artwork(token);
      if (current.revision !== body.revision) throw new Error('ARTWORK_CONFLICT');
      const legacyKeys=Object.keys(current.legacyRegions);
      if(Object.keys(body.legacyRegions).length!==legacyKeys.length ||
          legacyKeys.some(key=>body.legacyRegions[key]!==current.legacyRegions[key]))throw new Error('INVALID_ARTWORK');
      let totalPoints=0;
      const strokes=body.strokes.map(stroke=>{
        if(!stroke||!['paint','erase'].includes(stroke.tool)||!Number.isFinite(stroke.width)||stroke.width<1||stroke.width>60||
            !Array.isArray(stroke.points)||stroke.points.length<1||stroke.points.length>2000||
            !palette.colors.some(color=>color.hex===stroke.color)||
            stroke.points.some(p=>!Array.isArray(p)||p.length!==2||!p.every(Number.isFinite)||p[0]<0||p[0]>600||p[1]<0||p[1]>400))throw new Error('INVALID_ARTWORK');
        totalPoints+=stroke.points.length;
        return {tool:stroke.tool,color:stroke.color,width:stroke.width,points:stroke.points};
      });
      if(totalPoints>20000)throw new Error('INVALID_ARTWORK');
      const data={strokes,legacyRegions:current.legacyRegions};
      const revision = current.revision + 1;
      this.db.prepare(`INSERT INTO artworks VALUES (?, ?, ?) ON CONFLICT(owner_hash)
        DO UPDATE SET regions = excluded.regions, revision = excluded.revision`)
        .run(owner, JSON.stringify(data), revision);
      this.db.exec('COMMIT');
      return { ...data, revision };
    } catch (error) { this.db.exec('ROLLBACK'); throw error; }
  }
  revokeSession(token, id) {
    this.get(token);
    const result = this.db.prepare('UPDATE sessions SET revoked = 1 WHERE id = ? AND owner_hash = ?')
      .run(id, accountKey(token));
    if (!result.changes) throw new Error('SESSION_NOT_FOUND');
    return { revoked: true };
  }
  close() { this.db.close(); }
}
