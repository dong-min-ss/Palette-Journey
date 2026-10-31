import { STICKERS } from '../core/catalog.mjs';
export function initializeStickers(accounts) {
  accounts.db.exec(`CREATE TABLE IF NOT EXISTS sticker_ownership (
    owner_hash TEXT NOT NULL, sticker_id TEXT NOT NULL, visit_id TEXT NOT NULL,
    PRIMARY KEY(owner_hash,sticker_id));`);
}
export function ownedStickers(accounts, token) {
  const owner=accounts.participantOwner(token);
  const regional=accounts.db.prepare('SELECT sticker_id FROM sticker_ownership WHERE owner_hash=?').all(owner);
  return STICKERS.filter(s=>s.kind==='basic'||regional.some(row=>row.sticker_id===s.id)).map(s=>({stickerId:s.id}));
}
