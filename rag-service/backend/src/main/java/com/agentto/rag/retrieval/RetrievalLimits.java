package com.agentto.rag.retrieval;

public record RetrievalLimits(
        int keywordLimit,
        int vectorLimit,
        int fusionLimit,
        int rerankLimit,
        int finalLimit) {

    public static RetrievalLimits from(RetrievalRequest request) {
        return new RetrievalLimits(request.keywordLimit(), request.vectorLimit(), request.fusionLimit(),
                request.rerankLimit(), request.finalLimit());
    }
}
