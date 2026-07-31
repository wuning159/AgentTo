package com.agentto.rag.ingestion;

import java.time.Instant;
import java.util.List;

public record IngestionJobView(
        Long id,
        Long documentId,
        Long versionId,
        String status,
        String currentStage,
        int attemptNo,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        List<IngestionStageView> stages) {
}
