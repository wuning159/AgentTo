package com.agentto.rag.routing;

import java.util.List;
import java.util.Set;

/**
 * 知识库画像索引接口。
 * 第一阶段路由使用此接口进行画像向量检索，返回与查询向量最相似的知识库候选列表。
 * 检索结果必须过滤为调用方可访问的知识库 ID 集合。
 */
public interface KnowledgeBaseProfileIndex {

    /**
     * 按查询向量检索知识库画像候选。
     *
     * @param queryVector             查询文本的向量表示
     * @param accessibleKnowledgeBaseIds 调用方可访问的知识库 ID 集合
     * @param limit                   最大返回数量
     * @return 按相似度降序排列的知识库画像候选列表
     */
    List<KnowledgeBaseProfileCandidate> search(float[] queryVector, Set<Long> accessibleKnowledgeBaseIds, int limit);

    /**
     * 索引版本标识。
     */
    String indexVersion();

    /**
     * 健康检查。
     */
    boolean healthy();
}
