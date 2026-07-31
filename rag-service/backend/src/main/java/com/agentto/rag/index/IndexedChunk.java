package com.agentto.rag.index;

import java.util.Map;

public record IndexedChunk(
        String chunkId,
        Long documentId,
        Long versionId,
        int ordinal,
        String title,
        String content,
        Map<String, String> metadata,
        float[] embedding) {
}
