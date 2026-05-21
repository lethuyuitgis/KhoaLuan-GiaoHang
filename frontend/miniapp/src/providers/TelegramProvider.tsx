import { useEffect, type ReactNode } from 'react';
import { tg } from '@/lib/telegram';

interface Props {
  children: ReactNode;
}

export function TelegramProvider({ children }: Props) {
  useEffect(() => {
    tg.ready();
    tg.expand();
  }, []);

  return <>{children}</>;
}
