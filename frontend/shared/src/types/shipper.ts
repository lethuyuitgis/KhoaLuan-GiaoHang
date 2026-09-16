export type ShipperState = 'AVAILABLE' | 'BUSY' | 'OFFLINE';
export type VehicleType = 'MOTORBIKE' | 'CAR' | 'BICYCLE';
/** Trạng thái duyệt của role SHIPPER (user_role.status). */
export type ShipperApprovalStatus = 'PENDING' | 'ACTIVE';

export interface ShipperResponse {
  userId: number;
  firstName: string | null;
  lastName: string | null;
  username: string | null;
  vehicleType: VehicleType;
  licensePlate: string | null;
  currentState: ShipperState;
  approvalStatus: ShipperApprovalStatus | null;
  ratingAvg: number;
  ratingCount: number;
  totalDeliveries: number;
}

export interface CreateShipperRequest {
  telegramUserId: number;
  vehicleType: VehicleType;
  licensePlate?: string;
}

/**
 * An AVAILABLE shipper ranked as a candidate for assigning a specific order.
 * distanceKm/lastLocationAt are null when the shipper has no location history
 * (never delivered) — the UI shows "chưa rõ vị trí" and sorts them last.
 */
export interface ShipperCandidateResponse {
  userId: number;
  firstName: string | null;
  lastName: string | null;
  username: string | null;
  vehicleType: VehicleType;
  licensePlate: string | null;
  ratingAvg: number;
  ratingCount: number;
  totalDeliveries: number;
  distanceKm: number | null;
  lastLocationAt: string | null;
}

/** Trạng thái nhận đơn của shipper — trả từ GET/POST /api/shipper/me/status. */
export interface ShipperStatus {
  state: ShipperState;
  online: boolean;
  busy: boolean;
}
