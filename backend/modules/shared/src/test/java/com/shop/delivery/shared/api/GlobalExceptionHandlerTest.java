package com.shop.delivery.shared.api;

import com.shop.delivery.shared.exception.BusinessRuleException;
import com.shop.delivery.shared.exception.ConflictException;
import com.shop.delivery.shared.exception.ExternalServiceException;
import com.shop.delivery.shared.exception.NotFoundException;
import com.shop.delivery.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundShouldReturn404() {
        ResponseEntity<ApiError> resp = handler.handleNotFound(
            new NotFoundException("ORDER_NOT_FOUND", "Đơn không tồn tại"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().code()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(resp.getBody().message()).isEqualTo("Đơn không tồn tại");
        assertThat(resp.getBody().traceId()).isNotBlank();
    }

    @Test
    void validationShouldReturn400() {
        ResponseEntity<ApiError> resp = handler.handleValidation(
            new ValidationException("INVALID_PHONE", "SĐT sai định dạng"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("INVALID_PHONE");
    }

    @Test
    void businessRuleShouldReturn422() {
        ResponseEntity<ApiError> resp = handler.handleBusinessRule(
            new BusinessRuleException("INVALID_STATUS_TRANSITION", "Không thể chuyển DELIVERED sang CANCELLED"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().code()).isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void conflictShouldReturn409() {
        ResponseEntity<ApiError> resp = handler.handleConflict(
            new ConflictException("VERSION_CONFLICT", "Optimistic lock"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void externalServiceShouldReturn502() {
        ResponseEntity<ApiError> resp = handler.handleExternalService(
            new ExternalServiceException("VNPAY_DOWN", "VNPay không phản hồi"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void unknownExceptionShouldReturn500() {
        ResponseEntity<ApiError> resp = handler.handleUnknown(new RuntimeException("oops"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().code()).isEqualTo("INTERNAL_ERROR");
        // Don't leak internal message to client
        assertThat(resp.getBody().message()).isNotEqualTo("oops");
        assertThat(resp.getBody().traceId()).isNotBlank();
    }
}
