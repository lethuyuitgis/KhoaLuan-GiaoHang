package com.shop.delivery.delivery.api.admin;

import com.shop.delivery.delivery.api.admin.dto.DashboardSummary;
import com.shop.delivery.delivery.service.ReportsQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class AdminDashboardController {

    private final ReportsQueryService service;

    public AdminDashboardController(ReportsQueryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return service.dashboardSummary();
    }
}
