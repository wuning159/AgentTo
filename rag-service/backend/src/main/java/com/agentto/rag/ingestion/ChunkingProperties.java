package com.agentto.rag.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rag.chunking")
public record ChunkingProperties(int targetChars, int maxChars, int overlapChars) {
}
