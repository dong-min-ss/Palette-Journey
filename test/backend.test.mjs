import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { AccountStore, accountKey } from '../backend/store.mjs';
import { createApp } from '../backend/server.mjs';

test('old palettes retain saved colors without returning a ticket balance', () => {
  const dir = mkdtempSync(join(tmpdir(), 'palette-test-'));
  const path = join(dir, 'test.sqlite');
  const token = randomBytes(32).toString('hex');
  let store = new AccountStore(path);
  try {
    const { colors } = store.register(token);
    colors.push({ id: 'personal-123ABC', hex: '#123ABC', source: 'personal' });
    store.db.prepare('UPDATE accounts SET palette = ? WHERE token_hash = ?')
      .run(JSON.stringify({ tickets: 0, colors }), accountKey(token));
    store.close();
    store = new AccountStore(path);
    assert.deepEqual(store.register(token), { colors });
  } finally { store.close(); rmSync(dir, { recursive: true }); }
});

test('HTTP account authentication and retired personal color endpoint', async () => {
  const store = new AccountStore(':memory:');
  const server = createApp(store);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const token = randomBytes(32).toString('hex');
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
  try {
    assert.equal((await fetch(base + '/health')).status, 200);
    assert.equal((await fetch(base + '/api/palette')).status, 401);
    assert.equal((await fetch(base + '/api/palette', { headers })).status, 401);
    assert.equal((await fetch(base + '/api/account', { method: 'PUT', headers })).status, 200);
    assert.equal((await fetch(base + '/api/palette/personal', {
      method: 'POST', headers, body: JSON.stringify({ hex: '#123ABC' }),
    })).status, 404);
    const palette = await (await fetch(base + '/api/palette', { headers })).json();
    assert.equal(palette.colors.length, 11);
    assert.deepEqual(Object.keys(palette), ['colors']);
  } finally { await new Promise(resolve => server.close(resolve)); store.close(); }
});
