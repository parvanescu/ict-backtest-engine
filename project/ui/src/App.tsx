import { Alert, AppShell, Badge, Group, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';
import { resetData } from './api/history';
import { useConnection } from './api/StompProvider';
import { ControlPanel } from './components/ControlPanel';
import { InstrumentPanel } from './components/InstrumentPanel';
import { useSignals } from './hooks/useSignals';
import { canonicalRuleKey } from './lib/signals';
import { COLORS } from './theme';

const RULE = 'SEQUENCE_RANGE_SWEEP';
const DEFAULT_INSTRUMENTS = ['ES', 'NQ', 'YM', 'RTY'];

const CONNECTION_BADGE = {
  connected: { color: 'teal', label: 'Live' },
  connecting: { color: 'yellow', label: 'Connecting' },
  disconnected: { color: 'red', label: 'Offline' },
} as const;

export function App() {
  const { status } = useConnection();
  const [instruments, setInstruments] = useState(DEFAULT_INSTRUMENTS);
  // On by default: the rule must be running before 09:45 New York to judge the day.
  const [running, setRunning] = useState(true);

  // Bumped after the service wiped the day's data, so charts and signals reload from the (now empty) service.
  const [resetEpoch, setResetEpoch] = useState(0);
  const [resetError, setResetError] = useState<string | null>(null);

  const ruleKey = canonicalRuleKey(RULE, instruments);
  const { signals, dayStatus, error } = useSignals(running ? ruleKey : null, resetEpoch);
  const reset = async () => {
    setResetError(null);
    try {
      await resetData();
      setResetEpoch((epoch) => epoch + 1);
    } catch (failure) {
      setResetError(String(failure));
    }
  };
  const connection = CONNECTION_BADGE[status];

  return (
    <AppShell header={{ height: 52 }} navbar={{ width: 260, breakpoint: 'sm' }} footer={{ height: 30 }} padding="sm">
      <AppShell.Header style={{ background: COLORS.panel }}>
        <Group h="100%" px="md" justify="space-between">
          <Group gap="xs">
            <Title order={4} c={COLORS.text}>
              Alert Service
            </Title>
            <Text size="sm" c="dimmed">
              Quarterly theory rules
            </Text>
          </Group>
          <Badge color={connection.color} variant="dot">
            {connection.label}
          </Badge>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="md" style={{ background: COLORS.panel }}>
        <ControlPanel
          instruments={instruments}
          onInstruments={setInstruments}
          ruleKey={ruleKey}
          running={running}
          onRunning={setRunning}
          dayStatus={dayStatus}
          onReset={reset}
        />
      </AppShell.Navbar>

      <AppShell.Main style={{ background: COLORS.background }}>
        <Stack gap="sm">
          {error && (
            <Alert color="red" variant="light" title="Rule rejected">
              {error}
            </Alert>
          )}
          {resetError && (
            <Alert color="red" variant="light" title="Reset failed">
              {resetError}
            </Alert>
          )}
          {running && dayStatus === 'INVALID_WINDOW' && (
            <Alert color="orange" variant="light" title="No signals today">
              The rule started after 09:45 New York, so it missed the 3-3-3 range of at least one instrument.
            </Alert>
          )}
          <SimpleGrid cols={{ base: 1, xl: 2 }} spacing="sm">
            {instruments.map((symbol) => (
              <InstrumentPanel key={symbol} symbol={symbol} signals={signals} resetEpoch={resetEpoch} />
            ))}
          </SimpleGrid>
        </Stack>
      </AppShell.Main>

      <AppShell.Footer px="md" style={{ background: COLORS.panel }}>
        <Text size="xs" c="dimmed" lh="30px">
          Informational tool only, not financial advice. It displays market data you supply and computed values; it never
          places trades.
        </Text>
      </AppShell.Footer>
    </AppShell>
  );
}
