/** The quarterly grid is anchored in New York time, so the UI shows New York time. */
export const NEW_YORK = 'America/New_York';

const timeOfDay = new Intl.DateTimeFormat('en-GB', {
  timeZone: NEW_YORK,
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

const dayAndTime = new Intl.DateTimeFormat('en-GB', {
  timeZone: NEW_YORK,
  month: 'short',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

/** e.g. "10:12:40" (New York) */
export function formatNyTime(epochSeconds: number): string {
  return timeOfDay.format(epochSeconds * 1000);
}

/** e.g. "12 Oct, 10:12:40" (New York) */
export function formatNyDateTime(epochSeconds: number): string {
  return dayAndTime.format(epochSeconds * 1000);
}
