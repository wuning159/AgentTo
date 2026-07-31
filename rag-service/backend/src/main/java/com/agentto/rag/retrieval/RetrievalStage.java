package com.agentto.rag.retrieval;

public enum RetrievalStage {
    PREPROCESS,
    KEYWORD,
    EMBEDDING,
    VECTOR,
    FUSION,
    DEDUPE,
    RERANK,
    COMPLETE
}
