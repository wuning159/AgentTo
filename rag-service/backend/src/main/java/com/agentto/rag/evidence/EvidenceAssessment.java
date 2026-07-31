package com.agentto.rag.evidence;

/**
 * 证据评估结果。
 *
 * @param decision                 判定结果
 * @param topScore                 最高候选分数
 * @param qualifyingEvidenceCount  达到分数阈值的候选数量
 * @param reason                   判定原因说明，标记分数来源和降级情况
 */
public record EvidenceAssessment(
        EvidenceDecision decision,
        double topScore,
        int qualifyingEvidenceCount,
        String reason) {
}
