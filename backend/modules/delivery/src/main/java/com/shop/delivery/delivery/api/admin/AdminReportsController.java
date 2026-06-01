package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.delivery.api.admin.dto.CancellationReport;
import com.shop.delivery.delivery.api.admin.dto.RevenuePoint;
import com.shop.delivery.delivery.api.admin.dto.TopShipperRow;
import com.shop.delivery.delivery.service.ReportsQueryService;
import com.shop.delivery.shared.exception.ValidationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminReportsController {

    private static final Set<String> ALLOWED_BUCKETS = Set.of("day", "week");
    private static final int MAX_RANGE_DAYS = 90;
    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int MAX_TOP_LIMIT = 50;

    private final ReportsQueryService service;

    public AdminReportsController(ReportsQueryService service) {
        this.service = service;
    }

    @GetMapping("/revenue")
    public List<RevenuePoint> revenue(
        @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam("to")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(value = "groupBy", defaultValue = "day") String groupBy
    ) {
        requireRange(from, to);
        requireBucket(groupBy);
        return service.revenueSeries(from, to, groupBy);
    }

    @GetMapping("/top-shippers")
    public List<TopShipperRow> topShippers(
        @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam("to")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        requireRange(from, to);
        if (limit < 1 || limit > MAX_TOP_LIMIT) {
            throw new ValidationException("INVALID_LIMIT", "limit phải nằm trong khoảng 1..50");
        }
        return service.topShippers(from, to, limit);
    }

    @GetMapping("/cancellation")
    public CancellationReport cancellation(
        @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam("to")   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        requireRange(from, to);
        return service.cancellation(from, to);
    }

    // ---------- validation helpers ----------

    private void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ValidationException("MISSING_DATES", "Thiếu tham số from/to");
        }
        if (to.isBefore(from)) {
            throw new ValidationException("INVALID_RANGE", "Ngày kết thúc phải sau ngày bắt đầu");
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days > MAX_RANGE_DAYS) {
            throw new ValidationException("DATE_RANGE_TOO_LARGE",
                "Phạm vi tối đa " + MAX_RANGE_DAYS + " ngày");
        }
    }

    private void requireBucket(String bucket) {
        if (bucket == null || !ALLOWED_BUCKETS.contains(bucket)) {
            throw new ValidationException("INVALID_GROUP_BY",
                "groupBy phải là 'day' hoặc 'week'");
        }
    }
}
