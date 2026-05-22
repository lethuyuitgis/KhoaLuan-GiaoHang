export interface LocationPing {
  lat: string;
  lng: string;
  accuracy: string | null;
  heading: string | null;
  recordedAt: string;
}

export interface LocationMessage {
  orderId: string;
  lat: string;
  lng: string;
  accuracy: string | null;
  heading: string | null;
  recordedAt: string;
}
