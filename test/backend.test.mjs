import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { AccountStore } from '../backend/store.mjs';
import { createApp } from '../backend/server.mjs';

test('registration retries and restart preserve spent ticket and personal color', () => {
  const dir = mkdtempSync(join(tmpdir(), 'palette-test-'));
  const path = join(dir, 'test.sqlite');
  const token = randomBytes(32).toString('hex');
  let store = new AccountStore(path);
  try {
    assert.equal(store.register(token).tickets, 1);
    store.unlock(token, '#123ABC');
    assert.equal(store.register(token).tickets, 0);
    store.close();
    store = new AccountStore(path);
    assert.equal(store.register(token).tickets, 0);
    assert.equal(store.get(token).colors.length, 12);
    assert.equal(store.unlock(token, '#123abc').tickets, 0);
    assert.throws(() => store.unlock(token, '#ABCDEF'), /NO_TICKET/);
    assert.equal(store.get(token).colors.length, 12);
  } finally { store.close(); rmSync(dir, { recursive: true }); }
});

test('two database connections cannot spend the same ticket twice', () => {
  const dir = mkdtempSync(join(tmpdir(), 'palette-test-'));
  const path = join(dir, 'test.sqlite');
  const a = new AccountStore(path), b = new AccountStore(path);
  const token = randomBytes(32).toString('hex');
  try {
    a.register(token); a.unlock(token, '#123ABC');
    assert.throws(() => b.unlock(token, '#ABCDEF'), /NO_TICKET/);
    assert.equal(b.get(token).tickets, 0);
  } finally { a.close(); b.close(); rmSync(dir, { recursive: true }); }
});

test('HTTP authentication, isolated accounts, validation and parallel spending', async () => {
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
    const bad = await fetch(base + '/api/palette/personal', { method: 'POST', headers, body: 'null' });
    assert.equal(bad.status, 400);
    const responses = await Promise.all(['#123ABC', '#ABCDEF'].map(hex => fetch(base + '/api/palette/personal', {
      method: 'POST', headers, body: JSON.stringify({ hex }),
    })));
    assert.deepEqual(responses.map(r => r.status).sort(), [200, 409]);
    const palette = await (await fetch(base + '/api/palette', { headers })).json();
    assert.equal(palette.tickets, 0);
    assert.equal(palette.colors.length, 12);
    const other = randomBytes(32).toString('hex');
    assert.equal(store.register(other).colors.length, 11);
    assert.equal(store.register(other).tickets, 1);
    assert.equal((await fetch(base + '/api/account', { method: 'PUT', headers })).status, 200);
    assert.equal(store.get(token).tickets, 0);
  } finally { await new Promise(resolve => server.close(resolve)); store.close(); }
});
