/**
 * Payment module — VNPay sandbox integration.
 *
 * <p>Owns {@code payment} and {@code payment_transaction} tables. Depends on
 * {@code order} module for {@code OrderService.confirmAfterPayment} and on
 * {@code shared} for cross-module event records.
 *
 * <p>Cross-module signalling is one-way: this module publishes
 * {@code PaymentSucceededEvent} / {@code PaymentFailedEvent}; never imports
 * from {@code order}'s domain except via the public {@code OrderService} API.
 */
package com.shop.delivery.payment;
