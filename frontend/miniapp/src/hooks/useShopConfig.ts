import { useQuery } from '@tanstack/react-query';
import { fetchPublicShopConfig, type ShopConfig } from '@shop/shared';
import { api } from '@/lib/api';

/**
 * Customer-facing read of the shop's public branding config.
 * Stale-time 5 minutes — admin changes propagate to customers within that window.
 * No auth required (uses public endpoint).
 */
export function useShopConfig() {
  return useQuery<ShopConfig>({
    queryKey: ['shop-config'],
    queryFn: () => fetchPublicShopConfig(api),
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
    retry: 1,
  });
}
