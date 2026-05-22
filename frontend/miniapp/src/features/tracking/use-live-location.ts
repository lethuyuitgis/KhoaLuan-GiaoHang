import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getLatestLocation } from '@shop/shared';
import { api } from '@/lib/api';
import { createStompClient, subscribeOrderLocation, type LocationMessage } from '@/lib/ws';
import { tg } from '@/lib/telegram';

export interface LiveLocation {
  lat: number;
  lng: number;
  recordedAt: string;
}

/**
 * Hook that subscribes to live location for an order.
 * 1. Fetches latest via REST as initial state (if available).
 * 2. Opens STOMP connection and subscribes to /topic/order/{id}/location.
 */
export function useLiveLocation(orderId: string | undefined, enabled: boolean): {
  location: LiveLocation | null;
  isConnected: boolean;
  isLoading: boolean;
} {
  const [location, setLocation] = useState<LiveLocation | null>(null);
  const [isConnected, setIsConnected] = useState(false);

  // Fetch initial via REST
  const { isLoading } = useQuery({
    queryKey: ['order-location', orderId],
    queryFn: async () => {
      if (!orderId) return null;
      try {
        const ping = await getLatestLocation(api, orderId);
        setLocation({
          lat: Number(ping.lat),
          lng: Number(ping.lng),
          recordedAt: ping.recordedAt,
        });
        return ping;
      } catch {
        return null;
      }
    },
    enabled: !!orderId && enabled,
    retry: 0,
    staleTime: Infinity,
  });

  // Connect STOMP
  useEffect(() => {
    if (!orderId || !enabled || !tg.isInTelegram()) return;

    const client = createStompClient();

    client.onConnect = () => {
      setIsConnected(true);
      subscribeOrderLocation(client, orderId, (msg: LocationMessage) => {
        setLocation({
          lat: Number(msg.lat),
          lng: Number(msg.lng),
          recordedAt: msg.recordedAt,
        });
      });
    };
    client.onDisconnect = () => setIsConnected(false);
    client.onStompError = (frame) => {
      console.error('STOMP error', frame);
      setIsConnected(false);
    };

    client.activate();

    return () => {
      client.deactivate();
      setIsConnected(false);
    };
  }, [orderId, enabled]);

  return { location, isConnected, isLoading };
}
