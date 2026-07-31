package com.agentto.rag.evaluation;

import java.util.List;

import com.agentto.rag.query.RagQueryDecision;

/**
 * 单条评测用例。
 *
 * <p>JSONL 每行固定结构，例如：
 * <pre>
 * {"id":"route-finance-001","clientAppId":10,"query":"预算怎么审批",
 *  "expectedKbIds":[101],"expectedChunkIds":["finance-approval-1"],
 *  "expectedDecision":"ANSWERED"}
 * </pre>
 *
 * @param id              用例唯一 ID
 * @param clientAppId     客户端应用 ID
 * @param query           查询文本
 * @param expectedKbIds   期望命中的知识库 ID，空表示无相关知识库
 * @param expectedChunkIds 期望命中的文档块 ID，空表示不期望具体文档
 * @param expectedDecision 期望的查询决策
 * @param expectsRewrite  是否期望通过查询改写恢复（改写命中场景）
 */
public record EvaluationCase(
        String id,
        Long clientAppId,
        String query,
        List<Long> expectedKbIds,
        List<String> expectedChunkIds,
        RagQueryDecision expectedDecision,
        Boolean expectsRewrite) {

    /**
     * 紧凑构造器：校验必填字段并防御性拷贝列表。
     */
    public EvaluationCase {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("评测用例 ID 不能为空");
        }
        if (clientAppId == null) {
            throw new IllegalArgumentException("clientAppId 不能为空");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("查询不能为空");
        }
        if (expectedDecision == null) {
            throw new IllegalArgumentException("期望决策不能为空");
        }
        expectedKbIds = expectedKbIds == null ? List.of() : List.copyOf(expectedKbIds);
        expectedChunkIds = expectedChunkIds == null ? List.of() : List.copyOf(expectedChunkIds);
        expectsRewrite = expectsRewrite == null ? false : expectsRewrite;
    }
}
