package com.shop.delivery.shared.exception;

public class ValidationException extends DomainException {
    public ValidationException(String code, String message) {
        super(code, message);
    }
}
