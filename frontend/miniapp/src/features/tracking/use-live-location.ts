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

    let alive = true;
    let unsub: (() => void) | null = null;
    const client = createStompClient();

    client.onConnect = () => {
      if (!alive) return;
      setIsConnected(true);
      // Replace any previous subscription before creating a new one — STOMP auto-reconnect
      // can fire onConnect multiple times on the same client.
      unsub?.();
      unsub = subscribeOrderLocation(client, orderId, (msg: LocationMessage) => {
        if (!alive) return;
        setLocation({
          lat: Number(msg.lat),
          lng: Number(msg.lng),
          recordedAt: msg.recordedAt,
        });
      });
    };
    client.onDisconnect = () => {
      if (alive) setIsConnected(false);
    };
    client.onStompError = (frame) => {
      console.error('STOMP error', frame);
      if (alive) setIsConnected(false);
    };

    client.activate();

    return () => {
      alive = false;
      unsub?.();
      client.deactivate();
    };
  }, [orderId, enabled]);

  return { location, isConnected, isLoading };
}
