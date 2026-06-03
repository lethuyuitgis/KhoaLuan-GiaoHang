/**
 * Public shop branding + fee shape served by GET /api/public/shop-config.
 * Admin uses the same shape for PUT /api/admin/shop-config.
 *
 * Color fields are 6-digit hex (#RRGGBB) — validated on backend.
 */
export interface ShopConfig {
  name: string;
  tagline: string;
  logoUrl: string | null;
  brandPrimary: string;
  brandSecondary: string;
  contactPhone: string | null;
  contactEmail: string | null;
  openingHours: string | null;
  pickupLat: number;
  pickupLng: number;
  pickupAddress: string;
  feeBase: number;
  feePerKm: number;
  freeKm: number;
}

/** Body for PUT /api/admin/shop-config — same shape as ShopConfig. */
export type UpdateShopConfigRequest = ShopConfig;
