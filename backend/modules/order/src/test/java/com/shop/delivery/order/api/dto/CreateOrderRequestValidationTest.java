package com.shop.delivery.order.api.dto;

import com.shop.delivery.order.domain.PaymentMethod;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Bean Validation constraints declared on {@link CreateOrderRequest}.
 * These guard against malformed requests reaching {@link com.shop.delivery.order.service.OrderService}.
 */
class CreateOrderRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void boot() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void shutdown() {
        if (factory != null) factory.close();
    }

    @Test
    void validHanoiAddressShouldHaveNoViolations() {
        CreateOrderRequest req = newRequest(
            "123 Lê Lợi, Hoàn Kiếm, Hà Nội",
            new BigDecimal("21.0285"),
            new BigDecimal("105.8542")
        );
        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }

    @Test
    void latOutsideVietnamShouldFailDecimalMin() {
        // Equator (lat=0) — south of Vietnam's south-most bound (~8.0)
        CreateOrderRequest req = newRequest(
            "123 Lê Lợi, Hoàn Kiếm, Hà Nội",
            new BigDecimal("0.0"),
            new BigDecimal("105.8542")
        );
        Set<ConstraintViolation<CreateOrderRequest>> v = validator.validate(req);
        assertThat(v).anyMatch(cv -> cv.getPropertyPath().toString().equals("deliveryLat"));
    }

    @Test
    void lngOutsideVietnamShouldFailDecimalMax() {
        // Singapore-ish longitude ~115 — east of Vietnam's east-most bound (~110)
        CreateOrderRequest req = newRequest(
            "123 Lê Lợi, Hoàn Kiếm, Hà Nội",
            new BigDecimal("21.0285"),
            new BigDecimal("115.0")
        );
        Set<ConstraintViolation<CreateOrderRequest>> v = validator.validate(req);
        assertThat(v).anyMatch(cv -> cv.getPropertyPath().toString().equals("deliveryLng"));
    }

    @Test
    void tooShortDeliveryAddressShouldFailSize() {
        CreateOrderRequest req = newRequest(
            "Hà Nội", // < 10 chars
            new BigDecimal("21.0285"),
            new BigDecimal("105.8542")
        );
        Set<ConstraintViolation<CreateOrderRequest>> v = validator.validate(req);
        assertThat(v).anyMatch(cv -> cv.getPropertyPath().toString().equals("deliveryAddress"));
    }

    @Test
    void nullCoordinatesShouldFailNotNull() {
        CreateOrderRequest req = new CreateOrderRequest(
            "Khách",
            "+84900111222",
            "123 Lê Lợi, Hoàn Kiếm, Hà Nội",
            null,
            null,
            List.of(new OrderItemRequest(1L, 1)),
            PaymentMethod.COD,
            null,
            null
        );
        Set<ConstraintViolation<CreateOrderRequest>> v = validator.validate(req);
        assertThat(v).anyMatch(cv -> cv.getPropertyPath().toString().equals("deliveryLat"));
        assertThat(v).anyMatch(cv -> cv.getPropertyPath().toString().equals("deliveryLng"));
    }

    private CreateOrderRequest newRequest(String address, BigDecimal lat, BigDecimal lng) {
        return new CreateOrderRequest(
            "Khách",
            "+84900111222",
            address,
            lat,
            lng,
            List.of(new OrderItemRequest(1L, 1)),
            PaymentMethod.COD,
            null,
            null
        );
    }
}
