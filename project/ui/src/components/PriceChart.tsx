import { Alert, Box, Group, SegmentedControl, Text } from '@mantine/core';
import {
  ColorType,
  CrosshairMode,
  createChart,
  createSeriesMarkers,
  LineSeries,
  LineStyle,
  type IChartApi,
  type IPriceLine,
  type ISeriesApi,
  type ISeriesMarkersPluginApi,
  type Time,
  type UTCTimestamp,
} from 'lightweight-charts';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { Bar, Signal } from '../api/types';
import { useBarFeed, type BarSink } from '../hooks/useBarFeed';
import { lastIndexAtOrBefore } from '../lib/bars';
import { markersFor, rangeLevelsFor, type MarkerSpec } from '../lib/signals';
import { formatNyDateTime, formatNyTime } from '../lib/time';
import { logicalRangeFor, RIGHT_OFFSET_BARS, VIEW_MODES, type ViewMode } from '../lib/view';
import { COLORS } from '../theme';

const FIELDS = ['open', 'high', 'low', 'close'] as const;
type Field = (typeof FIELDS)[number];

const LINE_COLORS: Record<Field, string> = {
  open: COLORS.open,
  high: COLORS.high,
  low: COLORS.low,
  close: COLORS.close,
};

interface ChartParts {
  chart: IChartApi;
  series: Record<Field, ISeriesApi<'Line'>>;
  markers: ISeriesMarkersPluginApi<Time>;
  rangeLines: IPriceLine[];
  /** Start times of the bars shown, ordered. */
  times: number[];
  /** Markers waiting for their bar to arrive. */
  pendingMarkers: boolean;
  /** The preset being followed, or null once the user zoomed or panned by hand. */
  mode: ViewMode | null;
  /** A view update is scheduled for the next frame. */
  viewScheduled: boolean;
}

/** Narrow enough for a whole day of 1-second bars (86,400) to fit on screen when zooming out. */
const MIN_BAR_SPACING = 0.001;
/** A pointer moving more than this between press and release is a pan, not a click. */
const DRAG_PIXELS = 3;

/**
 * The 1-second bars of one symbol as four lines (open, high, low, close), with the rule's results as markers and
 * the 3-3-3 range as dashed lines. Times are New York time.
 */
