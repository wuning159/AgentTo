package com.agentto.rag.query;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agentto.rag.common.api.ApiResponse;
import com.agentto.rag.common.api.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 公共查询编排 Trace 管理控制器。
 *
 * <p>供技术管理员查看公共 RAG 查询的完整编排记录，
 * 包含路由画像、选中知识库、证据门、改写、引用校验和阶段事件。
 *
 * 端点：
 * GET /api/admin/query-flow/traces                最近编排 Trace 列表
 * GET /api/admin/query-flow/traces/{flowTraceUid} 编排 Trace 详情
 */
@RestController
@RequestMapping("/api/admin/query-flow")
public class QueryFlowAdminController {

    private final QueryFlowTraceService traceService;

    public QueryFlowAdminController(QueryFlowTraceService traceService) {
        this.traceService = traceService;
    }

    /**
     * 查询最近的公共查询编排 Trace 列表。
     */
    @GetMapping("/traces")
    public ApiResponse<List<QueryFlowTraceSummary>> traces(
            @RequestParam(defaultValue = "20") int limit, HttpServletRequest request) {
        return ApiResponse.ok(traceService.recent(limit), TraceIdFilter.current(request));
    }

    /**
     * 查询公共查询编排 Trace 详情。
     */
    @GetMapping("/traces/{flowTraceUid}")
    public ApiResponse<QueryFlowTraceDetail> trace(@PathVariable String flowTraceUid,
            HttpServletRequest request) {
        return ApiResponse.ok(traceService.detail(flowTraceUid), TraceIdFilter.current(request));
    }
}
