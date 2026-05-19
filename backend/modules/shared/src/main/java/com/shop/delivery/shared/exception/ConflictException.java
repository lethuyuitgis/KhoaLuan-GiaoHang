package com.shop.delivery.shared.exception;

public class ConflictException extends DomainException {
    public ConflictException(String code, String message) {
        super(code, message);
    }
}
