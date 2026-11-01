import { describe, expect, it } from 'vitest';
import { logicalRangeFor, RIGHT_OFFSET_BARS } from './view';

describe('logicalRangeFor', () => {
  it('shows the last span of bars, plus the right offset', () => {
    expect(logicalRangeFor('5m', 1000)).toEqual({ from: 700, to: 999 + RIGHT_OFFSET_BARS });
    expect(logicalRangeFor('1m', 60)).toEqual({ from: 0, to: 59 + RIGHT_OFFSET_BARS });
  });

  it('keeps the span when there are fewer bars than it covers', () => {
    const range = logicalRangeFor('2h', 100)!;

    expect(range.to - range.from).toBe(7199 + RIGHT_OFFSET_BARS);
  });

  it('fits everything for All', () => {
    expect(logicalRangeFor('all', 1000)).toBeNull();
  });
});
