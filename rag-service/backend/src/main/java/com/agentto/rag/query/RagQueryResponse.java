package com.agentto.rag.query;

import java.util.List;

import com.agentto.rag.citation.Citation;

/**
 * 公共 RAG 查询响应。
 *
 * @param decision  查询决策
 * @param answer    答案文本，拒答时为 null
 * @param citations 通过真实性校验的引用列表，拒答时为空
 * @param attempts  一次/二次检索尝试快照
 * @param traceUid  最近一次检索的 Trace ID（无检索时为 null）
 */
public record RagQueryResponse(
        RagQueryDecision decision,
        String answer,
        List<Citation> citations,
        List<QueryAttempt> attempts,
        String traceUid) {

    /**
     * 紧凑构造器：防御性拷贝列表。
     */
    public RagQueryResponse {
        citations = citations == null ? List.of() : List.copyOf(citations);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
    }
}
