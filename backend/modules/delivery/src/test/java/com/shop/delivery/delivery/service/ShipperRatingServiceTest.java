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
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipperRatingServiceTest {

    @Mock ShipperRatingRepository ratingRepo;
    @Mock OrderRepository orderRepo;
    @Mock DeliveryAssignmentRepository assignmentRepo;
    @Mock EntityManager em;
    @Mock Query query;

    @InjectMocks ShipperRatingService service;

    private UUID orderId;
    private Long customerId;
    private Long shipperId;
    private Order order;
    private DeliveryAssignment assignment;

    @BeforeEach
    void setUp() {
        // @PersistenceContext is injected via field, not constructor — set it directly.
        ReflectionTestUtils.setField(service, "em", em);

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
    }

    @Test
    void rateCustomer_happyPath_insertsRatingAndRecomputesCustomerStats() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(assignmentRepo.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        ShipperRatingRepository.CustomerRatingStats stats = mockStats(new BigDecimal("4.33"), 3);
        when(ratingRepo.aggregateForCustomer(customerId)).thenReturn(stats);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);

        ShipperRating result = service.rateCustomer(orderId, shipperId, 4, "Khách dễ tìm");

        ArgumentCaptor<ShipperRating> captor = ArgumentCaptor.forClass(ShipperRating.class);
        verify(ratingRepo).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(orderId);
        assertThat(captor.getValue().getShipperId()).isEqualTo(shipperId);
        assertThat(captor.getValue().getCustomerId()).isEqualTo(customerId);
        assertThat(captor.getValue().getStars()).isEqualTo((short) 4);
        assertThat(captor.getValue().getComment()).isEqualTo("Khách dễ tìm");

        // Native UPDATE was executed against telegram_user
        verify(em).createNativeQuery(anyString());
        verify(query).executeUpdate();
        assertThat(result).isSameAs(captor.getValue());
    }

    @Test
    void rateCustomer_orderNotFound_throwsNotFoundException() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rateCustomer(orderId, shipperId, 5, null))
            .isInstanceOf(NotFoundException.class)
            .hasFieldOrPropertyWithValue("code", "ORDER_NOT_FOUND");

        verify(ratingRepo, never()).saveAndFlush(any());
    }

    @Test
    void rateCustomer_orderNotDelivered_throwsBusinessRuleException() {
        order.setStatus(OrderStatus.DELIVERING);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.rateCustomer(orderId, shipperId, 5, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasFieldOrPropertyWithValue("code", "ORDER_NOT_RATEABLE");

        verify(ratingRepo, never()).saveAndFlush(any());
    }

    @Test
    void rateCustomer_wrongShipper_throwsBusinessRuleException() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(assignmentRepo.findByOrderId(orderId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.rateCustomer(orderId, 9999L, 5, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasFieldOrPropertyWithValue("code", "NOT_YOUR_ORDER");

        verify(ratingRepo, never()).saveAndFlush(any());
    }

    @Test
    void rateCustomer_alreadyRated_translatesIntegrityViolationToConflict() {
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(assignmentRepo.findByOrderId(orderId)).thenReturn(Optional.of(assignment));
        when(ratingRepo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("UNIQUE"));

        assertThatThrownBy(() -> service.rateCustomer(orderId, shipperId, 5, null))
            .isInstanceOf(ConflictException.class)
            .hasFieldOrPropertyWithValue("code", "ALREADY_RATED");

        verify(em, never()).createNativeQuery(anyString());
    }

    @Test
    void rateCustomer_starsOutOfRange_throwsBusinessRuleException() {
        assertThatThrownBy(() -> service.rateCustomer(orderId, shipperId, 0, null))
            .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.rateCustomer(orderId, shipperId, 6, null))
            .isInstanceOf(BusinessRuleException.class);

        verify(orderRepo, never()).findById(any());
    }

    private ShipperRatingRepository.CustomerRatingStats mockStats(BigDecimal avg, int count) {
        return new ShipperRatingRepository.CustomerRatingStats() {
            @Override public BigDecimal getAvg() { return avg; }
            @Override public Integer getCount() { return count; }
        };
    }
}
