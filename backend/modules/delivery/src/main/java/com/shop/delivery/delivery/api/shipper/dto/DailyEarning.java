package com.shop.delivery.delivery.api.shipper.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyEarning(LocalDate date, int ordersCount, BigDecimal commission) {}
