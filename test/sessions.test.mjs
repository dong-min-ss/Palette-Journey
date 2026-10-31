import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdtempSync, rmSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { AccountStore } from '../backend/store.mjs';
import { createApp } from '../backend/server.mjs';
const credential = () => randomBytes(32).toString('hex');

test('invitation expiry, participant retry, session expiry and shared ticket', () => {
  let now = 1_000_000;
  const store = new AccountStore(':memory:', () => now);
  const owner = credential(), member = credential();
  try {
    store.register(owner);
    const session = store.createSession(owner);
    assert.equal(store.joinSession(member, session.code).id, session.id);
    store.unlock(member, '#123ABC', true);
    assert.equal(store.get(owner).tickets, 0);
    now = session.joinExpiresAt;
    assert.throws(() => store.joinSession(credential(), session.code), /INVALID_SESSION/);
    assert.equal(store.joinSession(member, session.code).id, session.id);
    assert.equal(store.sessionPalette(member).colors.length, 12);
    assert.throws(() => store.createSession(member), /UNAUTHORIZED/);
    assert.throws(() => store.unlock(member, '#ABCDEF', true), /NO_TICKET/);
    now = session.expiresAt;
    assert.throws(() => store.sessionPalette(member), /UNAUTHORIZED/);
    assert.throws(() => store.joinSession(member, session.code), /INVALID_SESSION/);
    assert.equal(store.register(owner).tickets, 0);
  } finally { store.close(); }
});

test('only owner can revoke; replacement session invalidates old participants', () => {
  const store = new AccountStore(':memory:');
  const owner = credential(), other = credential(), member = credential();
  try {
    store.register(owner); store.register(other);
    const a = store.createSession(owner);
    store.joinSession(member, a.code);
    assert.throws(() => store.revokeSession(other, a.id), /SESSION_NOT_FOUND/);
    const b = store.createSession(owner);
    assert.throws(() => store.sessionPalette(member), /UNAUTHORIZED/);
    assert.throws(() => store.joinSession(credential(), a.code), /INVALID_SESSION/);
    assert.throws(() => store.joinSession(member, b.code), /PARTICIPANT_IN_USE/);
    const fresh = credential(); store.joinSession(fresh, b.code);
    store.revokeSession(owner, b.id);
    assert.throws(() => store.unlock(fresh, '#123ABC', true), /UNAUTHORIZED/);
    assert.equal(store.get(owner).tickets, 1);
  } finally { store.close(); }
});

test('reopened database retains participants, expiry and revocation without raw credentials', () => {
  const dir = mkdtempSync(join(tmpdir(), 'palette-session-'));
  const path = join(dir, 'session.sqlite');
  const owner = credential(), member = credential();
  let store = new AccountStore(path, () => 1000);
  try {
    store.register(owner);
    const session = store.createSession(owner);
    store.joinSession(member, session.code);
    store.close(); store = new AccountStore(path, () => 2000);
    assert.equal(store.sessionPalette(member).tickets, 1);
    store.revokeSession(owner, session.id);
    store.close(); store = new AccountStore(path, () => 3000);
    assert.throws(() => store.sessionPalette(member), /UNAUTHORIZED/);
    store.close(); store = null;
    const bytes = readFileSync(path);
    for (const secret of [owner, member, session.code]) assert.equal(bytes.includes(Buffer.from(secret)), false);
  } finally { store?.close(); rmSync(dir, { recursive: true }); }
});

test('HTTP participants see owner palette without account creation and lose access on revoke', async () => {
  const store = new AccountStore(':memory:');
  const server = createApp(store);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const owner = credential(), member = credential(); store.register(owner);
  const request = (path, token, method = 'GET', body) => fetch(base + path, {
    method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  try {
    const session = await (await request('/api/sessions', owner, 'POST')).json();
    assert.equal((await request('/api/sessions/join', member, 'POST', { code: 'wrong' })).status, 400);
    assert.equal((await request('/api/sessions/join', member, 'POST', { code: session.code })).status, 200);
    assert.equal((await request('/api/palette', member)).status, 401);
    assert.equal((await request('/api/sessions', member, 'POST')).status, 401);
    assert.equal((await request('/api/session/palette', credential())).status, 401);
    const palette = await (await request('/api/session/palette', member)).json();
    assert.equal(palette.tickets, 1); assert.equal(palette.colors.length, 11);
    assert.equal((await request('/api/session/palette/personal', member, 'POST', { hex: '#123ABC' })).status, 200);
    assert.equal(store.get(owner).tickets, 0);
    assert.equal((await request(`/api/sessions/${session.id}`, owner, 'DELETE')).status, 200);
    assert.equal((await request('/api/session/palette', member)).status, 401);
  } finally { await new Promise(resolve => server.close(resolve)); store.close(); }
});
