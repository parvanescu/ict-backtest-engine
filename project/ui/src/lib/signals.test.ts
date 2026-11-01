import { describe, expect, it } from 'vitest';
import type { Signal } from '../api/types';
import {
  canonicalRuleKey,
  dayStatusAfter,
  latestResultFor,
  markersFor,
  mergeSignals,
  rangeLevelsFor,
  reportsFor,
} from './signals';

const KEY = 'SEQUENCE_RANGE_SWEEP[ES,NQ,RTY,YM]';

const report: Signal = {
  id: 1,
  ruleKey: KEY,
  kind: 'REPORT',
  code: 'EXTREMITY_PASSED',
  scope: ['YM'],
  eventTime: '2026-10-12T14:12:40Z',
  evidence: { side: 'LOW', price: 44168, level: 44180 },
};
const bullishEs: Signal = {
  id: 2,
  ruleKey: KEY,
  kind: 'RESULT',
  scope: ['ES'],
  verdict: 'BULLISH',
  eventTime: '2026-10-12T14:12:40Z',
  evidence: { target: { H: 6032, L: 6026, M: 6029, low: 6027 } },
};

describe('canonicalRuleKey', () => {
  it('sorts and dedupes symbols like the service', () => {
    expect(canonicalRuleKey('SEQUENCE_RANGE_SWEEP', ['YM', 'ES', 'NQ', 'RTY', 'ES'])).toBe(KEY);
  });
});

describe('mergeSignals', () => {
  it('orders by id, drops duplicates and errors', () => {
    const error: Signal = { ruleKey: KEY, kind: 'ERROR', message: 'boom' };

    expect(mergeSignals([bullishEs, error], [report, bullishEs]).map((s) => s.id)).toEqual([1, 2]);
  });
});

describe('markersFor', () => {
  it('marks the results about the charted symbol only, not reports', () => {
    expect(markersFor([report, bullishEs], 'ES')).toEqual([
      expect.objectContaining({ time: 1_791_814_360, shape: 'arrowUp', position: 'belowBar', text: 'BULLISH' }),
    ]);
    expect(markersFor([report, bullishEs], 'YM')).toEqual([]);
  });

  it('marks bearish results above the lines', () => {
    const bearish: Signal = { ...bullishEs, id: 3, verdict: 'BEARISH' };

    expect(markersFor([bearish], 'ES')).toEqual([
      expect.objectContaining({ shape: 'arrowDown', position: 'aboveBar', text: 'BEARISH' }),
    ]);
  });
});

describe('latestResultFor', () => {
  it('finds the newest result about the symbol', () => {
    const later: Signal = { ...bullishEs, id: 9, verdict: 'BEARISH' };

    expect(latestResultFor([bullishEs, report, later], 'ES')).toBe(later);
    expect(latestResultFor([bullishEs, report], 'YM')).toBeNull();
  });
});

describe('reportsFor', () => {
  it('lists the reports about the symbol, newest first, up to the limit', () => {
    const reports = [1, 2, 3].map((id): Signal => ({ ...report, id }));

    expect(reportsFor([...reports, bullishEs], 'YM', 2).map((s) => s.id)).toEqual([3, 2]);
    expect(reportsFor(reports, 'ES', 10)).toEqual([]);
  });
});

describe('rangeLevelsFor', () => {
  it('reads the 3-3-3 range from the latest result about the symbol', () => {
    expect(rangeLevelsFor([report, bullishEs], 'ES')).toEqual({ high: 6032, low: 6026, mean: 6029 });
    expect(rangeLevelsFor([report, bullishEs], 'YM')).toBeNull();
  });
});

describe('dayStatusAfter', () => {
  it('follows statuses and results', () => {
    expect(dayStatusAfter('UNKNOWN', report)).toBe('UNKNOWN');
    expect(dayStatusAfter('UNKNOWN', bullishEs)).toBe('VALID');
    expect(dayStatusAfter('UNKNOWN', { ruleKey: KEY, kind: 'STATUS', code: 'INVALID_WINDOW' })).toBe(
      'INVALID_WINDOW',
    );
  });
});
