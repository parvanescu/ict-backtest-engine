import { Box, ScrollArea, Stack, Text } from '@mantine/core';
import { useMemo } from 'react';
import { RANGE_SWEEP_EVENT, RANGE_SWEEP_NO_EVENT, type Signal } from '../api/types';
import { describeCheck, reportsFor } from '../lib/signals';
import { formatNyTime } from '../lib/time';
import { COLORS } from '../theme';

/** Newest reports kept per instrument; older ones roll out of the window. */
const ROLLING_LIMIT = 200;

/**
 * The reports about one instrument (its range breaks and the sweep events it was reported in), newest first, beside
 * its chart. A rolling window rather than
 * chart markers: while price stays beyond a level there is one report per second.
 */
export function ReportsFeed({ symbol, signals, height }: { symbol: string; signals: Signal[]; height: number }) {
  const reports = useMemo(() => reportsFor(signals, symbol, ROLLING_LIMIT), [signals, symbol]);
  return (
    <Box w={200} style={{ flexShrink: 0, borderLeft: `1px solid ${COLORS.grid}` }} pl="xs">
      <Text size="xs" tt="uppercase" c="dimmed" fw={700} mb={4}>
        Reports ({reports.length})
      </Text>
      <ScrollArea h={height}>
        {reports.length === 0 ? (
          <Text size="xs" c="dimmed">
            No range break yet
          </Text>
        ) : (
          <Stack gap={2}>
            {reports.map((report) => (
              <ReportRow key={report.id} report={report} />
            ))}
          </Stack>
        )}
      </ScrollArea>
    </Box>
  );
}

function ReportRow({ report }: { report: Signal }) {
  if (report.code === RANGE_SWEEP_EVENT) {
    return <EventRow report={report} />;
  }
  return report.code === RANGE_SWEEP_NO_EVENT ? <NoEventRow report={report} /> : <ExtremityRow report={report} />;
}

/** An instrument that was checked after a break and did not qualify, as facts. */
function NoEventRow({ report }: { report: Signal }) {
  return (
    <Text size="xs" ff="monospace" c={COLORS.text}>
      <Text span c={COLORS.muted} inherit>
        {report.eventTime ? formatNyTime(Date.parse(report.eventTime) / 1000) : '—'}
      </Text>{' '}
      <Text span c={COLORS.muted} inherit>
        Checked, no event:
      </Text>{' '}
      {describeCheck(report)}
    </Text>
  );
}

/** A range sweep event, as facts: which instrument broke which side, and where this instrument's bar was. */
function EventRow({ report }: { report: Signal }) {
  const trigger = (report.evidence?.trigger ?? {}) as Record<string, unknown>;
  const target = (report.evidence?.target ?? {}) as Record<string, unknown>;
  const side = trigger.side === 'LOW' ? 'low' : 'high';
  return (
    <Text size="xs" ff="monospace" c={COLORS.text}>
      <Text span c={COLORS.muted} inherit>
        {report.eventTime ? formatNyTime(Date.parse(report.eventTime) / 1000) : '—'}
      </Text>{' '}
      <Text span c={COLORS.close} inherit>
        Sweep event:
      </Text>{' '}
      {String(trigger.symbol)} broke 3-3-3 {side} {String(trigger.price)}; {report.scope?.[0]} {side}{' '}
      {String(target[side])} within L {String(target.L)} / M {String(target.M)} / H {String(target.H)}
    </Text>
  );
}

function ExtremityRow({ report }: { report: Signal }) {
  const evidence = report.evidence ?? {};
  const low = evidence.side === 'LOW';
  return (
    <Text size="xs" ff="monospace" c={COLORS.text}>
      <Text span c={COLORS.muted} inherit>
        {report.eventTime ? formatNyTime(Date.parse(report.eventTime) / 1000) : '—'}
      </Text>{' '}
      <Text span c={low ? COLORS.low : COLORS.high} inherit>
        {low ? 'low' : 'high'} {String(evidence.price)}
      </Text>{' '}
      {low ? '<' : '>'} {String(evidence.level)}
    </Text>
  );
}
