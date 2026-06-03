import type { AxiosInstance } from 'axios';
import type { ShopConfig, UpdateShopConfigRequest } from '../types';

/** Public read — no auth required. Used by miniapp + zaloapp for theming. */
export async function fetchPublicShopConfig(client: AxiosInstance): Promise<ShopConfig> {
  const { data } = await client.get<ShopConfig>('/api/public/shop-config');
  return data;
}

/** Admin read — SHOP_OWNER JWT required. Same shape as public. */
export async function fetchAdminShopConfig(client: AxiosInstance): Promise<ShopConfig> {
  const { data } = await client.get<ShopConfig>('/api/admin/shop-config');
  return data;
}

/** Admin write — SHOP_OWNER JWT required. */
export async function updateShopConfig(
  client: AxiosInstance,
  req: UpdateShopConfigRequest,
): Promise<ShopConfig> {
  const { data } = await client.put<ShopConfig>('/api/admin/shop-config', req);
  return data;
}
