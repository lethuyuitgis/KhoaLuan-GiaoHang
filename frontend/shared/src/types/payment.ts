/**
 * VNPay create-payment response.
 */
export interface CreatePaymentResponse {
  paymentUrl: string;
  txnRef: string;
}

export interface CreatePaymentRequest {
  orderId: string;
}
