package com.shop.delivery.delivery.api.shipper.dto;

/** Body của POST /api/shipper/me/status — shipper tự bật (true) / tắt (false) nhận đơn. */
public record ShipperStatusRequest(boolean online) {}
