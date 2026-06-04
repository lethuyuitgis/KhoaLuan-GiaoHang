package com.shop.delivery.delivery.domain;

/**
 * Sign convention for shipper_ledger.amount (relative to shipper):
 *   COMMISSION         (+) shop owes shipper — auto on DELIVERED
 *   COD_OWED           (−) shipper owes shop — auto on DELIVERED if COD
 *   SETTLEMENT_PAYOUT  (−) shop paid shipper — manual by admin (clears + balance)
 *   SETTLEMENT_DEPOSIT (+) shipper paid shop — manual by admin (clears − balance)
 *
 * Balance = SUM(amount) WHERE shipper_id = ?
 *   Positive = shop owes shipper → admin should PAYOUT
 *   Negative = shipper owes shop → admin should DEPOSIT (after shipper hands cash over)
 */
public enum LedgerEntryType {
    COMMISSION,
    COD_OWED,
    SETTLEMENT_PAYOUT,
    SETTLEMENT_DEPOSIT
}
