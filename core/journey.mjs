// Domain helpers only: callers must authenticate and verify visits on the server.
// Acquisition thresholds belong to a future policy; none are invented here.
const id = value => typeof value === 'string' && /^[a-zA-Z0-9_-]{1,100}$/.test(value);
const timestamp = value => typeof value === 'string' && Number.isFinite(Date.parse(value));

export function awardRegionSticker(owned, sticker, verifiedVisit) {
  if (!Array.isArray(owned) || !id(sticker?.id) || !id(sticker?.regionId) ||
      !id(verifiedVisit?.id) || sticker.regionId !== verifiedVisit.regionId ||
      !timestamp(verifiedVisit.visitedAt) || verifiedVisit.verified !== true) {
    throw new Error('INVALID_STICKER_AWARD');
  }
  if (owned.some(item => item.stickerId === sticker.id)) return owned;
  return [...owned, { stickerId: sticker.id, regionId: sticker.regionId,
    visitId: verifiedVisit.id, acquiredAt: verifiedVisit.visitedAt }];
}

// Width and height are independent, so children can change aspect ratio.
// A placement references a reusable owned asset; placing it never consumes it.
export function validateStickerPlacements(placements, owned) {
  if (!Array.isArray(placements) || placements.length > 100 || !Array.isArray(owned)) {
    throw new Error('INVALID_STICKER_PLACEMENT');
  }
  const ids = new Set();
  return placements.map(item => {
    if (!id(item?.id) || ids.has(item.id) || !owned.some(s => s.stickerId === item.stickerId) ||
        ![item.x, item.y, item.width, item.height].every(Number.isFinite) ||
        item.x < 0 || item.y < 0 || item.width < 1 || item.height < 1 ||
        item.x + item.width > 600 || item.y + item.height > 400) {
      throw new Error('INVALID_STICKER_PLACEMENT');
    }
    ids.add(item.id);
    return { id: item.id, stickerId: item.stickerId, x: item.x, y: item.y,
      width: item.width, height: item.height };
  });
}

// Run on a verified region-entry event, not every location sample.
// Delivery receipts must be persisted by the caller after successful delivery.
export function selectRevisitMemory({ diaries, visit, familyId, childId,
  enabled, delivered = [] }) {
  if (!id(familyId) || !id(childId) || !id(visit?.id) || !id(visit?.tripId) ||
      !id(visit?.regionId) || !timestamp(visit?.enteredAt) ||
      !Array.isArray(diaries) || !Array.isArray(delivered)) throw new Error('INVALID_REVISIT');
  if (enabled !== true || visit.verified !== true) return null;
  if (delivered.some(r => r.visitId === visit.id && r.familyId === familyId && r.childId === childId)) return null;
  const currentDay = seoulDate(visit.enteredAt);
  const matching = diaries.filter(d => id(d?.id) && id(d.tripId) && d.familyId === familyId &&
    d.childId === childId && !d.deletedAt && d.hasContent === true &&
    d.regionId === visit.regionId && d.tripId !== visit.tripId &&
    timestamp(d.createdAt) && Date.parse(d.createdAt) < Date.parse(visit.enteredAt) &&
    seoulDate(d.createdAt).key !== currentDay.key);
  matching.sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt) || a.id.localeCompare(b.id));
  if (!matching.length) return null;
  const diary = matching[0], date = seoulDate(diary.createdAt);
  return { diaryId: diary.id, visitId: visit.id, familyId, childId,
    message: `${date.year}년 ${date.month}월 ${date.day}일에 이곳을 방문하고 일기를 썼어요. 한번 읽어보시겠어요?` };
}

function seoulDate(value) {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: 'Asia/Seoul',
    year: 'numeric', month: 'numeric', day: 'numeric' }).formatToParts(new Date(value));
  const date = Object.fromEntries(parts.map(p => [p.type, p.value]));
  return { ...date, key: `${date.year}-${date.month}-${date.day}` };
}
