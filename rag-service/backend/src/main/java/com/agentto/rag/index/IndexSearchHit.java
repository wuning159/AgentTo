package com.agentto.rag.index;

import java.util.Map;

public record IndexSearchHit(
        String chunkId,
        String content,
        String title,
        Long documentId,
        Long versionId,
        int ordinal,
        double score,
        Map<String, String> metadata) {
}
