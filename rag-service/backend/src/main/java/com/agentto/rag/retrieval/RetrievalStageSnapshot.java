package com.agentto.rag.retrieval;

public record RetrievalStageSnapshot(
        RetrievalStage stage,
        RetrievalStageStatus status,
        Long elapsedMs,
        Integer itemCount,
        String message) {
}
