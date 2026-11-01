import { Badge, Group, Paper, Text } from '@mantine/core';
import { useMemo } from 'react';
import { RANGE_SWEEP_NO_EVENT, type Signal } from '../api/types';
import { latestCheckFor } from '../lib/signals';
import { formatNyTime } from '../lib/time';
import { COLORS } from '../theme';
import { PriceChart } from './PriceChart';
import { ReportsFeed } from './ReportsFeed';

const CHART_HEIGHT = 300;

/** One instrument: its chart with the rule's events on it, and its reports beside it. */
export function InstrumentPanel({ symbol, signals }: { symbol: string; signals: Signal[] }) {
  const check = useMemo(() => latestCheckFor(signals, symbol), [signals, symbol]);
  return (
    <Paper p="xs" withBorder style={{ background: COLORS.background, borderColor: COLORS.grid }}>
      <Group justify="space-between" mb={4} wrap="nowrap">
        <Text fw={700} c={COLORS.text}>
          {symbol}
        </Text>
        {check && (
          <Badge color="gray" variant={check.code === RANGE_SWEEP_NO_EVENT ? 'outline' : 'light'}>
            {check.code === RANGE_SWEEP_NO_EVENT ? 'Checked, no event' : 'Sweep event'} at{' '}
            {formatNyTime(Date.parse(check.eventTime!) / 1000)}
          </Badge>
        )}
      </Group>
      <Group align="flex-start" gap="xs" wrap="nowrap">
        <PriceChart symbol={symbol} signals={signals} height={CHART_HEIGHT} />
        <ReportsFeed symbol={symbol} signals={signals} height={CHART_HEIGHT} />
      </Group>
    </Paper>
  );
}
