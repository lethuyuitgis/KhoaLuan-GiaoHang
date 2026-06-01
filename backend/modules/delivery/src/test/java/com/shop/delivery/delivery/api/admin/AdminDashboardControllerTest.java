package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.delivery.api.admin.dto.DashboardSummary;
import com.shop.delivery.delivery.api.admin.dto.RevenuePoint;
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
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDashboardController.class)
@Import({AdminDashboardController.class, GlobalExceptionHandler.class, AdminDashboardControllerTest.MethodSecurityConfig.class})
class AdminDashboardControllerTest {

    @Configuration
    @EnableMethodSecurity(prePostEnabled = true)
    static class MethodSecurityConfig {}

    @Autowired MockMvc mvc;
    @MockBean ReportsQueryService service;

    @Test
    @WithMockUser(roles = "SHOP_OWNER")
    void summary_returnsJson() throws Exception {
        DashboardSummary expected = new DashboardSummary(
            new DashboardSummary.OrdersTodaySummary(7L,
                Map.of("PENDING", 2L, "DELIVERED", 5L)),
            new BigDecimal("1500000"),
            3L,
            12L,
            List.of(
                new RevenuePoint(LocalDate.of(2026, 5, 26), new BigDecimal("100000"), 1L),
                new RevenuePoint(LocalDate.of(2026, 5, 27), BigDecimal.ZERO, 0L)
            )
        );
        when(service.dashboardSummary()).thenReturn(expected);

        mvc.perform(get("/api/admin/dashboard/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ordersToday.total").value(7))
            .andExpect(jsonPath("$.ordersToday.byStatus.PENDING").value(2))
            .andExpect(jsonPath("$.revenueToday").value(1500000))
            .andExpect(jsonPath("$.activeShippers").value(3))
            .andExpect(jsonPath("$.newCustomersToday").value(12))
            .andExpect(jsonPath("$.revenueLast7Days[0].date").value("2026-05-26"))
            .andExpect(jsonPath("$.revenueLast7Days[0].revenue").value(100000))
            .andExpect(jsonPath("$.revenueLast7Days[1].revenue").value(0));
    }

    @Test
    void summary_unauthenticated_returns401or403() throws Exception {
        mvc.perform(get("/api/admin/dashboard/summary"))
            .andExpect(result -> {
                int sc = result.getResponse().getStatus();
                if (sc != 401 && sc != 403) {
                    throw new AssertionError("expected 401 or 403, got " + sc);
                }
            });
    }
}
