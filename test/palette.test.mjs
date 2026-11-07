import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createPalette, collectRegionColor } from '../core/palette.mjs';

test('first account receives eleven unique colors', () => {
  const state = createPalette();
  assert.equal(new Set(state.colors.map(c => c.id)).size, 11);
  state.colors.pop();
  assert.equal(createPalette().colors.length, 11);
});
test('region entry retains provenance and cannot award twice', () => {
  const region = { id: 'demo-sea', name: '시연 바다', hex: '#0077AA' };
  const saved = collectRegionColor(createPalette(), region, '2026-10-30T00:00:00Z');
  assert.equal(saved.colors.at(-1).source, 'region');
  assert.equal(saved.colors.at(-1).acquiredAt, '2026-10-30T00:00:00Z');
  assert.equal(collectRegionColor(saved, region, '2026-10-31T00:00:00Z'), saved);
});
