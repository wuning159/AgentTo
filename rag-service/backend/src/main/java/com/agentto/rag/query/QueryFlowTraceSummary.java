package com.agentto.rag.query;

import java.time.Instant;

/**
 * 公共查询编排 Trace 摘要（管理端列表视图）。
 *
 * @param flowTraceUid  编排 Trace ID
 * @param clientAppId   调用方应用 ID
 * @param originalQuery 原始查询
 * @param decision      最终决策
 * @param attemptCount  检索尝试次数
 * @param totalMs       编排总耗时（毫秒）
 * @param createdAt     记录时间
 */
public record QueryFlowTraceSummary(
        String flowTraceUid,
        Long clientAppId,
        String originalQuery,
        String decision,
        int attemptCount,
        long totalMs,
        Instant createdAt) {
}
