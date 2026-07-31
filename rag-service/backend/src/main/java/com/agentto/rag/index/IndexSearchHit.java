package com.agentto.rag.index;

import java.util.Map;

/**
 * 索引检索命中结果。
 * 包含知识库 ID，用于验证检索结果不超出 SearchScope 范围。
 */
public record IndexSearchHit(
        String chunkId,
        String content,
        String title,
        Long documentId,
        Long versionId,
        Long knowledgeBaseId,
        int ordinal,
        double score,
        Map<String, String> metadata) {
}
