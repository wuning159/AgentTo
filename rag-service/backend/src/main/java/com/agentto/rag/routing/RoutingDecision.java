package com.agentto.rag.routing;

/**
 * 路由决策结果。
 * - ROUTED: 成功匹配到至少一个相关知识库
 * - NO_RELEVANT_KNOWLEDGE_BASE: 两阶段路由后没有任何知识库达到验证阈值
 */
public enum RoutingDecision {
    ROUTED,
    NO_RELEVANT_KNOWLEDGE_BASE
}
