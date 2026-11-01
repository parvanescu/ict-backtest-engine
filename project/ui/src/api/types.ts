/** A message on /topic/bars/{symbol}, and an element of GET /api/bars. */
export interface BarMessage {
  type: 'BAR' | 'ERROR';
  symbol?: string;
  /** ISO-8601 UTC start of the 1-second bar. */
  time?: string;
  open?: number;
  high?: number;
  low?: number;
  close?: number;
  volume?: number;
  message?: string;
}

export interface BarHistory {
  symbol: string;
  tradingDay: string | null;
  bars: BarMessage[];
}

/** A bar as the chart uses it. */
export interface Bar {
  /** Bar start, UTC epoch seconds. */
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export type SignalKind = 'REPORT' | 'STATUS' | 'ERROR';
/** Code of the report a Sequence range sweep emits for the instrument it fired from. */
export const RANGE_SWEEP_EVENT = 'RANGE_SWEEP_EVENT';
export type DayStatus = 'UNKNOWN' | 'VALID' | 'INVALID_WINDOW';

/** A message on /topic/signals/{ruleKey}, and an element of GET /api/signals. */
export interface Signal {
  id?: number;
  ruleKey: string;
  rule?: string;
  kind: SignalKind;
  code?: string;
  level?: string;
  scope?: string[];
  /** ISO-8601 UTC time of the bar that produced it. */
  eventTime?: string;
  evidence?: Record<string, unknown>;
  message?: string;
}

export interface SignalHistory {
  ruleKey: string;
  dayStatus: DayStatus;
  tradingDay: string | null;
  signals: Signal[];
}
