package com.shop.delivery.delivery.listener;

import com.shop.delivery.delivery.domain.LedgerEntryType;
import com.shop.delivery.delivery.service.CommissionCalculator;
import com.shop.delivery.delivery.service.ShipperLedgerService;
import com.shop.delivery.delivery.service.event.OrderDeliveredEvent;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.order.service.ShopConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

/**
 * On OrderDeliveredEvent (AFTER_COMMIT phase, so order.status='DELIVERED' is
 * already persisted), compute shipper commission and append ledger entries:
 *
 *   - COMMISSION (+commission)
 *   - If payment_method == 'COD': COD_OWED (-order.total)  // shipper holds cash
 *
 * Commission base is `orders.delivery_fee_original` (Phase 14 snapshot). Idempotent
 * via existsByOrderIdAndEntryType — defensive against event replay.
 */
@Component
public class OrderCompletionListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletionListener.class);

    private final OrderRepository orderRepo;
    private final ShopConfigService shopConfigService;
    private final CommissionCalculator calculator;
    private final ShipperLedgerService ledgerService;

    public OrderCompletionListener(OrderRepository orderRepo,
                                   ShopConfigService shopConfigService,
                                   CommissionCalculator calculator,
                                   ShipperLedgerService ledgerService) {
        this.orderRepo = orderRepo;
        this.shopConfigService = shopConfigService;
        this.calculator = calculator;
        this.ledgerService = ledgerService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDelivered(OrderDeliveredEvent event) {
        if (ledgerService.alreadyHasEntry(event.orderId(), LedgerEntryType.COMMISSION)) {
            log.debug("Skipping commission for order {} — already recorded", event.orderId());
            return;
        }

        Order order = orderRepo.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.warn("OrderDeliveredEvent for unknown order {}", event.orderId());
            return;
        }

        BigDecimal pct = shopConfigService.getConfig().shipperCommissionPct();
        BigDecimal commission = calculator.commission(order.getDeliveryFeeOriginal(), pct);

        order.setShipperCommission(commission);
        orderRepo.save(order);

        ledgerService.append(
            event.shipperId(),
            LedgerEntryType.COMMISSION,
            commission,
            event.orderId(),
            "Hoa hồng đơn " + event.orderCode(),
            "system");

        // payment_method is an enum — compare via name()
        if ("COD".equalsIgnoreCase(order.getPaymentMethod().name())) {
            ledgerService.append(
                event.shipperId(),
                LedgerEntryType.COD_OWED,
                order.getTotal().negate(),
                event.orderId(),
                "Shipper đã thu COD đơn " + event.orderCode(),
                "system");
        }
    }
}
