package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.entity.ShipperRating;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.delivery.repository.ShipperRatingRepository;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.ConflictException;
import com.shop.delivery.shared.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Shipper-facing rating service — shipper rates the customer (attitude,
 * address accuracy, presence at delivery). Counterpart to {@link RatingService}.
 *
 * <p>Result is NOT exposed to the customer (private to shipper / admin).
 *
 * <p><b>Concurrency:</b> same pattern as {@link RatingService} — insert +
 * aggregate recompute in a single transaction. The aggregate columns live on
 * {@code telegram_user} (customer_rating_avg / customer_rating_count) and are
 * updated via a native query to avoid coupling the auth module's entity to
 * delivery-domain columns.
 */
@Service
public class ShipperRatingService {

    private static final Logger log = LoggerFactory.getLogger(ShipperRatingService.class);

    private final ShipperRatingRepository ratingRepo;
    private final OrderRepository orderRepo;
    private final DeliveryAssignmentRepository assignmentRepo;

    @PersistenceContext
    private EntityManager em;

    public ShipperRatingService(ShipperRatingRepository ratingRepo,
                                OrderRepository orderRepo,
                                DeliveryAssignmentRepository assignmentRepo) {
        this.ratingRepo = ratingRepo;
        this.orderRepo = orderRepo;
        this.assignmentRepo = assignmentRepo;
    }

    @Transactional
    public ShipperRating rateCustomer(UUID orderId, Long shipperId, int stars, String comment) {
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

        DeliveryAssignment assignment = assignmentRepo.findByOrderId(orderId)
            .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND",
                "Không tìm thấy phân công cho đơn này"));

        if (!assignment.getShipperId().equals(shipperId)) {
            throw new BusinessRuleException("NOT_YOUR_ORDER",
                "Đây không phải đơn của bạn");
        }

        ShipperRating r = new ShipperRating();
        r.setOrderId(orderId);
        r.setShipperId(shipperId);
        r.setCustomerId(order.getCustomerId());
        r.setStars((short) stars);
        r.setComment(comment);

        try {
            ratingRepo.saveAndFlush(r);
        } catch (DataIntegrityViolationException ex) {
            // UNIQUE(order_id) — double-click race
            throw new ConflictException("ALREADY_RATED", "Đơn đã được đánh giá rồi");
        }

        recomputeCustomerStats(order.getCustomerId());
        log.info("Shipper {} rated customer {} on order {} → {} stars",
            shipperId, order.getCustomerId(), orderId, stars);
        return r;
    }

    void recomputeCustomerStats(Long customerId) {
        ShipperRatingRepository.CustomerRatingStats stats = ratingRepo.aggregateForCustomer(customerId);
        BigDecimal avg = stats.getAvg() != null
            ? stats.getAvg().setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        int count = stats.getCount() != null ? stats.getCount() : 0;
        em.createNativeQuery("""
            UPDATE telegram_user
               SET customer_rating_avg = :avg,
                   customer_rating_count = :count
             WHERE id = :id
            """)
            .setParameter("avg", avg)
            .setParameter("count", count)
            .setParameter("id", customerId)
            .executeUpdate();
    }
}
