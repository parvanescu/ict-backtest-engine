import { useEffect, useState, type RefObject } from 'react';
import { BARS_TOPIC } from '../api/config';
import { fetchBars } from '../api/history';
import { useConnection, useStomp } from '../api/StompProvider';
import type { Bar, BarMessage } from '../api/types';
import { mergeBars, toBar } from '../lib/bars';

/** The chart gets every bar at once; the legend (React state) is refreshed at most this often. */
const LEGEND_REFRESH_MS = 250;

/** Where bars go. Bars bypass React state: a day is up to 86,400 of them. */
export interface BarSink {
  /** Replaces everything shown with {@code bars} (ordered by time). */
  reset(bars: Bar[]): void;
  /** Appends a bar newer than the last one. */
  push(bar: Bar): void;
}

/**
 * Live 1-second bars of {@code symbol} plus today's history: subscribe first and buffer, fetch the history, merge by
 * time, then stream. Starts over after a reconnect, since bars may have been missed, and after a data reset
 * ({@code resetEpoch} changed).
 */
export function useBarFeed(symbol: string | null, sink: RefObject<BarSink | null>, resetEpoch = 0) {
  const connection = useStomp();
  const { status, epoch } = useConnection();
  const [latest, setLatest] = useState<Bar | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!symbol || status !== 'connected') {
      return;
    }
    let ready = false;
    let buffer: Bar[] = [];
    let lastTime = -Infinity;
    let newest: Bar | null = null;
    let refresh: ReturnType<typeof setTimeout> | undefined;
    const controller = new AbortController();
    const showNewest = (bar: Bar) => {
      newest = bar;
      refresh ??= setTimeout(() => {
        refresh = undefined;
        setLatest(newest);
      }, LEGEND_REFRESH_MS);
    };
    setError(null);
    setLatest(null);
    sink.current?.reset([]);

    const show = (bars: Bar[]) => {
      buffer = [];
      ready = true;
      lastTime = bars.at(-1)?.time ?? -Infinity;
      sink.current?.reset(bars);
      setLatest(bars.at(-1) ?? null);
    };

    const unsubscribe = connection.subscribe(BARS_TOPIC + symbol, (body) => {
      const message = body as BarMessage;
      if (message.type === 'ERROR') {
        setError(message.message ?? 'The service rejected the bar subscription');
        return;
      }
      const bar = toBar(message);
      if (!bar) {
        return;
      }
      if (!ready) {
        buffer.push(bar);
      } else if (bar.time > lastTime) {
        lastTime = bar.time;
        sink.current?.push(bar);
        showNewest(bar);
      }
    });

    fetchBars(symbol, controller.signal)
      .then((history) => {
        const stored = history.bars.map(toBar).filter((bar): bar is Bar => bar !== null);
        show(mergeBars(stored, buffer));
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) {
          setError(`Bar history unavailable: ${String(failure)}`);
          show(mergeBars(buffer));
        }
      });

    return () => {
      controller.abort();
      clearTimeout(refresh);
      unsubscribe();
    };
  }, [connection, symbol, status, epoch, sink, resetEpoch]);

  return { latest, error };
}
