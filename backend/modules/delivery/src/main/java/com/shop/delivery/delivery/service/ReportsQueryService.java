package com.shop.delivery.delivery.service;

import com.shop.delivery.delivery.api.admin.dto.CancellationReport;
import com.shop.delivery.delivery.api.admin.dto.DashboardSummary;
import com.shop.delivery.delivery.api.admin.dto.RevenuePoint;
import com.shop.delivery.delivery.api.admin.dto.TopShipperRow;
import com.shop.delivery.delivery.repository.ReportsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ReportsQueryService {

    private final ReportsRepository repo;

    public ReportsQueryService(ReportsRepository repo) {
        this.repo = repo;
    }

    public DashboardSummary dashboardSummary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;
        for (var row : repo.ordersTodayByStatus()) {
            byStatus.put(row.getStatus(), row.getCnt());
            total += row.getCnt();
        }
        BigDecimal revenue = repo.revenueToday() == null ? BigDecimal.ZERO : repo.revenueToday();
        long activeShippers = repo.activeShippers();
        long newCustomers = repo.newCustomersToday();

        List<RevenuePoint> last7 = repo.revenueLast7Days().stream()
            .map(r -> new RevenuePoint(r.getBucketDate(), r.getRevenue(),
                                        r.getOrderCount() == null ? 0L : r.getOrderCount()))
            .toList();

        return new DashboardSummary(
            new DashboardSummary.OrdersTodaySummary(total, byStatus),
            revenue,
            activeShippers,
            newCustomers,
            last7
        );
    }

    public List<RevenuePoint> revenueSeries(LocalDate from, LocalDate to, String bucket) {
        return repo.revenueSeries(from, to, bucket).stream()
            .map(r -> new RevenuePoint(r.getBucketDate(), r.getRevenue(),
                                        r.getOrderCount() == null ? 0L : r.getOrderCount()))
            .toList();
    }

    public List<TopShipperRow> topShippers(LocalDate from, LocalDate to, int limit) {
        return repo.topShippers(from, to, limit).stream()
            .map(r -> new TopShipperRow(
                r.getShipperId(),
                buildName(r.getFirstName(), r.getLastName(), r.getUsername()),
                r.getDeliveredCount() == null ? 0L : r.getDeliveredCount(),
                r.getRevenueGenerated() == null ? BigDecimal.ZERO : r.getRevenueGenerated(),
                r.getRatingAvg() == null ? BigDecimal.ZERO : r.getRatingAvg()
            ))
            .toList();
    }

    public CancellationReport cancellation(LocalDate from, LocalDate to) {
        var totals = repo.cancellationTotals(from, to);
        long total = totals.getTotalOrders() == null ? 0L : totals.getTotalOrders();
        long cancelled = totals.getCancelledCount() == null ? 0L : totals.getCancelledCount();
        double rate = total == 0 ? 0.0 : (double) cancelled / (double) total;
        List<CancellationReport.ReasonCount> reasons = repo.cancellationByReason(from, to).stream()
            .map(r -> new CancellationReport.ReasonCount(r.getReason(), r.getCnt() == null ? 0L : r.getCnt()))
            .toList();
        return new CancellationReport(total, cancelled, rate, reasons);
    }

    private static String buildName(String first, String last, String username) {
        String f = first == null ? "" : first.trim();
        String l = last  == null ? "" : last.trim();
        String combined = (f + " " + l).trim();
        if (!combined.isEmpty()) return combined;
        return username != null && !username.isBlank() ? "@" + username : "Shipper";
    }
}
