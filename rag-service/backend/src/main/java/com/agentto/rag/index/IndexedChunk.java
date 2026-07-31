package com.agentto.rag.index;

import java.util.Map;

/**
 * 已索引的切片数据。
 * 包含知识库 ID，用于按知识库过滤检索结果。
 */
public record IndexedChunk(
        String chunkId,
        Long documentId,
        Long versionId,
        Long knowledgeBaseId,
        int ordinal,
        String title,
        String content,
        Map<String, String> metadata,
        float[] embedding) {
}
