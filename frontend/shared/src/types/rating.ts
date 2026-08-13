/** Response of POST /api/orders/{id}/rating (customer rates the shipper). */
export interface OrderRatingResponse {
  ratingId: number;
  orderId: string;
  stars: number;
  comment: string | null;
  createdAt: string;
}
