package com.agentto.rag.query;

import com.agentto.rag.evidence.EvidenceDecision;

/**
 * 单次检索尝试的快照，用于诊断和响应输出。
 *
 * @param attemptNo       尝试序号，1=首次，2=改写后重试
 * @param query           本次实际使用的查询（第二次为改写后的查询）
 * @param evidenceDecision 证据门判定结果
 * @param evidenceCount   证据候选数量
 * @param note            证据门判定说明
 */
public record QueryAttempt(
        int attemptNo,
        String query,
        EvidenceDecision evidenceDecision,
        int evidenceCount,
        String note) {
}
