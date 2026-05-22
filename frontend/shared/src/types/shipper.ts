export type ShipperState = 'AVAILABLE' | 'BUSY' | 'OFFLINE';
export type VehicleType = 'MOTORBIKE' | 'CAR' | 'BICYCLE';

export interface ShipperResponse {
  userId: number;
  firstName: string | null;
  lastName: string | null;
  username: string | null;
  vehicleType: VehicleType;
  licensePlate: string | null;
  currentState: ShipperState;
  ratingAvg: number;
  ratingCount: number;
  totalDeliveries: number;
}

export interface CreateShipperRequest {
  telegramUserId: number;
  vehicleType: VehicleType;
  licensePlate?: string;
}
