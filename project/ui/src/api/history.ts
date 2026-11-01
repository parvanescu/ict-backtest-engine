import { HTTP_BASE } from './config';
import type { BarHistory, SignalHistory } from './types';

export async function fetchBars(symbol: string, signal?: AbortSignal): Promise<BarHistory> {
  const response = await fetch(`${HTTP_BASE}/api/bars/${encodeURIComponent(symbol)}`, { signal });
  if (!response.ok) {
    throw new Error(`GET /api/bars/${symbol}: HTTP ${response.status}`);
  }
  return response.json();
}

/** @returns null while the rule is not running yet (the service starts it right after the subscription). */
export async function fetchSignals(ruleKey: string, signal?: AbortSignal): Promise<SignalHistory | null> {
  const response = await fetch(`${HTTP_BASE}/api/signals/${encodeURIComponent(ruleKey)}`, { signal });
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`GET /api/signals/${ruleKey}: HTTP ${response.status}`);
  }
  return response.json();
}

/** Clears the service's stored bars, signals and rule state. JSON content type: the service refuses anything else. */
export async function resetData(): Promise<void> {
  const response = await fetch(`${HTTP_BASE}/api/reset`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{}',
  });
  if (!response.ok) {
    throw new Error(`POST /api/reset: HTTP ${response.status}`);
  }
}
