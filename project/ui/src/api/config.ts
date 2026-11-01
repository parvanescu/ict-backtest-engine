/** Where the alert service runs. Override with VITE_ALERT_SERVICE_HTTP / VITE_ALERT_SERVICE_WS (see .env.example). */
export const HTTP_BASE: string = import.meta.env.VITE_ALERT_SERVICE_HTTP ?? 'http://127.0.0.1:8080';
export const WS_URL: string = import.meta.env.VITE_ALERT_SERVICE_WS ?? 'ws://127.0.0.1:8080/ws';

export const BARS_TOPIC = '/topic/bars/';
export const SIGNALS_TOPIC = '/topic/signals/';
