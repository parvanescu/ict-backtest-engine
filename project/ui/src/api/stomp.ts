import { Client, type StompSubscription } from '@stomp/stompjs';

export type ConnectionStatus = 'connecting' | 'connected' | 'disconnected';

interface Wanted {
  destination: string;
  handler: (body: unknown) => void;
  active?: StompSubscription;
}

/**
 * One STOMP connection for the whole UI. Callers declare the destinations they want; they are subscribed while
 * connected and subscribed again after every reconnect (the service ends a session's subscriptions when the socket
 * drops). {@link epoch} increases on every (re)connect, so data can be reloaded after a gap.
 */
export class StompConnection {
  private readonly client: Client;
  private readonly wanted = new Set<Wanted>();
  private readonly listeners = new Set<() => void>();
  private status: ConnectionStatus = 'connecting';
  private connections = 0;

  constructor(url: string) {
    this.client = new Client({
      brokerURL: url,
      reconnectDelay: 2000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.wanted.forEach((w) => this.activate(w));
        this.connections++;
        this.setStatus('connected');
      },
      onWebSocketClose: () => {
        this.wanted.forEach((w) => (w.active = undefined));
        this.setStatus(this.client.active ? 'connecting' : 'disconnected');
      },
    });
  }

  start(): void {
    this.client.activate();
  }

  stop(): void {
    void this.client.deactivate();
  }

  getStatus = (): ConnectionStatus => this.status;

  /** Increases on every successful (re)connect. */
  getEpoch = (): number => this.connections;

  onChange = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  /** @returns a function that unsubscribes */
  subscribe(destination: string, handler: (body: unknown) => void): () => void {
    const wanted: Wanted = { destination, handler };
    this.wanted.add(wanted);
    if (this.client.connected) {
      this.activate(wanted);
    }
    return () => {
      this.wanted.delete(wanted);
      if (wanted.active && this.client.connected) {
        wanted.active.unsubscribe();
      }
      wanted.active = undefined;
    };
  }

  private activate(wanted: Wanted): void {
    wanted.active = this.client.subscribe(wanted.destination, (frame) => {
      try {
        wanted.handler(JSON.parse(frame.body));
      } catch (error) {
        console.error(`Bad message on ${wanted.destination}`, error);
      }
    });
  }

  private setStatus(status: ConnectionStatus): void {
    this.status = status;
    this.listeners.forEach((listener) => listener());
  }
}
