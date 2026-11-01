import { describe, expect, it } from 'vitest';
import { lastIndexAtOrBefore, mergeBars, toBar } from './bars';

const bar = (time: number, close = 1) => ({ time, open: 1, high: 2, low: 1, close, volume: 1 });

describe('toBar', () => {
  it('converts a bar message to epoch seconds', () => {
    expect(
      toBar({ type: 'BAR', symbol: 'ES', time: '2026-10-12T14:12:40Z', open: 1, high: 3, low: 0.5, close: 2, volume: 7 }),
    ).toEqual({ time: 1_791_814_360, open: 1, high: 3, low: 0.5, close: 2, volume: 7 });
  });

  it('ignores errors and incomplete bars', () => {
    expect(toBar({ type: 'ERROR', message: 'Invalid symbol: es' })).toBeNull();
    expect(toBar({ type: 'BAR', time: '2026-10-12T14:12:40Z', open: 1 })).toBeNull();
  });
});

describe('mergeBars', () => {
  it('orders by time and keeps the first bar of a duplicated time', () => {
    const merged = mergeBars([bar(3, 30), bar(1)], [bar(3, 99), bar(2)]);

    expect(merged.map((b) => b.time)).toEqual([1, 2, 3]);
    expect(merged[2].close).toBe(30);
  });
});

describe('lastIndexAtOrBefore', () => {
  const times = [10, 20, 30];

  it('finds the bar at or before a time', () => {
    expect(lastIndexAtOrBefore(times, 20)).toBe(1);
    expect(lastIndexAtOrBefore(times, 25)).toBe(1);
    expect(lastIndexAtOrBefore(times, 99)).toBe(2);
  });

  it('returns -1 before the first bar', () => {
    expect(lastIndexAtOrBefore(times, 5)).toBe(-1);
    expect(lastIndexAtOrBefore([], 5)).toBe(-1);
  });
});
