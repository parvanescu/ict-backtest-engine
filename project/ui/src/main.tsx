import '@mantine/core/styles.css';
import { MantineProvider } from '@mantine/core';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { StompProvider } from './api/StompProvider';
import { App } from './App';
import { COLORS, theme } from './theme';

document.body.style.background = COLORS.background;

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MantineProvider theme={theme} defaultColorScheme="dark" forceColorScheme="dark">
      <StompProvider>
        <App />
      </StompProvider>
    </MantineProvider>
  </StrictMode>,
);
