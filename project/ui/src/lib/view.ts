/** How much history a chart shows. Bars are 1 second, so a span is a number of bars. */
export type ViewMode = '1m' | '5m' | '30m' | '2h' | 'all';

export const VIEW_MODES: { value: ViewMode; label: string }[] = [
  { value: '1m', label: '1m' },
  { value: '5m', label: '5m' },
  { value: '30m', label: '30m' },
  { value: '2h', label: '2h' },
  { value: 'all', label: 'All' },
];

const SPAN_BARS: Record<Exclude<ViewMode, 'all'>, number> = { '1m': 60, '5m': 300, '30m': 1800, '2h': 7200 };

/** Empty bars kept right of the last one, so the newest bar is not glued to the price axis. */
export const RIGHT_OFFSET_BARS = 5;

/**
 * The logical (bar index) range showing the last span of {@code mode} out of {@code barCount} bars, or null for
 * {@code all} (fit everything).
 */
export function logicalRangeFor(mode: ViewMode, barCount: number): { from: number; to: number } | null {
  if (mode === 'all') {
    return null;
  }
  const last = barCount - 1;
  return { from: last - SPAN_BARS[mode] + 1, to: last + RIGHT_OFFSET_BARS };
}
