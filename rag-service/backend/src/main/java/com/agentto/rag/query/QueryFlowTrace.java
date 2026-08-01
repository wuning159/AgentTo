package com.agentto.rag.query;

import java.util.List;

import com.agentto.rag.evaluation.RagFailureCode;
import com.agentto.rag.evidence.EvidenceDecision;
import com.agentto.rag.observability.ExecutionEvent;
import com.agentto.rag.routing.RoutingDecision;

/**
 * 公共查询编排 Trace 快照。
 *
 * <p>一次公共 RAG 查询的完整诊断记录，由编排服务在查询结束时构造并交给记录器持久化。
 *
 * @param clientAppId           调用方应用 ID
 * @param originalQuery         原始查询文本
 * @param effectiveQuery        生效查询文本（改写后，未改写时为原始查询）
 * @param finalLimit            最终返回数量
 * @param decision              最终决策
 * @param routingDecision       路由决策
 * @param profileShortlist      第一阶段画像召回的 Top N 知识库 ID
 * @param selectedKnowledgeBases 第二阶段选中的知识库及其验证分数
 * @param evidenceDecision      最终证据门判定（路由拒答时为 null）
 * @param rewriteAttempted      是否尝试过查询改写
 * @param citationValid         引用校验是否通过（未生成答案时为 null）
 * @param failureCode           可确定的故障码（正常回答时为 null）
 * @param attemptCount          检索尝试次数（1 或 2）
 * @param traceUids             关联的检索级 Trace ID 列表
 * @param events                编排阶段事件列表
 * @param answerLength          答案文本长度（拒答时为 0）
 * @param totalMs               编排总耗时（毫秒）
 */
public record QueryFlowTrace(
        Long clientAppId,
        String originalQuery,
        String effectiveQuery,
        int finalLimit,
        RagQueryDecision decision,
        RoutingDecision routingDecision,
        List<Long> profileShortlist,
        List<KnowledgeBaseSelection> selectedKnowledgeBases,
        EvidenceDecision evidenceDecision,
        boolean rewriteAttempted,
        Boolean citationValid,
        RagFailureCode failureCode,
        int attemptCount,
        List<String> traceUids,
        List<ExecutionEvent> events,
        int answerLength,
        long totalMs) {

    /**
     * 紧凑构造器：防御性拷贝列表。
     */
    public QueryFlowTrace {
        profileShortlist = profileShortlist == null ? List.of() : List.copyOf(profileShortlist);
        selectedKnowledgeBases = selectedKnowledgeBases == null ? List.of()
                : List.copyOf(selectedKnowledgeBases);
        traceUids = traceUids == null ? List.of() : List.copyOf(traceUids);
        events = events == null ? List.of() : List.copyOf(events);
    }

    /**
     * 选中的知识库及验证分数。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param score           内容验证分数
     */
    public record KnowledgeBaseSelection(Long knowledgeBaseId, double score) {
    }
}
