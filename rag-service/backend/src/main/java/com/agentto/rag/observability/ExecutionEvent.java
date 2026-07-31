package com.agentto.rag.observability;

public record ExecutionEvent(
        String stage,
        String status,
        String startedAt,
        String finishedAt,
        Long elapsedMs,
        TechnicalStageDetail detail) {
}
