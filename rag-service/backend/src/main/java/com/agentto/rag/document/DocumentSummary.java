package com.agentto.rag.document;

import java.time.Instant;

public record DocumentSummary(
        Long id,
        String name,
        String category,
        String sourceType,
        String status,
        Long currentVersionId,
        Instant createdAt,
        Instant updatedAt) {
}
