import { useQuery } from '@tanstack/react-query';
import { getLatestLocation } from '@shop/shared';
import { api } from '@/lib/api';

export interface LiveLocation {
  lat: number;
  lng: number;
  recordedAt: string;
}

const LOCATION_POLL_MS = 5000;

/**
 * Live shipper location for an order via REST polling. Unlike the Telegram
 * Mini App (STOMP WebSocket), the Zalo Mini App has no WS channel, so we poll
 * {@code GET /api/orders/{id}/location} every few seconds while enabled.
 */
export function useLiveLocation(orderId: string | undefined, enabled: boolean): {
  location: LiveLocation | null;
  isLoading: boolean;
} {
  const { data, isLoading } = useQuery({
    queryKey: ['order', orderId, 'location'],
    queryFn: async (): Promise<LiveLocation | null> => {
      try {
        const ping = await getLatestLocation(api, orderId!);
        return { lat: Number(ping.lat), lng: Number(ping.lng), recordedAt: ping.recordedAt };
      } catch {
        return null; // no ping yet (shipper hasn't shared location)
      }
    },
    enabled: !!orderId && enabled,
    refetchInterval: enabled ? LOCATION_POLL_MS : false,
    retry: 0,
  });

  return { location: data ?? null, isLoading };
}
