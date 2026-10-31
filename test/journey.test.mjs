import test from 'node:test';
import assert from 'node:assert/strict';
import { awardRegionSticker, validateStickerPlacements, selectRevisitMemory } from '../core/journey.mjs';

test('regional awards require a verified matching visit and support reusable placements', () => {
  const asset = { id: 'food', regionId: 'yeosu' };
  const visit = { id: 'visit-1', regionId: 'yeosu', visitedAt: '2026-10-30T04:00:00Z', verified: true };
  const owned = awardRegionSticker([], asset, visit);
  assert.strictEqual(awardRegionSticker(owned, asset, visit), owned);
  assert.throws(() => awardRegionSticker([], asset, { ...visit, verified: false }));
  assert.throws(() => awardRegionSticker([], asset, { ...visit, regionId: 'busan' }));
  const p = { id: 'p1', stickerId: 'food', x: 20, y: 30, width: 120, height: 40 };
  assert.deepEqual(validateStickerPlacements([p, { ...p, id: 'p2' }], owned), [p, { ...p, id: 'p2' }]);
  assert.equal(owned.length, 1);
  for (const bad of [{ width: Infinity }, { height: 0 }, { x: 590 }, { stickerId: 'unowned' }]) {
    assert.throws(() => validateStickerPlacements([{ ...p, ...bad }], owned));
  }
  assert.throws(() => validateStickerPlacements([p, p], owned));
});

test('revisit memory respects child ownership, prior trips, Seoul dates and delivery receipts', () => {
  const diary = { id: 'diary-1', familyId: 'family-1', childId: 'child-1', tripId: 'trip-1',
    regionId: 'yeosu', createdAt: '2026-10-30T04:00:00Z', hasContent: true };
  const request = { familyId: 'family-1', childId: 'child-1', enabled: true,
    visit: { id: 'visit-2', tripId: 'trip-2', regionId: 'yeosu', enteredAt: '2026-11-30T04:00:00Z', verified: true },
    diaries: [diary] };
  const result = selectRevisitMemory(request);
  assert.equal(result.diaryId, diary.id);
  assert.match(result.message, /2026년 10월 30일/);
  assert.equal(selectRevisitMemory({ ...request, delivered: [result] }), null);
  assert.equal(selectRevisitMemory({ ...request, enabled: false }), null);
  assert.equal(selectRevisitMemory({ ...request, visit: { ...request.visit, verified: false } }), null);
  for (const patch of [{ familyId: 'other' }, { childId: 'other' }, { deletedAt: '2026-11-01' },
    { hasContent: false }, { regionId: 'busan' }, { tripId: 'trip-2' },
    { createdAt: '2026-12-01T00:00:00Z' }, { createdAt: '2026-11-29T15:00:00Z' }]) {
    assert.equal(selectRevisitMemory({ ...request, diaries: [{ ...diary, ...patch }] }), null);
  }
  assert.equal(selectRevisitMemory({ ...request, diaries: [diary, { ...diary, id: 'newer', createdAt: '2026-11-01T00:00:00Z' }] }).diaryId, 'newer');
});
