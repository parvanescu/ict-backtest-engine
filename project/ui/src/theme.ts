import { createTheme } from '@mantine/core';

/** Dark trading palette, close to TradingView's. */
export const COLORS = {
  background: '#131722',
  panel: '#1e222d',
  grid: '#2a2e39',
  text: '#d1d4dc',
  muted: '#787b86',
  open: '#2962ff',
  high: '#26a69a',
  low: '#ef5350',
  close: '#f5c542',
  bullish: '#26a69a',
  bearish: '#ef5350',
  range: '#787b86',
} as const;

export const theme = createTheme({
  primaryColor: 'blue',
  fontFamily: '-apple-system, BlinkMacSystemFont, "Trebuchet MS", Roboto, Ubuntu, sans-serif',
  defaultRadius: 'sm',
});
