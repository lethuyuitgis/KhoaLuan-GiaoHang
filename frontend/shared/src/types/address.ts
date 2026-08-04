/** A customer's saved delivery address (auto-captured on order placement). */
export interface SavedAddress {
  id: number;
  address: string;
  lat: number;
  lng: number;
  lastUsedAt: string;
}
