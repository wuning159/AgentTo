package com.agentto.rag.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * SearchScope 契约测试。
 * 验证检索范围的基本约束和不可变性。
 */
class ChunkIndexContractTest {

    /**
     * SearchScope 必须冻结知识库 ID 集合，外部修改不影响范围。
     */
    @Test
    void searchScopeIsImmutable() {
        Set<Long> mutable = new java.util.HashSet<>(Set.of(101L, 102L));
        SearchScope scope = new SearchScope(mutable);

        // 修改原始集合不应影响 scope
        mutable.add(999L);
        assertThat(scope.knowledgeBaseIds()).containsExactlyInAnyOrder(101L, 102L);
        assertThat(scope.knowledgeBaseIds()).doesNotContain(999L);
    }

    /**
     * 空的知识库 ID 集合应抛出异常。
     */
    @Test
    void searchScopeRejectsEmptyKnowledgeBaseIds() {
        assertThatThrownBy(() -> new SearchScope(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不可为空");
    }

    /**
     * null 的知识库 ID 集合应抛出异常。
     */
    @Test
    void searchScopeRejectsNullKnowledgeBaseIds() {
        assertThatThrownBy(() -> new SearchScope(null))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * IndexedChunk 必须携带 knowledgeBaseId 字段。
     */
    @Test
    void indexedChunkMustCarryKnowledgeBaseId() {
        IndexedChunk chunk = new IndexedChunk("chunk-1", 2L, 7L, 101L, 0, "标题", "内容", java.util.Map.of(), new float[]{1.0f});
        assertThat(chunk.knowledgeBaseId()).isEqualTo(101L);
    }

    /**
     * IndexSearchHit 必须携带 knowledgeBaseId 字段。
     */
    @Test
    void indexSearchHitMustCarryKnowledgeBaseId() {
        IndexSearchHit hit = new IndexSearchHit("chunk-1", "内容", "标题", 2L, 7L, 101L, 0, 8.5, java.util.Map.of());
        assertThat(hit.knowledgeBaseId()).isEqualTo(101L);
    }
}
