package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.entity.DeliveryAssignment;
import com.shop.delivery.delivery.entity.Rating;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.DeliveryAssignmentRepository;
import com.shop.delivery.delivery.repository.RatingRepository;
import com.shop.delivery.delivery.repository.ShipperProfileRepository;
import com.shop.delivery.order.domain.OrderStatus;
import com.shop.delivery.order.entity.Order;
import com.shop.delivery.order.repository.OrderRepository;
import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.ConflictException;
import com.shop.delivery.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock RatingRepository ratingRepo;
    @Mock OrderRepository orderRepo;
    @Mock DeliveryAssignmentRepository assignmentRepo;
    @Mock ShipperProfileRepository shipperRepo;

    @InjectMocks RatingService service;

    private UUID orderId;
    private Long customerId;
    private Long shipperId;
    private Order order;
    private DeliveryAssignment assignment;
    private ShipperProfile shipper;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        customerId = 1001L;
        shipperId = 2001L;

        order = new Order();
        order.setId(orderId);
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.DELIVERED);

        assignment = new DeliveryAssignment();
        assignment.setOrderId(orderId);
        assignment.setShipperId(shipperId);

        shipper = new ShipperProfile();
        shipper.setUserId(shipperId);
        shipper.setRatingAvg(BigDecimal.ZERO);
        shipper.setRatingCount(0);
    }

    @Test
    void rate_happyPath_insertsRatingAndRecomputesShipperStats() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(assignmentRepo.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(shipperRepo.findById(shipperId)).thenReturn(Optional.of(shipper));
        RatingRepository.RatingStats stats = mockStats(new BigDecimal("4.50"), 4);
        when(ratingRepo.aggregateForShipper(shipperId)).thenReturn(stats);

        Rating result = service.rate(orderId, customerId, 5, null);

        ArgumentCaptor<Rating> ratingCaptor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepo).saveAndFlush(ratingCaptor.capture());
        assertThat(ratingCaptor.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(ratingCaptor.getValue().getCustomerId()).isEqualTo(customerId);
        assertThat(ratingCaptor.getValue().getShipperId()).isEqualTo(shipperId);
        assertThat(ratingCaptor.getValue().getStars()).isEqualTo((short) 5);
        assertThat(ratingCaptor.getValue().getComment()).isNull();

        ArgumentCaptor<ShipperProfile> shipperCaptor = ArgumentCaptor.forClass(ShipperProfile.class);
        verify(shipperRepo).save(shipperCaptor.capture());
        assertThat(shipperCaptor.getValue().getRatingAvg()).isEqualByComparingTo("4.50");
        assertThat(shipperCaptor.getValue().getRatingCount()).isEqualTo(4);
    }

    @Test
    void rate_orderNotFound_throwsNotFoundException() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rate(orderId, customerId, 5, null))
            .isInstanceOf(NotFoundException.class)
            .hasFieldOrPropertyWithValue("code", "ORDER_NOT_FOUND");

        verify(ratingRepo, never()).saveAndFlush(any());
    }

    @Test
    void rate_orderNotDelivered_throwsBusinessRuleException() {
        order.setStatus(OrderStatus.DELIVERING);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.rate(orderId, customerId, 5, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Chỉ đánh giá");

        verify(ratingRepo, never()).saveAndFlush(any());
    }

    @Test
    void rate_wrongCustomer_throwsBusinessRuleException() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.rate(orderId, 9999L, 5, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasFieldOrPropertyWithValue("code", "NOT_YOUR_ORDER");

        verify(ratingRepo, never()).saveAndFlush(any());
    }

    @Test
    void rate_alreadyRated_translatesIntegrityViolationToConflict() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(assignmentRepo.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(ratingRepo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("UNIQUE"));

        assertThatThrownBy(() -> service.rate(orderId, customerId, 5, null))
            .isInstanceOf(ConflictException.class)
            .hasFieldOrPropertyWithValue("code", "ALREADY_RATED");

        verify(shipperRepo, never()).save(any());
    }

    @Test
    void rate_starsOutOfRange_throwsBusinessRuleException() {
        assertThatThrownBy(() -> service.rate(orderId, customerId, 0, null))
            .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.rate(orderId, customerId, 6, null))
            .isInstanceOf(BusinessRuleException.class);

        verify(orderRepo, never()).findById(any());
    }

    @Test
    void updateComment_happyPath_writesComment() {
        Rating r = new Rating();
        r.setOrderId(orderId);
        r.setCustomerId(customerId);
        r.setShipperId(shipperId);
        r.setStars((short) 4);

        when(ratingRepo.findByOrderId(orderId)).thenReturn(Optional.of(r));

        service.updateComment(orderId, customerId, "Giao nhanh, thái độ tốt");

        ArgumentCaptor<Rating> captor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepo).save(captor.capture());
        assertThat(captor.getValue().getComment()).isEqualTo("Giao nhanh, thái độ tốt");
    }

    @Test
    void updateComment_ratingNotFound_throwsNotFoundException() {
        when(ratingRepo.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateComment(orderId, customerId, "x"))
            .isInstanceOf(NotFoundException.class)
            .hasFieldOrPropertyWithValue("code", "RATING_NOT_FOUND");
    }

    @Test
    void updateComment_wrongCustomer_throwsBusinessRuleException() {
        Rating r = new Rating();
        r.setCustomerId(customerId);
        when(ratingRepo.findByOrderId(orderId)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.updateComment(orderId, 9999L, "x"))
            .isInstanceOf(BusinessRuleException.class)
            .hasFieldOrPropertyWithValue("code", "NOT_YOUR_RATING");
    }

    private RatingRepository.RatingStats mockStats(BigDecimal avg, int count) {
        return new RatingRepository.RatingStats() {
            @Override public BigDecimal getAvg() { return avg; }
            @Override public Integer getCount() { return count; }
        };
    }
}
