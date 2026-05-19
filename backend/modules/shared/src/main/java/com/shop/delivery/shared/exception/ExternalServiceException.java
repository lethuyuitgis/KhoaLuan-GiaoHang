package com.shop.delivery.shared.exception;

public class ExternalServiceException extends DomainException {
    public ExternalServiceException(String code, String message) {
        super(code, message);
    }
    public ExternalServiceException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
