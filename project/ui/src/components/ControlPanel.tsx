import { Badge, Button, Divider, Stack, Switch, TagsInput, Text } from '@mantine/core';
import { useState } from 'react';
import type { DayStatus } from '../api/types';

const DAY_STATUS: Record<DayStatus, { color: string; label: string }> = {
  UNKNOWN: { color: 'gray', label: 'Day not judged yet' },
  VALID: { color: 'teal', label: 'Day valid' },
  INVALID_WINDOW: { color: 'red', label: 'No signals today: started too late' },
};

interface Props {
  instruments: string[];
  onInstruments(instruments: string[]): void;
  ruleKey: string;
  running: boolean;
  onRunning(running: boolean): void;
  dayStatus: DayStatus;
  onReset(): Promise<void>;
}

export function ControlPanel(props: Props) {
  const status = DAY_STATUS[props.dayStatus];
  const [resetting, setResetting] = useState(false);
  return (
    <Stack gap="md">
      <div>
        <Text size="xs" tt="uppercase" c="dimmed" fw={700}>
          Rule
        </Text>
        <Text fw={600}>Sequence range sweep</Text>
      </div>
      <TagsInput
        label="Instruments"
        description="One chart each. Uppercase symbols, e.g. ES"
        value={props.instruments}
        onChange={(symbols) =>
          props.onInstruments([...new Set(symbols.map((s) => s.trim().toUpperCase()).filter(Boolean))])
        }
        disabled={props.running}
      />
      <Switch
        label="Run rule"
        checked={props.running}
        onChange={(event) => props.onRunning(event.currentTarget.checked)}
        disabled={props.instruments.length < 2}
      />
      <Text size="xs" c="dimmed" ff="monospace" style={{ wordBreak: 'break-all' }}>
        {props.ruleKey}
      </Text>
      {props.running && (
        <Badge color={status.color} variant="light" fullWidth>
          {status.label}
        </Badge>
      )}

      <Button
        variant="light"
        color="gray"
        loading={resetting}
        onClick={() => {
          setResetting(true);
          props.onReset().finally(() => setResetting(false));
        }}
      >
        Reset data
      </Button>
      <Text size="xs" c="dimmed">
        Clears the day's bars, signals and rule state in the service and on screen. The rule keeps running.
      </Text>

      <Divider />

      <Text size="xs" c="dimmed">
        The rule needs at least two instruments and must be running before 09:45 New York (3-3-3) to judge the day.
        Results appear as arrows on the instrument's chart, range breaks in the reports beside it. Bar history covers
        the bars received since the instrument's chart first opened today.
      </Text>
    </Stack>
  );
}
