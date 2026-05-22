package com.shop.delivery.payment.api.dto;

/**
 * Return-URL handler decision: where to redirect the browser + a flag for
 * whether the underlying payment was successful. No DB side-effects in this
 * codepath — IPN is the source of truth.
 */
public record ReturnRedirect(String redirectUrl, boolean success) {}
