package com.agentto.rag.index;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 禁用的索引实现，当 Elasticsearch 未启用时使用。
 */
@Service
@ConditionalOnProperty(prefix = "rag.elasticsearch", name = "enabled", havingValue = "false")
public class DisabledChunkIndex implements ChunkIndex {
    @Override public void ensureIndex() { throw new IllegalStateException("Elasticsearch 未启用"); }
    @Override public void replaceVersionChunks(Long versionId, List<IndexedChunk> chunks) { throw new IllegalStateException("Elasticsearch 未启用"); }
    @Override public List<IndexSearchHit> keywordSearch(String query, SearchScope scope, int limit) { return List.of(); }
    @Override public List<IndexSearchHit> vectorSearch(float[] queryVector, SearchScope scope, int limit) { return List.of(); }
    @Override @Deprecated public List<IndexSearchHit> keywordSearch(String query, int limit) { return List.of(); }
    @Override @Deprecated public List<IndexSearchHit> vectorSearch(float[] queryVector, int limit) { return List.of(); }
    @Override public void clearAll() { }
    @Override public boolean healthy() { return false; }
    @Override public String indexVersion() { return "disabled"; }
}
