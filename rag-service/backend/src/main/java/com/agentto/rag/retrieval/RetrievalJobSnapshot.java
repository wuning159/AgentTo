package com.agentto.rag.retrieval;

import java.time.Instant;
import java.util.List;

public record RetrievalJobSnapshot(
        String jobUid,
        RetrievalJobStatus status,
        RetrievalStage currentStage,
        List<RetrievalStageSnapshot> stages,
        RetrievalResponse result,
        String error,
        Instant createdAt,
        Instant completedAt) {
}
