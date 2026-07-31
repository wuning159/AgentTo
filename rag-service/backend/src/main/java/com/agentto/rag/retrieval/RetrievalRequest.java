package com.agentto.rag.retrieval;

import com.agentto.rag.index.SearchScope;

/**
 * 检索请求。
 *
 * @param query        查询文本
 * @param scope        知识库检索范围，null 表示不限定（兼容旧调用）
 * @param keywordLimit 关键词召回数量
 * @param vectorLimit  向量召回数量
 * @param fusionLimit  融合后保留数量
 * @param rerankLimit  精排输入数量
 * @param finalLimit   最终返回数量
 * @param requesterId  发起人 ID（可为 null）
 * @param attemptNo    检索尝试序号，1=首次，2=改写后重试
 */
public record RetrievalRequest(
        String query,
        SearchScope scope,
        int keywordLimit,
        int vectorLimit,
        int fusionLimit,
        int rerankLimit,
        int finalLimit,
        Long requesterId,
        int attemptNo) {

    /**
     * 兼容旧调用：不限定知识库、首次尝试。
     */
    public RetrievalRequest(String query, int keywordLimit, int vectorLimit, int fusionLimit, int rerankLimit,
            int finalLimit) {
        this(query, null, keywordLimit, vectorLimit, fusionLimit, rerankLimit, finalLimit, null, 1);
    }

    /**
     * 兼容旧调用：不限定知识库、首次尝试、携带发起人 ID。
     */
    public RetrievalRequest(String query, int keywordLimit, int vectorLimit, int fusionLimit, int rerankLimit,
            int finalLimit, Long requesterId) {
        this(query, null, keywordLimit, vectorLimit, fusionLimit, rerankLimit, finalLimit, requesterId, 1);
    }

    /**
     * 紧凑构造器：校验必填参数。
     */
    public RetrievalRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("检索问题不能为空");
        }
        if (keywordLimit < 1 || vectorLimit < 1 || fusionLimit < 1 || rerankLimit < 1 || finalLimit < 1) {
            throw new IllegalArgumentException("各阶段返回数量必须大于 0");
        }
        if (attemptNo < 1) {
            throw new IllegalArgumentException("尝试序号必须大于 0");
        }
    }

    /**
     * 带发起人 ID 的副本。
     *
     * @param userId 发起人 ID
     * @return 新请求
     */
    public RetrievalRequest withRequesterId(Long userId) {
        return new RetrievalRequest(query, scope, keywordLimit, vectorLimit, fusionLimit, rerankLimit,
                finalLimit, userId, attemptNo);
    }

    /**
     * 限定知识库检索范围的副本。
     *
     * @param scope 知识库范围
     * @return 新请求
     */
    public RetrievalRequest withScope(SearchScope scope) {
        return new RetrievalRequest(query, scope, keywordLimit, vectorLimit, fusionLimit, rerankLimit,
                finalLimit, requesterId, attemptNo);
    }

    /**
     * 标记检索尝试序号的副本。
     *
     * @param attemptNo 尝试序号
     * @return 新请求
     */
    public RetrievalRequest withAttemptNo(int attemptNo) {
        return new RetrievalRequest(query, scope, keywordLimit, vectorLimit, fusionLimit, rerankLimit,
                finalLimit, requesterId, attemptNo);
    }
}
