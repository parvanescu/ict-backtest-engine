import { Badge, Group, Paper, Text } from '@mantine/core';
import { useMemo } from 'react';
import type { Signal } from '../api/types';
import { latestResultFor } from '../lib/signals';
import { formatNyTime } from '../lib/time';
import { COLORS } from '../theme';
import { PriceChart } from './PriceChart';
import { ReportsFeed } from './ReportsFeed';

const CHART_HEIGHT = 300;

/** One instrument: its chart with the rule's results on it, and its reports beside it. */
export function InstrumentPanel({ symbol, signals }: { symbol: string; signals: Signal[] }) {
  const result = useMemo(() => latestResultFor(signals, symbol), [signals, symbol]);
  return (
    <Paper p="xs" withBorder style={{ background: COLORS.background, borderColor: COLORS.grid }}>
      <Group justify="space-between" mb={4} wrap="nowrap">
        <Text fw={700} c={COLORS.text}>
          {symbol}
        </Text>
        {result && (
          <Badge color={result.verdict === 'BULLISH' ? 'teal' : 'red'} variant="light">
            {result.verdict} at {formatNyTime(Date.parse(result.eventTime!) / 1000)}
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
