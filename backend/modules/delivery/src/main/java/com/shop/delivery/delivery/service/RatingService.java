package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.entity.Rating;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.delivery.repository.RatingRepository;
import com.shop.delivery.delivery.repository.ShipperProfileRepository;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.ConflictException;
import com.shop.delivery.shared.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Customer-facing rating service. {@link #rate} is invoked from
 * {@code RatingController} (HTTP) and from {@code RatingCallbackHandler} (bot).
 *
 * <p><b>Concurrency:</b> rating insert + shipper-stats recompute happen in a
 * single transaction. The aggregate query sees the freshly-inserted row (same tx,
 * READ COMMITTED). Concurrent inserts for different orders each recompute the
 * full aggregate so the last-write-wins outcome on {@code shipper_profile} still
 * reflects all committed ratings. See research §1.2.
 */
@Service
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    private final RatingRepository ratingRepo;
    private final OrderRepository orderRepo;
    private final DeliveryAssignmentRepository assignmentRepo;
    private final ShipperProfileRepository shipperRepo;

    public RatingService(RatingRepository ratingRepo,
                         OrderRepository orderRepo,
                         DeliveryAssignmentRepository assignmentRepo,
                         ShipperProfileRepository shipperRepo) {
        this.ratingRepo = ratingRepo;
        this.orderRepo = orderRepo;
        this.assignmentRepo = assignmentRepo;
        this.shipperRepo = shipperRepo;
    }

    @Transactional
    public Rating rate(UUID orderId, Long customerId, int stars, String comment) {
        if (stars < 1 || stars > 5) {
            throw new BusinessRuleException("STARS_OUT_OF_RANGE", "Số sao phải từ 1 đến 5");
        }

        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND",
                "Không tìm thấy đơn hàng"));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessRuleException("ORDER_NOT_RATEABLE",
                "Chỉ đánh giá được sau khi đơn đã giao");
        }
        if (!order.getCustomerId().equals(customerId)) {
            throw new BusinessRuleException("NOT_YOUR_ORDER",
                "Đây không phải đơn của bạn");
        }

        DeliveryAssignment assignment = assignmentRepo.findByOrderId(orderId)
            .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND",
                "Không tìm thấy phân công cho đơn này"));

        Rating r = new Rating();
        r.setOrderId(orderId);
        r.setCustomerId(customerId);
        r.setShipperId(assignment.getShipperId());
        r.setStars((short) stars);
        r.setComment(comment);

        try {
            ratingRepo.saveAndFlush(r);
        } catch (DataIntegrityViolationException ex) {
            // UNIQUE(order_id) — double-click race
            throw new ConflictException("ALREADY_RATED", "Đơn đã được đánh giá rồi");
        }

        recomputeShipperStats(assignment.getShipperId());
        log.info("Customer {} rated order {} → {} stars", customerId, orderId, stars);
        return r;
    }

    @Transactional
    public void updateComment(UUID orderId, Long customerId, String comment) {
        Rating r = ratingRepo.findByOrderId(orderId)
            .orElseThrow(() -> new NotFoundException("RATING_NOT_FOUND",
                "Chưa có đánh giá cho đơn này"));

        if (!r.getCustomerId().equals(customerId)) {
            throw new BusinessRuleException("NOT_YOUR_RATING",
                "Không phải đánh giá của bạn");
        }
        r.setComment(comment);
        ratingRepo.save(r);
    }

    private void recomputeShipperStats(Long shipperId) {
        RatingRepository.RatingStats stats = ratingRepo.aggregateForShipper(shipperId);
        shipperRepo.findById(shipperId).ifPresent(sp -> {
            BigDecimal avg = stats.getAvg() != null
                ? stats.getAvg().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
            sp.setRatingAvg(avg);
            sp.setRatingCount(stats.getCount() != null ? stats.getCount() : 0);
            shipperRepo.save(sp);
        });
    }
}
