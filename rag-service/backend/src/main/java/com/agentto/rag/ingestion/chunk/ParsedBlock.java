package com.agentto.rag.ingestion.chunk;

import java.util.Map;

public record ParsedBlock(String title, String content, Map<String, String> metadata) {

    public ParsedBlock {
        title = title == null ? "" : title.trim();
        content = content == null ? "" : content.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
