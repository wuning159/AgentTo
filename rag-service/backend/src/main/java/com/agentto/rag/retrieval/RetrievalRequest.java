package com.agentto.rag.retrieval;

public record RetrievalRequest(
        String query,
        int keywordLimit,
        int vectorLimit,
        int fusionLimit,
        int rerankLimit,
        int finalLimit,
        Long requesterId) {

    public RetrievalRequest(String query, int keywordLimit, int vectorLimit, int fusionLimit, int rerankLimit,
            int finalLimit) {
        this(query, keywordLimit, vectorLimit, fusionLimit, rerankLimit, finalLimit, null);
    }

    public RetrievalRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("检索问题不能为空");
        }
        if (keywordLimit < 1 || vectorLimit < 1 || fusionLimit < 1 || rerankLimit < 1 || finalLimit < 1) {
            throw new IllegalArgumentException("各阶段返回数量必须大于 0");
        }
    }

    public RetrievalRequest withRequesterId(Long userId) {
        return new RetrievalRequest(query, keywordLimit, vectorLimit, fusionLimit, rerankLimit, finalLimit, userId);
    }
}
