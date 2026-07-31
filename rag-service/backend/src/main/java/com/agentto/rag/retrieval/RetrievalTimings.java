package com.agentto.rag.retrieval;

public record RetrievalTimings(
        long embeddingMs,
        long keywordMs,
        long vectorMs,
        long fusionMs,
        long rerankMs,
        long totalMs) {
}
