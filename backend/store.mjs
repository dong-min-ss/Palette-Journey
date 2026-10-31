import { DatabaseSync } from 'node:sqlite';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { createPalette, unlockPersonalColor } from '../core/palette.mjs';

export function accountKey(token) {
  if (typeof token !== 'string' || !/^[a-f0-9]{64}$/.test(token)) throw new Error('UNAUTHORIZED');
  return createHash('sha256').update(token).digest('hex');
}

export class AccountStore {
  constructor(path, now = Date.now) {
    this.now = now;
    this.db = new DatabaseSync(path);
    this.db.exec(`PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;
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
  get(token) {
    return this.getByKey(accountKey(token));
  }
  getByKey(key) {
    const row = this.db.prepare('SELECT palette FROM accounts WHERE token_hash = ?').get(key);
    if (!row) throw new Error('UNAUTHORIZED');
    return JSON.parse(row.palette);
  }
  unlock(token, hex, participant = false) {
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const key = participant ? this.participantOwner(token) : accountKey(token);
      const updated = unlockPersonalColor(this.getByKey(key), hex);
      this.db.prepare('UPDATE accounts SET palette = ? WHERE token_hash = ?')
        .run(JSON.stringify(updated), key);
      this.db.exec('COMMIT');
      return updated;
    } catch (error) {
      this.db.exec('ROLLBACK');
      throw error;
    }
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
      const session = this.db.prepare('SELECT * FROM sessions WHERE code_hash = ?')
        .get(createHash('sha256').update(code).digest('hex'));
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
  sessionPalette(token) { return this.getByKey(this.participantOwner(token)); }
  artwork(token) {
    const row = this.db.prepare('SELECT regions, revision FROM artworks WHERE owner_hash = ?').get(this.participantOwner(token));
    return row ? { regions: JSON.parse(row.regions), revision: row.revision } : { regions: {}, revision: 0 };
  }
  saveArtwork(token, body) {
    if (!body || !Number.isSafeInteger(body.revision) || body.revision < 0 ||
        !body.regions || typeof body.regions !== 'object' || Array.isArray(body.regions) ||
        Object.keys(body.regions).some(id => !['sky', 'sun', 'mountain', 'field', 'road'].includes(id))) {
      throw new Error('INVALID_ARTWORK');
    }
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const owner = this.participantOwner(token);
      const palette = this.getByKey(owner);
      if (Object.values(body.regions).some(hex => typeof hex !== 'string' ||
          !palette.colors.some(color => color.hex === hex))) throw new Error('INVALID_ARTWORK');
      const current = this.artwork(token);
      if (current.revision !== body.revision) throw new Error('ARTWORK_CONFLICT');
      const revision = current.revision + 1;
      this.db.prepare(`INSERT INTO artworks VALUES (?, ?, ?) ON CONFLICT(owner_hash)
        DO UPDATE SET regions = excluded.regions, revision = excluded.revision`)
        .run(owner, JSON.stringify(body.regions), revision);
      this.db.exec('COMMIT');
      return { regions: body.regions, revision };
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
