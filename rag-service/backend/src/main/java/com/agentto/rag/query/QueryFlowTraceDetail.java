package com.agentto.rag.query;

import java.time.Instant;
import java.util.List;

import com.agentto.rag.observability.ExecutionEvent;

/**
 * 公共查询编排 Trace 详情（管理端检查器视图）。
 *
 * @param flowTraceUid            编排 Trace ID
 * @param clientAppId             调用方应用 ID
 * @param originalQuery           原始查询
 * @param effectiveQuery          生效查询（改写后）
 * @param finalLimit              最终返回数量
 * @param decision                最终决策
 * @param routingDecision         路由决策
 * @param profileShortlist        画像召回 Top N 知识库 ID
 * @param selectedKnowledgeBases  选中知识库及验证分数
 * @param evidenceDecision        最终证据门判定（可空）
 * @param rewriteAttempted        是否尝试过改写
 * @param citationValid           引用校验是否通过（可空）
 * @param failureCode             故障码（可空）
 * @param attemptCount            检索尝试次数
 * @param traceUids               关联检索级 Trace ID
 * @param events                  编排阶段事件
 * @param answerLength            答案长度（可空）
 * @param totalMs                 总耗时（毫秒）
 * @param createdAt               记录时间
 */
public record QueryFlowTraceDetail(
        String flowTraceUid,
        Long clientAppId,
        String originalQuery,
        String effectiveQuery,
        int finalLimit,
        String decision,
        String routingDecision,
        List<Long> profileShortlist,
        List<QueryFlowTrace.KnowledgeBaseSelection> selectedKnowledgeBases,
        String evidenceDecision,
        boolean rewriteAttempted,
        Boolean citationValid,
        String failureCode,
        int attemptCount,
        List<String> traceUids,
        List<ExecutionEvent> events,
        Integer answerLength,
        long totalMs,
        Instant createdAt) {
}
