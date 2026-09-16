package com.shop.delivery.delivery.api.shipper.dto;

import com.shop.delivery.delivery.domain.ShipperState;

/** Trạng thái nhận đơn hiện tại của shipper. `online` = (state == AVAILABLE). */
public record ShipperStatusResponse(ShipperState state, boolean online, boolean busy) {
    public static ShipperStatusResponse of(ShipperState s) {
        return new ShipperStatusResponse(s, s == ShipperState.AVAILABLE, s == ShipperState.BUSY);
    }
}
