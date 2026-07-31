package com.agentto.rag.retrieval;

import java.time.Instant;
import java.util.List;

import com.agentto.rag.observability.ExecutionReport;

public record QueryTraceDetail(
        String traceUid,
        String query,
        String retrievalMode,
        String fallbackReason,
        int resultCount,
        RetrievalLimits limits,
        int rankConstant,
        int deduplicatedCount,
        RetrievalTimings timings,
        ExecutionReport executionReport,
        List<QueryTraceCandidate> candidates,
        Instant createdAt) {
}
