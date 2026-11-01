import { RANGE_SWEEP_EVENT, type DayStatus, type Signal } from '../api/types';
import { COLORS } from '../theme';

/** Topic name the service publishes to: rule type, then the symbols sorted and unique. */
export function canonicalRuleKey(rule: string, symbols: string[]): string {
  return `${rule}[${[...new Set(symbols)].sort().join(',')}]`;
}

/** Numbered signals of all lists, ordered by id, without duplicates. Errors (no id) are left out. */
export function mergeSignals(...lists: Signal[][]): Signal[] {
  const byId = new Map<number, Signal>();
  for (const list of lists) {
    for (const signal of list) {
      if (signal.id !== undefined && !byId.has(signal.id)) {
        byId.set(signal.id, signal);
      }
    }
  }
  return [...byId.values()].sort((a, b) => a.id! - b.id!);
}

export function dayStatusAfter(current: DayStatus, signal: Signal): DayStatus {
  if (signal.kind === 'STATUS' && signal.code === 'INVALID_WINDOW') {
    return 'INVALID_WINDOW';
  }
  if (signal.code === RANGE_SWEEP_EVENT) {
    return 'VALID';
  }
  return current;
}

export interface MarkerSpec {
  /** UTC epoch seconds. */
  time: number;
  position: 'aboveBar' | 'belowBar';
  shape: 'arrowUp' | 'arrowDown' | 'circle';
  color: string;
  text: string;
  id: string;
}

const isEventFor = (signal: Signal, symbol: string) =>
  signal.code === RANGE_SWEEP_EVENT && signal.scope?.[0] === symbol && signal.eventTime !== undefined;

/**
 * Chart markers for the range sweep events of {@code symbol}: one neutral marker per event, no direction. Other
 * reports are not markers: they go to the instrument's rolling window ({@link reportsFor}).
 */
export function markersFor(signals: Signal[], symbol: string): MarkerSpec[] {
  const markers: MarkerSpec[] = [];
  for (const signal of signals) {
    if (isEventFor(signal, symbol)) {
      markers.push({
        time: Date.parse(signal.eventTime!) / 1000,
        id: String(signal.id),
        position: 'aboveBar',
        shape: 'circle',
        color: COLORS.close,
        text: 'Sweep event',
      });
    }
  }
  return markers.sort((a, b) => a.time - b.time);
}

/** The latest range sweep event about {@code symbol}, or null. */
export function latestEventFor(signals: Signal[], symbol: string): Signal | null {
  for (let i = signals.length - 1; i >= 0; i--) {
    if (isEventFor(signals[i], symbol)) {
      return signals[i];
    }
  }
  return null;
}

/** The reports about {@code symbol}, newest first, at most {@code limit}. */
export function reportsFor(signals: Signal[], symbol: string, limit: number): Signal[] {
  const reports: Signal[] = [];
  for (let i = signals.length - 1; i >= 0 && reports.length < limit; i--) {
    if (signals[i].kind === 'REPORT' && signals[i].scope?.[0] === symbol) {
      reports.push(signals[i]);
    }
  }
  return reports;
}

export interface RangeLevels {
  high: number;
  low: number;
  mean: number;
}

/** The 3-3-3 range of {@code symbol}, from the latest event about it. */
export function rangeLevelsFor(signals: Signal[], symbol: string): RangeLevels | null {
  for (let i = signals.length - 1; i >= 0; i--) {
    const signal = signals[i];
    const target = signal.evidence?.target as Record<string, unknown> | undefined;
    if (signal.code === RANGE_SWEEP_EVENT && signal.scope?.[0] === symbol && target) {
      const { H, L, M } = target;
      if (typeof H === 'number' && typeof L === 'number' && typeof M === 'number') {
        return { high: H, low: L, mean: M };
      }
    }
  }
  return null;
}
