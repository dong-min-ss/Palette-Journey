export const BASE_COLORS = Object.freeze([
  ['red', '#F44336'], ['orange', '#FF9800'], ['yellow', '#FFEB3B'],
  ['green', '#4CAF50'], ['blue', '#2196F3'], ['purple', '#9C27B0'],
  ['pink', '#E91E63'], ['brown', '#795548'], ['white', '#FFFFFF'],
  ['black', '#000000'], ['gray', '#9E9E9E'],
].map(([id, hex]) => Object.freeze({ id, hex, source: 'base' })));

// Account creation only: reconnecting a session must load the existing state.
export function createPalette() {
  return { tickets: 1, colors: BASE_COLORS.map(color => ({ ...color })) };
}

export function unlockPersonalColor(state, hex) {
  if (!/^#[0-9a-f]{6}$/i.test(hex)) throw new Error('INVALID_COLOR');
  hex = hex.toUpperCase();
  if (state.colors.some(color => color.hex === hex)) return state;
  if (state.tickets < 1) throw new Error('NO_TICKET');
  return { tickets: state.tickets - 1, colors: [...state.colors,
    { id: `personal-${hex.slice(1)}`, hex, source: 'personal' }] };
}

export function collectRegionColor(state, { id, hex, name }, acquiredAt) {
  if (!id || !name || !/^#[0-9a-f]{6}$/i.test(hex) || !Number.isFinite(Date.parse(acquiredAt))) {
    throw new Error('INVALID_REGION');
  }
  if (state.colors.some(color => color.id === `region-${id}`)) return state;
  return { ...state, colors: [...state.colors,
    { id: `region-${id}`, hex: hex.toUpperCase(), name, source: 'region', acquiredAt }] };
}
