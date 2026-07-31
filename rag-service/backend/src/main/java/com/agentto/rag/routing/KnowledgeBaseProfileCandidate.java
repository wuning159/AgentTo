package com.agentto.rag.routing;

/**
 * 知识库画像召回候选。
 * 第一阶段路由从画像索引中检索到的知识库候选项，包含知识库 ID、名称和匹配分数。
 */
public record KnowledgeBaseProfileCandidate(
        Long knowledgeBaseId,
        String name,
        double score) {
}
