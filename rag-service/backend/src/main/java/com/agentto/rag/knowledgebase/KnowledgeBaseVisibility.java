package com.agentto.rag.knowledgebase;

/**
 * 知识库可见性枚举。
 * PRIVATE：仅所有者可访问。
 * SHARED：可被显式授权的调用方访问。
 */
public enum KnowledgeBaseVisibility {
    PRIVATE,
    SHARED
}
