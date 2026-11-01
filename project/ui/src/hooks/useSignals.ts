import { useEffect, useState } from 'react';
import { SIGNALS_TOPIC } from '../api/config';
import { fetchSignals } from '../api/history';
import { useConnection, useStomp } from '../api/StompProvider';
import type { DayStatus, Signal } from '../api/types';
import { dayStatusAfter, mergeSignals } from '../lib/signals';

const HISTORY_ATTEMPTS = 10;
const HISTORY_RETRY_MS = 300;

/**
 * Runs the rule {@code ruleKey} (subscribing is what starts it) and collects its signals: live ones plus today's
 * history, merged by id. Null stops the rule.
 */
export function useSignals(ruleKey: string | null, resetEpoch = 0) {
  const connection = useStomp();
  const { status, epoch } = useConnection();
  const [signals, setSignals] = useState<Signal[]>([]);
  const [dayStatus, setDayStatus] = useState<DayStatus>('UNKNOWN');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setSignals([]);
    setDayStatus('UNKNOWN');
    setError(null);
    if (!ruleKey || status !== 'connected') {
      return;
    }
    let rejected = false;
    const controller = new AbortController();

    const unsubscribe = connection.subscribe(SIGNALS_TOPIC + ruleKey, (body) => {
      const signal = body as Signal;
      if (signal.kind === 'ERROR') {
        rejected = true;
        setError(signal.message ?? 'The service rejected the rule');
        return;
      }
      setSignals((current) => mergeSignals(current, [signal]));
      setDayStatus((current) => dayStatusAfter(current, signal));
    });

    // The service starts the rule right after the subscription, so the first calls may find it not running yet.
    const loadHistory = async () => {
      for (let attempt = 0; attempt < HISTORY_ATTEMPTS && !rejected; attempt++) {
        const history = await fetchSignals(ruleKey, controller.signal);
        if (history) {
          setSignals((current) => mergeSignals(current, history.signals));
          setDayStatus((current) => (current === 'UNKNOWN' ? history.dayStatus : current));
          return;
        }
        await new Promise((resolve) => setTimeout(resolve, HISTORY_RETRY_MS));
      }
    };
    loadHistory().catch((failure: unknown) => {
      if (!controller.signal.aborted) {
        setError(`Signal history unavailable: ${String(failure)}`);
      }
    });

    return () => {
      controller.abort();
      unsubscribe();
    };
  }, [connection, ruleKey, status, epoch, resetEpoch]);

  return { signals, dayStatus, error };
}
