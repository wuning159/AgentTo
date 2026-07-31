package com.agentto.rag.query;

/**
 * 公共 RAG 查询决策。
 *
 * - ANSWERED: 正常回答，带引用
 * - NO_RELEVANT_KNOWLEDGE_BASE: 路由阶段无相关知识库
 * - INSUFFICIENT_EVIDENCE: 一次/二次检索证据不足（或模型判定证据不足返回空答案）
 * - INVALID_CITATION: 生成答案的引用未通过真实性校验
 * - GENERATION_UNAVAILABLE: 未配置 ChatModel，答案生成不可用
 */
public enum RagQueryDecision {
    ANSWERED,
    NO_RELEVANT_KNOWLEDGE_BASE,
    INSUFFICIENT_EVIDENCE,
    INVALID_CITATION,
    GENERATION_UNAVAILABLE
}
