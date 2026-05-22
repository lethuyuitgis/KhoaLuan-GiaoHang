export type AssignmentStatus = 'OFFERED' | 'ACCEPTED' | 'REJECTED' | 'STARTED' | 'COMPLETED' | 'CANCELLED';

export interface AssignmentResponse {
  id: string;
  orderId: string;
  orderCode: string;
  customerId: number;
  customerName: string | null;
  customerPhone: string | null;
  deliveryAddress: string;
  deliveryLat: string;
  deliveryLng: string;
  distanceKm: string;
  deliveryFee: number;
  total: number;
  status: AssignmentStatus;
  orderStatus: string;
  assignedAt: string;
  acceptedAt: string | null;
  startedAt: string | null;
  deliveredAt: string | null;
}
