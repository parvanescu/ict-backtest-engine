import type { Bar, BarMessage } from '../api/types';

/** @returns null for anything that is not a complete bar (e.g. an ERROR message) */
export function toBar(message: BarMessage): Bar | null {
  if (message.type !== 'BAR' || !message.time) {
    return null;
  }
  const { open, high, low, close } = message;
  if (open === undefined || high === undefined || low === undefined || close === undefined) {
    return null;
  }
  return { time: Date.parse(message.time) / 1000, open, high, low, close, volume: message.volume ?? 0 };
}

/** Bars of all lists, ordered by time; when two share a time the first one seen is kept. */
export function mergeBars(...lists: Bar[][]): Bar[] {
  const byTime = new Map<number, Bar>();
  for (const list of lists) {
    for (const bar of list) {
      if (!byTime.has(bar.time)) {
        byTime.set(bar.time, bar);
      }
    }
  }
  return [...byTime.values()].sort((a, b) => a.time - b.time);
}

/** Index of the last of the ordered {@code times} that is at or before {@code time}, or -1. */
export function lastIndexAtOrBefore(times: number[], time: number): number {
  let low = 0;
  let high = times.length - 1;
  let found = -1;
  while (low <= high) {
    const mid = (low + high) >> 1;
    if (times[mid] <= time) {
      found = mid;
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }
  return found;
}
