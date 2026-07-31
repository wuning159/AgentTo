package com.agentto.rag.evidence;

/**
 * 证据判定结果。
 * - SUFFICIENT: 证据充足，可以生成答案
 * - INSUFFICIENT_EVIDENCE: 证据不足，分数或合格数量未达阈值
 * - NO_RELEVANT_KNOWLEDGE_BASE: 路由阶段已拒绝，无相关知识库
 */
public enum EvidenceDecision {
    SUFFICIENT,
    INSUFFICIENT_EVIDENCE,
    NO_RELEVANT_KNOWLEDGE_BASE
}
