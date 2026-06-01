package com.shop.delivery.delivery.repository;

import com.shop.delivery.delivery.DeliveryTestConfig;
import com.shop.delivery.delivery.entity.Rating;
import com.shop.delivery.delivery.support.DeliveryTestcontainerBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(DeliveryTestConfig.class)
@ActiveProfiles("test")
class RatingRepositoryIT extends DeliveryTestcontainerBase {

    @Autowired
    RatingRepository ratingRepo;

    @Autowired
    TestEntityManager em;

    @Test
    void shouldRoundtripRating() {
        UUID orderId = UUID.randomUUID();
        Rating r = new Rating();
        r.setOrderId(orderId);
        r.setCustomerId(1001L);
        r.setShipperId(2001L);
        r.setStars((short) 5);
        r.setComment("Great shipper!");

        Rating saved = ratingRepo.saveAndFlush(r);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(ratingRepo.existsByOrderId(orderId)).isTrue();
        assertThat(ratingRepo.findByOrderId(orderId)).isPresent()
            .get().extracting(Rating::getStars).isEqualTo((short) 5);
    }

    @Test
    void shouldRejectDuplicateOrderId() {
        UUID orderId = UUID.randomUUID();
        Rating first = newRating(orderId, 1001L, 2001L, (short) 4);
        ratingRepo.saveAndFlush(first);

        Rating dup = newRating(orderId, 1002L, 2001L, (short) 3);
        assertThatThrownBy(() -> ratingRepo.saveAndFlush(dup))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectStarsBelowOne() {
        Rating r = newRating(UUID.randomUUID(), 1001L, 2001L, (short) 0);
        assertThatThrownBy(() -> ratingRepo.saveAndFlush(r))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectStarsAboveFive() {
        Rating r = newRating(UUID.randomUUID(), 1001L, 2001L, (short) 6);
        assertThatThrownBy(() -> ratingRepo.saveAndFlush(r))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aggregateForShipper_returnsAvgAndCount() {
        Long shipperId = 9999L;
        ratingRepo.saveAndFlush(newRating(UUID.randomUUID(), 1001L, shipperId, (short) 5));
        ratingRepo.saveAndFlush(newRating(UUID.randomUUID(), 1002L, shipperId, (short) 3));
        ratingRepo.saveAndFlush(newRating(UUID.randomUUID(), 1003L, shipperId, (short) 4));

        RatingRepository.RatingStats stats = ratingRepo.aggregateForShipper(shipperId);
        assertThat(stats.getCount()).isEqualTo(3);
        // (5 + 3 + 4) / 3 = 4.00
        assertThat(stats.getAvg()).isEqualByComparingTo(new BigDecimal("4.0000000000000000"));
    }

    @Test
    void aggregateForShipper_returnsZeroForUnknownShipper() {
        RatingRepository.RatingStats stats = ratingRepo.aggregateForShipper(0L);
        assertThat(stats.getCount()).isEqualTo(0);
        assertThat(stats.getAvg()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private Rating newRating(UUID orderId, Long customerId, Long shipperId, short stars) {
        Rating r = new Rating();
        r.setOrderId(orderId);
        r.setCustomerId(customerId);
        r.setShipperId(shipperId);
        r.setStars(stars);
        return r;
    }
}
