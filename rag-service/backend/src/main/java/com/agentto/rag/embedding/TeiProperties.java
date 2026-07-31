package com.agentto.rag.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rag.tei")
public record TeiProperties(
        boolean embeddingEnabled,
        String embeddingUrl,
        boolean rerankEnabled,
        String rerankUrl,
        int connectTimeoutSeconds,
        int readTimeoutSeconds) {
}
