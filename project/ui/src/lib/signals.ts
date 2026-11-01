import type { DayStatus, Signal } from '../api/types';
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
  if (signal.kind === 'RESULT') {
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

const isResultFor = (signal: Signal, symbol: string) =>
  signal.kind === 'RESULT' && signal.scope?.[0] === symbol && signal.eventTime !== undefined;

/**
 * Chart markers for the results about {@code symbol}. Labels stay factual (the verdict, not an instruction), as the
 * README requires. Reports are not markers: they go to the instrument's rolling window ({@link reportsFor}).
 */
export function markersFor(signals: Signal[], symbol: string): MarkerSpec[] {
  const markers: MarkerSpec[] = [];
  for (const signal of signals) {
    if (!isResultFor(signal, symbol)) {
      continue;
    }
    const time = Date.parse(signal.eventTime!) / 1000;
    const id = String(signal.id);
    if (signal.verdict === 'BULLISH') {
      markers.push({ time, id, position: 'belowBar', shape: 'arrowUp', color: COLORS.bullish, text: 'BULLISH' });
    } else if (signal.verdict === 'BEARISH') {
      markers.push({ time, id, position: 'aboveBar', shape: 'arrowDown', color: COLORS.bearish, text: 'BEARISH' });
    }
  }
  return markers.sort((a, b) => a.time - b.time);
}

/** The latest result about {@code symbol}, or null. */
export function latestResultFor(signals: Signal[], symbol: string): Signal | null {
  for (let i = signals.length - 1; i >= 0; i--) {
    if (isResultFor(signals[i], symbol)) {
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

/** The 3-3-3 range of {@code symbol}, from the latest result about it. */
export function rangeLevelsFor(signals: Signal[], symbol: string): RangeLevels | null {
  for (let i = signals.length - 1; i >= 0; i--) {
    const signal = signals[i];
    const target = signal.evidence?.target as Record<string, unknown> | undefined;
    if (signal.kind === 'RESULT' && signal.scope?.[0] === symbol && target) {
      const { H, L, M } = target;
      if (typeof H === 'number' && typeof L === 'number' && typeof M === 'number') {
        return { high: H, low: L, mean: M };
      }
    }
  }
  return null;
}
