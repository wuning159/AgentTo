package com.agentto.rag.query;

/**
 * 公共 RAG 查询命令。
 *
 * @param clientAppId 调用方应用 ID（由认证拦截器写入请求上下文）
 * @param query       查询文本
 * @param finalLimit  最终返回证据数量
 */
public record RagQueryCommand(Long clientAppId, String query, int finalLimit) {

    /**
     * 紧凑构造器：校验必填参数。
     */
    public RagQueryCommand {
        if (clientAppId == null) {
            throw new IllegalArgumentException("clientAppId 不能为空");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("查询不能为空");
        }
        if (finalLimit < 1) {
            throw new IllegalArgumentException("finalLimit 必须大于 0");
        }
    }
}
