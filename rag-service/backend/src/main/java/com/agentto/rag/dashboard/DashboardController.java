package com.agentto.rag.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentto.rag.common.api.ApiResponse;
import com.agentto.rag.common.api.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ApiResponse<DashboardOverview> overview(HttpServletRequest request) {
        return ApiResponse.ok(dashboardService.overview(), TraceIdFilter.current(request));
    }
}
