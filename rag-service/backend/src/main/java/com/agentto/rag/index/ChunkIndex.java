package com.agentto.rag.index;

import java.util.List;

/**
 * 切片索引存储抽象接口。
 * 提供按知识库范围过滤的检索能力。
 * 旧的无 scope 方法已标记 @Deprecated，仅用于测试迁移期，
 * 生产调用必须使用带 SearchScope 的新方法。
 */
public interface ChunkIndex {

    /** 确保索引存在 */
    void ensureIndex();

    /** 替换指定版本的全部切片 */
    void replaceVersionChunks(Long versionId, List<IndexedChunk> chunks);

    /**
     * 关键词检索（带知识库范围过滤）。
     *
     * @param query 查询文本
     * @param scope 知识库检索范围
     * @param limit 最大返回数量
     * @return 命中结果列表
     */
    List<IndexSearchHit> keywordSearch(String query, SearchScope scope, int limit);

    /**
     * 向量检索（带知识库范围过滤）。
     *
     * @param queryVector 查询向量
     * @param scope      知识库检索范围
     * @param limit      最大返回数量
     * @return 命中结果列表
     */
    List<IndexSearchHit> vectorSearch(float[] queryVector, SearchScope scope, int limit);

    /**
     * @deprecated 生产代码必须使用带 SearchScope 的方法。
     * 仅用于测试迁移期。
     */
    @Deprecated
    List<IndexSearchHit> keywordSearch(String query, int limit);

    /**
     * @deprecated 生产代码必须使用带 SearchScope 的方法。
     * 仅用于测试迁移期。
     */
    @Deprecated
    List<IndexSearchHit> vectorSearch(float[] queryVector, int limit);

    /** 清空索引 */
    default void clearAll() {
        throw new UnsupportedOperationException("当前索引不支持清理");
    }

    /** 健康检查 */
    boolean healthy();

    /** 索引版本标识 */
    String indexVersion();
}
