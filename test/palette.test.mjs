import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createPalette, unlockPersonalColor, collectRegionColor } from '../core/palette.mjs';

test('first account receives eleven unique colors and one ticket', () => {
  const state = createPalette();
  assert.equal(new Set(state.colors.map(c => c.id)).size, 11);
  assert.equal(state.tickets, 1);
  state.colors.pop();
  assert.equal(createPalette().colors.length, 11);
});
test('unlock consumes exactly one ticket, retry is idempotent, overspend fails', () => {
  const initial = createPalette();
  const saved = unlockPersonalColor(initial, '#123abc');
  assert.equal(saved.tickets, 0);
  assert.equal(saved.colors.length, 12);
  assert.equal(initial.tickets, 1);
  assert.equal(unlockPersonalColor(saved, '#123ABC'), saved);
  assert.throws(() => unlockPersonalColor(saved, '#ABCDEF'), /NO_TICKET/);
  assert.throws(() => unlockPersonalColor(initial, 'invalid'), /INVALID_COLOR/);
});
test('region entry retains provenance, costs no ticket and cannot award twice', () => {
  const region = { id: 'demo-sea', name: '시연 바다', hex: '#0077AA' };
  const saved = collectRegionColor(createPalette(), region, '2026-10-30T00:00:00Z');
  assert.equal(saved.tickets, 1);
  assert.equal(saved.colors.at(-1).source, 'region');
  assert.equal(saved.colors.at(-1).acquiredAt, '2026-10-30T00:00:00Z');
  assert.equal(collectRegionColor(saved, region, '2026-10-31T00:00:00Z'), saved);
});
