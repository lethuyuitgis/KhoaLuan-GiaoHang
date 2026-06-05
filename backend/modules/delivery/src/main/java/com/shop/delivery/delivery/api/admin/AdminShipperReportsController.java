package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.delivery.repository.ShipperLedgerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminShipperReportsController {

    private final ShipperLedgerRepository repo;

    public AdminShipperReportsController(ShipperLedgerRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/shipper-earnings")
    public List<EarningsBucket> earnings(
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(defaultValue = "shipper") String groupBy) {

        if (!"shipper".equals(groupBy) && !"day".equals(groupBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_GROUP_BY");
        }
        List<Object[]> rows = "shipper".equals(groupBy)
            ? repo.aggregateByShipper(from, to)
            : repo.aggregateByDay(from, to);
        return rows.stream()
            .map(r -> new EarningsBucket(
                String.valueOf(r[0]),
                ((Number) r[1]).intValue(),
                (BigDecimal) r[2]))
            .toList();
    }

    public record EarningsBucket(String groupKey, int ordersCount, BigDecimal commission) {}
}
