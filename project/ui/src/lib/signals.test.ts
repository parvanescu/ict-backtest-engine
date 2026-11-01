import { describe, expect, it } from 'vitest';
import type { Signal } from '../api/types';
import {
  canonicalRuleKey,
  dayStatusAfter,
  describeCheck,
  latestCheckFor,
  latestEventFor,
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
const eventEs: Signal = {
  id: 2,
  ruleKey: KEY,
  kind: 'REPORT',
  code: 'RANGE_SWEEP_EVENT',
  scope: ['ES'],
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

    expect(mergeSignals([eventEs, error], [report, eventEs]).map((s) => s.id)).toEqual([1, 2]);
  });
});

describe('markersFor', () => {
  it('marks the events about the charted symbol only, not other reports', () => {
    expect(markersFor([report, eventEs], 'ES')).toEqual([
      expect.objectContaining({ time: 1_791_814_360, shape: 'circle', text: 'Sweep event' }),
    ]);
    expect(markersFor([report, eventEs], 'YM')).toEqual([]);
  });

  it('uses the same neutral marker whichever side was broken', () => {
    const high: Signal = { ...eventEs, id: 3, evidence: { trigger: { side: 'HIGH' } } };

    expect(markersFor([eventEs, high], 'ES').map((m) => [m.shape, m.color, m.text])).toEqual([
      ['circle', expect.any(String), 'Sweep event'],
      ['circle', expect.any(String), 'Sweep event'],
    ]);
  });
});

describe('latestEventFor', () => {
  it('finds the newest event about the symbol', () => {
    const later: Signal = { ...eventEs, id: 9 };

    expect(latestEventFor([eventEs, report, later], 'ES')).toBe(later);
    expect(latestEventFor([eventEs, report], 'YM')).toBeNull();
  });
});

const noEventNq: Signal = {
  id: 3,
  ruleKey: KEY,
  kind: 'REPORT',
  code: 'RANGE_SWEEP_NO_EVENT',
  scope: ['NQ'],
  eventTime: '2026-10-12T14:12:40Z',
  evidence: {
    trigger: { symbol: 'YM', side: 'LOW', price: 44168 },
    target: { barTime: '2026-10-12T14:12:40Z', low: 21108, H: 21120, L: 21090, M: 21105 },
  },
};

describe('latestCheckFor', () => {
  it('finds the newest check result about the symbol, event or no event', () => {
    expect(latestCheckFor([report, eventEs, noEventNq], 'NQ')).toBe(noEventNq);
    expect(latestCheckFor([report, eventEs, noEventNq], 'ES')).toBe(eventEs);
  });

  it('is null for a symbol that was never checked, and ignores other reports', () => {
    expect(latestCheckFor([report, eventEs, noEventNq], 'YM')).toBeNull();
    expect(latestCheckFor([], 'ES')).toBeNull();
  });

  it('takes the later of two checks about the same symbol', () => {
    const later: Signal = { ...noEventNq, id: 9, eventTime: '2026-10-12T14:20:15Z' };

    expect(latestCheckFor([noEventNq, later], 'NQ')).toBe(later);
  });
});

describe('describeCheck', () => {
  it('states the trigger and the target bar against its range, as facts', () => {
    expect(describeCheck(noEventNq)).toBe('YM broke 3-3-3 low 44168; NQ low 21108 outside L 21090 / M 21105');
  });

  it('uses the high side for a high break', () => {
    const high: Signal = {
      ...noEventNq,
      evidence: {
        trigger: { symbol: 'YM', side: 'HIGH', price: 44245 },
        target: { high: 21100, H: 21120, L: 21090, M: 21105 },
      },
    };

    expect(describeCheck(high)).toBe('YM broke 3-3-3 high 44245; NQ high 21100 outside M 21105 / H 21120');
  });
});

describe('reportsFor', () => {
  it('lists the reports about the symbol, newest first, up to the limit', () => {
    const reports = [1, 2, 3].map((id): Signal => ({ ...report, id }));

    expect(reportsFor([...reports, eventEs], 'YM', 2).map((s) => s.id)).toEqual([3, 2]);
    expect(reportsFor(reports, 'ES', 10)).toEqual([]);
    expect(reportsFor([report, eventEs], 'ES', 10)).toEqual([eventEs]);
  });
});

describe('rangeLevelsFor', () => {
  it('reads the 3-3-3 range from the latest event about the symbol', () => {
    expect(rangeLevelsFor([report, eventEs], 'ES')).toEqual({ high: 6032, low: 6026, mean: 6029 });
    expect(rangeLevelsFor([report, eventEs], 'YM')).toBeNull();
  });
});

describe('dayStatusAfter', () => {
  it('follows statuses and events', () => {
    expect(dayStatusAfter('UNKNOWN', report)).toBe('UNKNOWN');
    expect(dayStatusAfter('UNKNOWN', eventEs)).toBe('VALID');
    expect(dayStatusAfter('UNKNOWN', noEventNq)).toBe('VALID');
    expect(dayStatusAfter('UNKNOWN', { ruleKey: KEY, kind: 'STATUS', code: 'INVALID_WINDOW' })).toBe(
      'INVALID_WINDOW',
    );
  });
});
