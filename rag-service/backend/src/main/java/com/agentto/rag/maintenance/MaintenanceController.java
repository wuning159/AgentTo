package com.agentto.rag.maintenance;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentto.rag.common.api.ApiResponse;
import com.agentto.rag.common.api.BusinessException;
import com.agentto.rag.common.api.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/maintenance")
public class MaintenanceController {

    public static final String CONFIRMATION = "DELETE-RAG-TEST-DATA";
    private final TestDataCleanupService cleanupService;

    public MaintenanceController(TestDataCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @PostMapping("/cleanup")
    public ApiResponse<CleanupResult> cleanup(@RequestBody CleanupRequest body, HttpServletRequest request) {
        if (body == null || !CONFIRMATION.equals(body.confirmation())) {
            throw new BusinessException("CLEANUP_CONFIRMATION_REQUIRED", "清理确认词不正确", HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(cleanupService.cleanup(), TraceIdFilter.current(request));
    }

    public record CleanupRequest(String confirmation) {
    }
}
