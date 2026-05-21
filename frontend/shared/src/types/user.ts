export type Role = 'CUSTOMER' | 'SHIPPER' | 'SHOP_OWNER';

export interface MeResponse {
  id: number;
  username: string | null;
  firstName: string | null;
  lastName: string | null;
  languageCode: string | null;
  roles: Role[];
}
