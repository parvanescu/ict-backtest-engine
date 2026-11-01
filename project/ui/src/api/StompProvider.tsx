import { createContext, useContext, useEffect, useState, useSyncExternalStore, type ReactNode } from 'react';
import { WS_URL } from './config';
import { StompConnection, type ConnectionStatus } from './stomp';

const StompContext = createContext<StompConnection | null>(null);

export function StompProvider({ children }: { children: ReactNode }) {
  const [connection] = useState(() => new StompConnection(WS_URL));
  useEffect(() => {
    connection.start();
    return () => connection.stop();
  }, [connection]);
  return <StompContext.Provider value={connection}>{children}</StompContext.Provider>;
}

export function useStomp(): StompConnection {
  const connection = useContext(StompContext);
  if (!connection) {
    throw new Error('useStomp must be used inside <StompProvider>');
  }
  return connection;
}

export function useConnection(): { status: ConnectionStatus; epoch: number } {
  const connection = useStomp();
  const status = useSyncExternalStore(connection.onChange, connection.getStatus);
  const epoch = useSyncExternalStore(connection.onChange, connection.getEpoch);
  return { status, epoch };
}
