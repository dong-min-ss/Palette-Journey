import { DatabaseSync } from 'node:sqlite';
import { createHash } from 'node:crypto';
import { createPalette, unlockPersonalColor } from '../core/palette.mjs';

export function accountKey(token) {
  if (typeof token !== 'string' || !/^[a-f0-9]{64}$/.test(token)) throw new Error('UNAUTHORIZED');
  return createHash('sha256').update(token).digest('hex');
}

export class AccountStore {
  constructor(path) {
    this.db = new DatabaseSync(path);
    this.db.exec(`PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;
      CREATE TABLE IF NOT EXISTS accounts (
        token_hash TEXT PRIMARY KEY, palette TEXT NOT NULL
      );`);
  }
  register(token) {
    const key = accountKey(token);
    this.db.prepare('INSERT OR IGNORE INTO accounts VALUES (?, ?)')
      .run(key, JSON.stringify(createPalette()));
    return this.get(token);
  }
  get(token) {
    const row = this.db.prepare('SELECT palette FROM accounts WHERE token_hash = ?').get(accountKey(token));
    if (!row) throw new Error('UNAUTHORIZED');
    return JSON.parse(row.palette);
  }
  unlock(token, hex) {
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const updated = unlockPersonalColor(this.get(token), hex);
      this.db.prepare('UPDATE accounts SET palette = ? WHERE token_hash = ?')
        .run(JSON.stringify(updated), accountKey(token));
      this.db.exec('COMMIT');
      return updated;
    } catch (error) {
      this.db.exec('ROLLBACK');
      throw error;
    }
  }
  close() { this.db.close(); }
}
