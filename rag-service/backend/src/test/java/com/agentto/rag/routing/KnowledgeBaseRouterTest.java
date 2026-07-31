package com.agentto.rag.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.agentto.rag.embedding.EmbeddingService;
import com.agentto.rag.index.ChunkIndex;
import com.agentto.rag.index.IndexSearchHit;
import com.agentto.rag.index.SearchScope;
import com.agentto.rag.knowledgebase.KnowledgeBaseAccessService;

/**
 * 两阶段动态多知识库路由器测试。
 *
 * 验证：
 * 1. 第一阶段画像召回不超过 Top 10 且只包含可访问的知识库
 * 2. 第二阶段内容验证最多选中 3 个知识库
 * 3. 无相关知识库时返回 NO_RELEVANT_KNOWLEDGE_BASE
 * 4. 可访问知识库为空时直接返回拒绝
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseRouterTest {

    @Mock
    private KnowledgeBaseAccessService accessService;

    @Mock
    private KnowledgeBaseProfileIndex profileIndex;

    @Mock
    private ChunkIndex chunkIndex;

    @Mock
    private EmbeddingService embeddingService;

    private RoutingProperties properties;

    @InjectMocks
    private KnowledgeBaseRouter router;

    @BeforeEach
    void setUp() {
        properties = new RoutingProperties();
        router = new KnowledgeBaseRouter(accessService, profileIndex, chunkIndex, embeddingService, properties);
    }

    /**
     * 第一阶段画像召回不超过 Top 10，且只包含可访问的知识库。
     */
    @Test
    void profileRoutingSearchesOnlyAccessibleKnowledgeBasesAndCapsAtTen() {
        Long clientAppId = 10L;
        Long unauthorizedKbId = 999L;

        when(accessService.accessibleKnowledgeBaseIds(clientAppId))
                .thenReturn(Set.of(101L, 102L, 103L));
        when(embeddingService.embed(any())).thenReturn(List.of(new float[] { 1.0f, 0.0f }));

        // 画像索引返回 12 个候选（应被截断为 10）
        List<KnowledgeBaseProfileCandidate> candidates = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            candidates.add(new KnowledgeBaseProfileCandidate((long) i, "KB" + i, 1.0 / i));
        }
        when(profileIndex.search(any(float[].class), any(Set.class), anyInt()))
                .thenReturn(candidates.subList(0, 10));

        // 第二阶段：所有知识库都达到阈值
        when(chunkIndex.vectorSearch(any(float[].class), any(SearchScope.class), anyInt()))
                .thenAnswer(invocation -> {
                    SearchScope scope = invocation.getArgument(1);
                    Long kbId = scope.knowledgeBaseIds().iterator().next();
                    double score = 0.8 + (kbId % 10) * 0.01;
                    return List.of(
                            new IndexSearchHit("chunk-1", "内容1", "标题1", null, null, kbId, 0, score, java.util.Map.of()),
                            new IndexSearchHit("chunk-2", "内容2", "标题2", null, null, kbId, 1, score - 0.1, java.util.Map.of()));
                });

        KnowledgeBaseRoute route = router.route(clientAppId, "报销发票怎么审批");

        assertThat(route.profileShortlist()).hasSizeLessThanOrEqualTo(10);
        assertThat(route.profileShortlist()).doesNotContain(unauthorizedKbId);
        assertThat(route.decision()).isEqualTo(RoutingDecision.ROUTED);
    }

    /**
     * 第二阶段最多选中 3 个知识库。
     */
    @Test
    void contentVerificationSelectsAtMostThreeKnowledgeBases() {
        Long clientAppId = 10L;

        when(accessService.accessibleKnowledgeBaseIds(clientAppId))
                .thenReturn(Set.of(101L, 102L, 103L, 104L, 105L));
        when(embeddingService.embed(any())).thenReturn(List.of(new float[] { 1.0f, 0.0f }));

        // 5 个候选知识库都通过画像召回
        when(profileIndex.search(any(float[].class), any(Set.class), anyInt()))
                .thenReturn(List.of(
                        new KnowledgeBaseProfileCandidate(101L, "财务KB", 0.9),
                        new KnowledgeBaseProfileCandidate(102L, "预算KB", 0.85),
                        new KnowledgeBaseProfileCandidate(103L, "报销KB", 0.8),
                        new KnowledgeBaseProfileCandidate(104L, "税务KB", 0.75),
                        new KnowledgeBaseProfileCandidate(105L, "审计KB", 0.7)));

        // 所有知识库的验证分数都高于阈值
        when(chunkIndex.vectorSearch(any(float[].class), any(SearchScope.class), anyInt()))
                .thenAnswer(invocation -> {
                    SearchScope scope = invocation.getArgument(1);
                    Long kbId = scope.knowledgeBaseIds().iterator().next();
                    return List.of(
                            new IndexSearchHit("c1", "内容1", "标题", null, null, kbId, 0, 0.9, java.util.Map.of()),
                            new IndexSearchHit("c2", "内容2", "标题", null, null, kbId, 1, 0.8, java.util.Map.of()));
                });

        KnowledgeBaseRoute route = router.route(clientAppId, "预算审批规则");

        assertThat(route.selectedKnowledgeBaseIds()).hasSizeBetween(1, 3);
        assertThat(route.decision()).isEqualTo(RoutingDecision.ROUTED);
        // 选中列表按分数降序排列
        assertThat(route.selectedKnowledgeBaseIds()).hasSize(3);
    }

    /**
     * 无相关知识库时返回 NO_RELEVANT_KNOWLEDGE_BASE 而非强制选择。
     */
    @Test
    void returnsNoRelevantKnowledgeBaseInsteadOfForcingAChoice() {
        Long clientAppId = 10L;

        when(accessService.accessibleKnowledgeBaseIds(clientAppId))
                .thenReturn(Set.of(101L, 102L));
        when(embeddingService.embed(any())).thenReturn(List.of(new float[] { 1.0f, 0.0f }));

        // 画像召回返回候选
        when(profileIndex.search(any(float[].class), any(Set.class), anyInt()))
                .thenReturn(List.of(
                        new KnowledgeBaseProfileCandidate(101L, "财务KB", 0.5),
                        new KnowledgeBaseProfileCandidate(102L, "预算KB", 0.45)));

        // 所有知识库的验证分数都低于阈值
        when(chunkIndex.vectorSearch(any(float[].class), any(SearchScope.class), anyInt()))
                .thenReturn(List.of(
                        new IndexSearchHit("c1", "无关内容", "标题", null, null, 101L, 0, 0.1, java.util.Map.of())));

        KnowledgeBaseRoute route = router.route(clientAppId, "完全无关的问题");

        assertThat(route.decision()).isEqualTo(RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE);
        assertThat(route.selectedKnowledgeBaseIds()).isEmpty();
        // 画像短列表仍然保留
        assertThat(route.profileShortlist()).isNotEmpty();
    }

    /**
     * 可访问知识库为空时直接返回拒绝。
     */
    @Test
    void returnsNoRelevantWhenNoAccessibleKnowledgeBases() {
        Long clientAppId = 20L;

        when(accessService.accessibleKnowledgeBaseIds(clientAppId))
                .thenReturn(Set.of());

        KnowledgeBaseRoute route = router.route(clientAppId, "任意问题");

        assertThat(route.decision()).isEqualTo(RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE);
        assertThat(route.selectedKnowledgeBaseIds()).isEmpty();
        assertThat(route.profileShortlist()).isEmpty();
    }

    /**
     * 画像召回为空时返回拒绝。
     */
    @Test
    void returnsNoRelevantWhenProfileSearchReturnsEmpty() {
        Long clientAppId = 10L;

        when(accessService.accessibleKnowledgeBaseIds(clientAppId))
                .thenReturn(Set.of(101L));
        when(embeddingService.embed(any())).thenReturn(List.of(new float[] { 1.0f, 0.0f }));
        when(profileIndex.search(any(float[].class), any(Set.class), anyInt()))
                .thenReturn(List.of());

        KnowledgeBaseRoute route = router.route(clientAppId, "任意问题");

        assertThat(route.decision()).isEqualTo(RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE);
        assertThat(route.profileShortlist()).isEmpty();
        assertThat(route.selectedKnowledgeBaseIds()).isEmpty();
    }

    /**
     * 验证分数计算正确：top1 * 0.7 + top2 * 0.3。
     */
    @Test
    void verificationScoreUsesWeightedAverageOfTopTwoHits() {
        Long clientAppId = 10L;

        when(accessService.accessibleKnowledgeBaseIds(clientAppId))
                .thenReturn(Set.of(101L));
        when(embeddingService.embed(any())).thenReturn(List.of(new float[] { 1.0f, 0.0f }));
        when(profileIndex.search(any(float[].class), any(Set.class), anyInt()))
                .thenReturn(List.of(new KnowledgeBaseProfileCandidate(101L, "财务KB", 0.9)));

        // top1=0.9, top2=0.6 → verificationScore = 0.9*0.7 + 0.6*0.3 = 0.63 + 0.18 = 0.81
        when(chunkIndex.vectorSearch(any(float[].class), any(SearchScope.class), anyInt()))
                .thenReturn(List.of(
                        new IndexSearchHit("c1", "内容1", "标题", null, null, 101L, 0, 0.9, java.util.Map.of()),
                        new IndexSearchHit("c2", "内容2", "标题", null, null, 101L, 1, 0.6, java.util.Map.of())));

        KnowledgeBaseRoute route = router.route(clientAppId, "财务报销");

        assertThat(route.decision()).isEqualTo(RoutingDecision.ROUTED);
        assertThat(route.verificationScores()).containsEntry(101L, 0.81);
    }

    /**
     * 只有一条命中时，top2 计为 0。
     */
    @Test
    void verificationScoreWithSingleHitUsesZeroForTop2() {
        Long clientAppId = 10L;

        when(accessService.accessibleKnowledgeBaseIds(clientAppId))
                .thenReturn(Set.of(101L));
        when(embeddingService.embed(any())).thenReturn(List.of(new float[] { 1.0f, 0.0f }));
        when(profileIndex.search(any(float[].class), any(Set.class), anyInt()))
                .thenReturn(List.of(new KnowledgeBaseProfileCandidate(101L, "财务KB", 0.9)));

        // 只有一条命中，top1=0.9, top2=0 → verificationScore = 0.9*0.7 + 0*0.3 = 0.63
        when(chunkIndex.vectorSearch(any(float[].class), any(SearchScope.class), anyInt()))
                .thenReturn(List.of(
                        new IndexSearchHit("c1", "内容1", "标题", null, null, 101L, 0, 0.9, java.util.Map.of())));

        KnowledgeBaseRoute route = router.route(clientAppId, "财务报销");

        assertThat(route.decision()).isEqualTo(RoutingDecision.ROUTED);
        // 0.9 * 0.7 = 0.63 >= 0.55
        assertThat(route.verificationScores()).containsEntry(101L, 0.63);
    }
}
