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
