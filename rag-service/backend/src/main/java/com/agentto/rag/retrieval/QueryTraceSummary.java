package com.agentto.rag.retrieval;

import java.time.Instant;

public record QueryTraceSummary(
        String traceUid,
        String query,
        String retrievalMode,
        String fallbackReason,
        long totalMs,
        int resultCount,
        Instant createdAt) {
}
