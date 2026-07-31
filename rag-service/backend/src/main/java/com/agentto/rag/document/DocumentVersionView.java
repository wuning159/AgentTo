package com.agentto.rag.document;

import java.time.Instant;

public record DocumentVersionView(
        Long id,
        int versionNo,
        String filename,
        String contentType,
        long fileSize,
        String sha256,
        String processingStatus,
        int chunkCount,
        String indexVersion,
        Instant createdAt) {
}
