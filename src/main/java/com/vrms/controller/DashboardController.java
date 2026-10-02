package com.vrms.controller;

import com.vrms.dto.DashboardStats;
import com.vrms.model.AuditLog;
import com.vrms.service.AuditService;
import com.vrms.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuditService auditService;

    public DashboardController(DashboardService dashboardService, AuditService auditService) {
        this.dashboardService = dashboardService;
        this.auditService = auditService;
    }

    @GetMapping("/api/dashboard")
    public DashboardStats dashboard() {
        return dashboardService.stats();
    }

    @GetMapping("/api/logs")
    public List<AuditLog> logs(@RequestParam(defaultValue = "200") int limit) {
        return auditService.latest(Math.max(1, Math.min(limit, 1000)));
    }
}
