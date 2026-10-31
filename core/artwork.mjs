export function validateStrokes(strokes, palette) {
  if (!Array.isArray(strokes) || strokes.length > 1000) throw new Error('INVALID_ARTWORK');
  let total = 0;
  const clean = strokes.map(stroke => {
    if (!stroke || !['paint', 'erase'].includes(stroke.tool) || !Number.isFinite(stroke.width) ||
        stroke.width < 1 || stroke.width > 60 || !Array.isArray(stroke.points) ||
        stroke.points.length < 1 || stroke.points.length > 2000 ||
        !palette.colors.some(color => color.hex === stroke.color) ||
        stroke.points.some(p => !Array.isArray(p) || p.length !== 2 || !p.every(Number.isFinite) ||
          p[0] < 0 || p[0] > 600 || p[1] < 0 || p[1] > 400)) throw new Error('INVALID_ARTWORK');
    total += stroke.points.length;
    return { tool: stroke.tool, color: stroke.color, width: stroke.width, points: stroke.points };
  });
  if (total > 20000) throw new Error('INVALID_ARTWORK');
  return clean;
}
