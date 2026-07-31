package com.agentto.rag.retrieval;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agentto.rag.auth.AuthRequestContext;
import com.agentto.rag.common.api.ApiResponse;
import com.agentto.rag.common.api.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/admin/retrieval")
public class RetrievalAdminController {

    private final HybridRetrievalService retrievalService;
    private final QueryTraceService traceService;
    private final RetrievalJobService jobService;

    public RetrievalAdminController(HybridRetrievalService retrievalService, QueryTraceService traceService,
            RetrievalJobService jobService) {
        this.retrievalService = retrievalService;
        this.traceService = traceService;
        this.jobService = jobService;
    }

    @PostMapping("/search")
    public ApiResponse<RetrievalResponse> search(@Valid @RequestBody SearchRequest body, HttpServletRequest request) {
        RetrievalRequest retrieval = toRequest(body, request);
        return ApiResponse.ok(retrievalService.search(retrieval), TraceIdFilter.current(request));
    }

    @PostMapping("/jobs")
    public ApiResponse<RetrievalJobCreated> createJob(@Valid @RequestBody SearchRequest body,
            HttpServletRequest request) {
        String jobUid = jobService.create(toRequest(body, request));
        return ApiResponse.ok(new RetrievalJobCreated(jobUid), TraceIdFilter.current(request));
    }

    @GetMapping("/jobs/{jobUid}")
    public ApiResponse<RetrievalJobSnapshot> job(@PathVariable String jobUid, HttpServletRequest request) {
        return ApiResponse.ok(jobService.get(jobUid), TraceIdFilter.current(request));
    }

    @GetMapping("/traces")
    public ApiResponse<java.util.List<QueryTraceSummary>> traces(@RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        return ApiResponse.ok(traceService.recent(limit), TraceIdFilter.current(request));
    }

    @GetMapping("/traces/{traceUid}")
    public ApiResponse<QueryTraceDetail> trace(@PathVariable String traceUid, HttpServletRequest request) {
        return ApiResponse.ok(traceService.detail(traceUid), TraceIdFilter.current(request));
    }

    private int value(Integer candidate, int fallback) {
        return candidate == null ? fallback : candidate;
    }

    private RetrievalRequest toRequest(SearchRequest body, HttpServletRequest request) {
        return new RetrievalRequest(body.query(), value(body.keywordLimit(), 20),
                value(body.vectorLimit(), 20), value(body.fusionLimit(), 30), value(body.rerankLimit(), 15),
                value(body.finalLimit(), 8), AuthRequestContext.principal(request).userId());
    }

    public record SearchRequest(@NotBlank String query, Integer keywordLimit, Integer vectorLimit,
            Integer fusionLimit, Integer rerankLimit, Integer finalLimit) {
    }
}
