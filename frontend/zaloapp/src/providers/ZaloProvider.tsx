import { useEffect, type ReactNode } from 'react';
import { zalo } from '@/lib/zalo';

interface Props {
  children: ReactNode;
}

/**
 * Mirrors TelegramProvider — calls into the SDK wrapper once on mount so
 * the Zalo container (or mock) can finish its handshake before any page
 * tries to read accessToken / open links.
 */
export function ZaloProvider({ children }: Props) {
  useEffect(() => {
    zalo.ready();
  }, []);

  return <>{children}</>;
}
