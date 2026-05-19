package com.shop.delivery.shared.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionTest {

    @Test
    void notFoundShouldExposeCodeAndMessage() {
        NotFoundException ex = new NotFoundException("USER_NOT_FOUND", "User 123 không tồn tại");
        assertThat(ex.getCode()).isEqualTo("USER_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("User 123 không tồn tại");
        assertThat(ex).isInstanceOf(DomainException.class);
    }

    @Test
    void validationException() {
        ValidationException ex = new ValidationException("INVALID_PHONE", "SĐT không đúng định dạng");
        assertThat(ex.getCode()).isEqualTo("INVALID_PHONE");
    }

    @Test
    void businessRuleException() {
        BusinessRuleException ex = new BusinessRuleException("ORDER_NOT_CANCELLABLE", "Đơn đã giao");
        assertThat(ex.getCode()).isEqualTo("ORDER_NOT_CANCELLABLE");
    }

    @Test
    void conflictException() {
        ConflictException ex = new ConflictException("VERSION_CONFLICT", "Optimistic lock");
        assertThat(ex.getCode()).isEqualTo("VERSION_CONFLICT");
    }

    @Test
    void externalServiceException() {
        Throwable cause = new RuntimeException("Connection refused");
        ExternalServiceException ex = new ExternalServiceException("VNPAY_DOWN", "VNPay không phản hồi", cause);
        assertThat(ex.getCode()).isEqualTo("VNPAY_DOWN");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