export function PriceChart({
  symbol,
  signals,
  height,
  resetEpoch,
}: {
  symbol: string;
  signals: Signal[];
  height: number;
  resetEpoch: number;
}) {
  const container = useRef<HTMLDivElement>(null);
  const parts = useRef<ChartParts | null>(null);
  const markerSpecs = useRef<MarkerSpec[]>([]);
  const sink = useRef<BarSink | null>(null);
  const [hovered, setHovered] = useState<Bar | null>(null);
  const [mode, setMode] = useState<ViewMode | null>('all');

  useEffect(() => {
    if (!container.current) {
      return;
    }
    const chart = createChart(container.current, {
      autoSize: true,
      layout: { background: { type: ColorType.Solid, color: COLORS.background }, textColor: COLORS.text },
      grid: { vertLines: { color: COLORS.grid }, horzLines: { color: COLORS.grid } },
      crosshair: { mode: CrosshairMode.Normal },
      rightPriceScale: { borderColor: COLORS.grid },
      timeScale: {
        borderColor: COLORS.grid,
        timeVisible: true,
        secondsVisible: true,
        tickMarkFormatter: (time: Time) => formatNyTime(time as number),
        minBarSpacing: MIN_BAR_SPACING,
        rightOffset: RIGHT_OFFSET_BARS,
      },
      localization: { timeFormatter: (time: Time) => formatNyDateTime(time as number) },
    });
    const series = Object.fromEntries(
      FIELDS.map((field) => [
        field,
        chart.addSeries(LineSeries, {
          color: LINE_COLORS[field],
          lineWidth: 2,
          title: field[0].toUpperCase(),
          priceLineVisible: field === 'close',
        }),
      ]),
    ) as Record<Field, ISeriesApi<'Line'>>;
    const created: ChartParts = {
      chart,
      series,
      markers: createSeriesMarkers(series.close, []),
      rangeLines: [],
      times: [],
      pendingMarkers: false,
      mode: 'all',
      viewScheduled: false,
    };
    parts.current = created;

    // Zooming or panning by hand stops following the preset; double-click goes back to everything.
    const element = container.current;
    let pressX: number | null = null;
    const toCustom = () => {
      created.mode = null;
      setMode(null);
    };
    const onPointerDown = (event: PointerEvent) => (pressX = event.clientX);
    const onPointerUp = (event: PointerEvent) => {
      if (pressX !== null && Math.abs(event.clientX - pressX) > DRAG_PIXELS) {
        toCustom();
      }
      pressX = null;
    };
    const onDoubleClick = () => {
      created.mode = 'all';
      setMode('all');
      applyView(created);
    };
    element.addEventListener('wheel', toCustom, { passive: true });
    element.addEventListener('pointerdown', onPointerDown);
    element.addEventListener('pointerup', onPointerUp);
    element.addEventListener('dblclick', onDoubleClick);

    chart.subscribeCrosshairMove((param) => {
      if (param.time === undefined) {
        setHovered(null);
        return;
      }
      const value = (field: Field) => {
        const point = param.seriesData.get(series[field]) as { value?: number } | undefined;
        return point?.value ?? Number.NaN;
      };
      setHovered({
        time: param.time as number,
        open: value('open'),
        high: value('high'),
        low: value('low'),
        close: value('close'),
        volume: 0,
      });
    });

    sink.current = {
      reset(bars) {
        for (const field of FIELDS) {
          series[field].setData(bars.map((bar) => ({ time: bar.time as UTCTimestamp, value: bar[field] })));
        }
        created.times = bars.map((bar) => bar.time);
        applyMarkers(created, markerSpecs.current);
        applyView(created);
      },
      push(bar) {
        for (const field of FIELDS) {
          series[field].update({ time: bar.time as UTCTimestamp, value: bar[field] });
        }
        created.times.push(bar.time);
        if (created.pendingMarkers) {
          applyMarkers(created, markerSpecs.current);
        }
        scheduleView(created);
      },
    };

    return () => {
      element.removeEventListener('wheel', toCustom);
      element.removeEventListener('pointerdown', onPointerDown);
      element.removeEventListener('pointerup', onPointerUp);
      element.removeEventListener('dblclick', onDoubleClick);
      sink.current = null;
      parts.current = null;
      chart.remove();
    };
  }, []);

  const { latest, error } = useBarFeed(symbol, sink, resetEpoch);

  const markers = useMemo(() => markersFor(signals, symbol), [signals, symbol]);
  const range = useMemo(() => rangeLevelsFor(signals, symbol), [signals, symbol]);

  useEffect(() => {
    markerSpecs.current = markers;
    if (parts.current) {
      applyMarkers(parts.current, markers);
    }
  }, [markers]);

  useEffect(() => {
    const current = parts.current;
    if (!current) {
      return;
    }
    current.rangeLines.forEach((line) => current.series.close.removePriceLine(line));
    current.rangeLines = range
      ? [
          { price: range.high, title: '3-3-3 H' },
          { price: range.mean, title: '3-3-3 M' },
          { price: range.low, title: '3-3-3 L' },
        ].map((line) =>
          current.series.close.createPriceLine({
            ...line,
            color: COLORS.range,
            lineWidth: 1,
            lineStyle: LineStyle.Dashed,
            axisLabelVisible: true,
          }),
        )
      : [];
  }, [range]);

  const choose = (value: string) => {
    const chosen = value as ViewMode;
    setMode(chosen);
    if (parts.current) {
      parts.current.mode = chosen;
      applyView(parts.current);
    }
  };

  const shown = hovered ?? latest;

  return (
    <Box style={{ flex: 1, minWidth: 0 }}>
      <Group gap="md" mb={4} wrap="nowrap">
        {FIELDS.map((field) => (
          <Text key={field} size="xs" c={LINE_COLORS[field]} ff="monospace">
            {field[0].toUpperCase()} {shown && Number.isFinite(shown[field]) ? shown[field] : '—'}
          </Text>
        ))}
        <Text size="xs" c={COLORS.muted} ff="monospace">
          {shown ? formatNyTime(shown.time) : '1s · New York time'}
        </Text>
        <SegmentedControl
          size="xs"
          ml="auto"
          data={VIEW_MODES}
          value={mode ?? ''}
          onChange={choose}
          title="History shown; zoom with the wheel, double-click for everything"
        />
      </Group>
      {error && (
        <Alert color="red" variant="light" mb={4} p="xs">
          {error}
        </Alert>
      )}
      <div ref={container} style={{ height }} />
    </Box>
  );
}

/** Shows the history the followed preset asks for; nothing once the user took over. */
function applyView(parts: ChartParts) {
  if (parts.mode === null || parts.times.length === 0) {
    return;
  }
  const range = logicalRangeFor(parts.mode, parts.times.length);
  if (range) {
    parts.chart.timeScale().setVisibleLogicalRange(range);
  } else {
    parts.chart.timeScale().fitContent();
  }
}

/** Bars can arrive many times per frame; the view follows them once per frame. */
function scheduleView(parts: ChartParts) {
  if (parts.mode === null || parts.viewScheduled) {
    return;
  }
  parts.viewScheduled = true;
  requestAnimationFrame(() => {
    parts.viewScheduled = false;
    applyView(parts);
  });
}

/** Places each marker on the bar at or before its time; markers newer than the last bar wait for it. */
function applyMarkers(parts: ChartParts, specs: MarkerSpec[]) {
  const lastTime = parts.times.at(-1) ?? -Infinity;
  let pending = false;
  const placed = [];
  for (const spec of specs) {
    const index = lastIndexAtOrBefore(parts.times, spec.time);
    if (spec.time > lastTime || index < 0) {
      pending = true;
      continue;
    }
    placed.push({
      id: spec.id,
      time: parts.times[index] as UTCTimestamp,
      position: spec.position,
      shape: spec.shape,
      color: spec.color,
      text: spec.text,
    });
  }
  parts.pendingMarkers = pending;
  parts.markers.setMarkers(placed);
}
