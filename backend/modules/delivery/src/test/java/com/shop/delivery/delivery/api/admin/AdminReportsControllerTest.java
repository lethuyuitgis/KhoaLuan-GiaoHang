package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.delivery.api.admin.dto.CancellationReport;
import com.shop.delivery.delivery.api.admin.dto.RevenuePoint;
import com.shop.delivery.delivery.api.admin.dto.TopShipperRow;
import com.shop.delivery.delivery.service.ReportsQueryService;
import com.shop.delivery.shared.api.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminReportsController.class)
@Import({AdminReportsController.class, GlobalExceptionHandler.class, AdminReportsControllerTest.MethodSecurityConfig.class})
class AdminReportsControllerTest {

    @Configuration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityConfig {}

    @Autowired MockMvc mvc;
    @MockBean ReportsQueryService service;

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void revenue_dayBucket_returnsSeries() throws Exception {
        when(service.revenueSeries(any(), any(), eq("day"))).thenReturn(List.of(
            new RevenuePoint(LocalDate.of(2026, 5, 27), new BigDecimal("500000"), 3L)
        ));

        mvc.perform(get("/api/admin/reports/revenue")
                .param("from", "2026-05-27")
                .param("to",   "2026-05-30")
                .param("groupBy", "day"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].date").value("2026-05-27"))
            .andExpect(jsonPath("$[0].revenue").value(500000))
            .andExpect(jsonPath("$[0].orderCount").value(3));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void revenue_invalidGroupBy_returns400() throws Exception {
        mvc.perform(get("/api/admin/reports/revenue")
                .param("from", "2026-05-27")
                .param("to",   "2026-05-30")
                .param("groupBy", "month"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_GROUP_BY"));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void revenue_rangeTooLarge_returns400() throws Exception {
        mvc.perform(get("/api/admin/reports/revenue")
                .param("from", "2026-01-01")
                .param("to",   "2026-05-01")
                .param("groupBy", "day"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DATE_RANGE_TOO_LARGE"));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void revenue_toBeforeFrom_returns400() throws Exception {
        mvc.perform(get("/api/admin/reports/revenue")
                .param("from", "2026-05-30")
                .param("to",   "2026-05-27"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void topShippers_returnsRows() throws Exception {
        when(service.topShippers(any(), any(), eq(10))).thenReturn(List.of(
            new TopShipperRow(2001L, "Nguyen Van A", 12L, new BigDecimal("3000000"), new BigDecimal("4.80"))
        ));

        mvc.perform(get("/api/admin/reports/top-shippers")
                .param("from", "2026-05-01")
                .param("to",   "2026-05-30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].shipperId").value(2001))
            .andExpect(jsonPath("$[0].name").value("Nguyen Van A"))
            .andExpect(jsonPath("$[0].deliveredCount").value(12))
            .andExpect(jsonPath("$[0].revenueGenerated").value(3000000))
            .andExpect(jsonPath("$[0].ratingAvg").value(4.80));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void topShippers_invalidLimit_returns400() throws Exception {
        mvc.perform(get("/api/admin/reports/top-shippers")
                .param("from", "2026-05-01")
                .param("to",   "2026-05-30")
                .param("limit", "999"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_LIMIT"));
    }

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void cancellation_returnsRateAndReasons() throws Exception {
        when(service.cancellation(any(), any())).thenReturn(
            new CancellationReport(100L, 7L, 0.07, List.of(
                new CancellationReport.ReasonCount("Khách huỷ", 5L),
                new CancellationReport.ReasonCount("Sai địa chỉ", 2L)
            ))
        );

        mvc.perform(get("/api/admin/reports/cancellation")
                .param("from", "2026-05-01")
                .param("to",   "2026-05-30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalOrders").value(100))
            .andExpect(jsonPath("$.cancelledCount").value(7))
            .andExpect(jsonPath("$.cancelRate").value(0.07))
            .andExpect(jsonPath("$.byReason[0].reason").value("Khách huỷ"))
            .andExpect(jsonPath("$.byReason[0].count").value(5));
    }

    @Test
    void anyEndpoint_unauthenticated_returns401or403() throws Exception {
        mvc.perform(get("/api/admin/reports/cancellation")
                .param("from", "2026-05-01")
                .param("to",   "2026-05-30"))
            .andExpect(result -> {
                int sc = result.getResponse().getStatus();
                if (sc != 401 && sc != 403) {
                    throw new AssertionError("expected 401 or 403, got " + sc);
                }
            });
    }
}
