package com.agentto.rag.index;

import java.util.List;

public interface ChunkIndex {
    void ensureIndex();
    void replaceVersionChunks(Long versionId, List<IndexedChunk> chunks);
    List<IndexSearchHit> keywordSearch(String query, int limit);
    List<IndexSearchHit> vectorSearch(float[] queryVector, int limit);
    default void clearAll() { throw new UnsupportedOperationException("当前索引不支持清理"); }
    boolean healthy();
    String indexVersion();
}
