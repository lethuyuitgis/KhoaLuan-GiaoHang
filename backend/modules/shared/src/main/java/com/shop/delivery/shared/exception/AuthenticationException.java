package com.shop.delivery.shared.exception;

public class AuthenticationException extends DomainException {
    public AuthenticationException(String code, String message) {
        super(code, message);
    }
}
