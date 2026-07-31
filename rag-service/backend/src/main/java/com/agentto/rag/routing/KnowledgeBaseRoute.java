package com.agentto.rag.routing;

import java.util.List;
import java.util.Map;

/**
 * 两阶段知识库路由结果。
 *
 * @param decision              路由决策（ROUTED 或 NO_RELEVANT_KNOWLEDGE_BASE）
 * @param profileShortlist      第一阶段画像召回的 Top N 知识库 ID 列表
 * @param selectedKnowledgeBaseIds 第二阶段内容验证后选中的知识库 ID 列表
 * @param verificationScores    每个选中知识库的验证分数
 */
public record KnowledgeBaseRoute(
        RoutingDecision decision,
        List<Long> profileShortlist,
        List<Long> selectedKnowledgeBaseIds,
        Map<Long, Double> verificationScores) {
}
