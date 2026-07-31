package com.agentto.rag.ingestion.chunk;

import java.util.Map;

public record RagChunk(String content, Map<String, String> metadata, int ordinal) {

    public RagChunk {
        content = content == null ? "" : content.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
