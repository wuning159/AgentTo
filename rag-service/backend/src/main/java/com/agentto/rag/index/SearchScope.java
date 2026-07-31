package com.agentto.rag.index;

import java.util.Set;

/**
 * 检索范围，限定搜索只在指定知识库内进行。
 * 知识库 ID 集合不可为空，确保不会意外跨知识库检索。
 */
public record SearchScope(Set<Long> knowledgeBaseIds) {

    /**
     * 紧凑构造函数，冻结集合并验证非空。
     */
    public SearchScope {
        knowledgeBaseIds = Set.copyOf(knowledgeBaseIds);
        if (knowledgeBaseIds.isEmpty()) {
            throw new IllegalArgumentException("knowledgeBaseIds 不可为空");
        }
    }
}
