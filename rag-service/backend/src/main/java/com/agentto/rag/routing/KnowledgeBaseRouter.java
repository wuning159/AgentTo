package com.agentto.rag.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.agentto.rag.embedding.EmbeddingService;
import com.agentto.rag.index.ChunkIndex;
import com.agentto.rag.index.IndexSearchHit;
import com.agentto.rag.index.SearchScope;
import com.agentto.rag.knowledgebase.KnowledgeBaseAccessService;

/**
 * 两阶段动态多知识库路由器。
 *
 * 第一阶段：画像召回
 *   - 将查询文本转为向量
 *   - 在知识库画像索引中检索 Top N 候选，过滤为调用方可访问的知识库
 *
 * 第二阶段：内容验证
 *   - 对每个候选知识库进行小规模受限向量检索
 *   - 计算 verificationScore = top1 * 0.7 + top2 * 0.3
 *   - 仅保留分数 >= 阈值的知识库，按分数降序选择 Top M
 *   - 没有合格项时返回 NO_RELEVANT_KNOWLEDGE_BASE
 */
@Service
public class KnowledgeBaseRouter {

    private static final double TOP1_WEIGHT = 0.7;
    private static final double TOP2_WEIGHT = 0.3;

    private final KnowledgeBaseAccessService accessService;
    private final KnowledgeBaseProfileIndex profileIndex;
    private final ChunkIndex chunkIndex;
    private final EmbeddingService embeddingService;
    private final RoutingProperties properties;

    public KnowledgeBaseRouter(KnowledgeBaseAccessService accessService,
            KnowledgeBaseProfileIndex profileIndex,
            ChunkIndex chunkIndex,
            EmbeddingService embeddingService,
            RoutingProperties properties) {
        this.accessService = accessService;
        this.profileIndex = profileIndex;
        this.chunkIndex = chunkIndex;
        this.embeddingService = embeddingService;
        this.properties = properties;
    }

    /**
     * 执行两阶段路由。
     *
     * @param clientAppId 调用方应用 ID，用于 ACL 过滤
     * @param query      查询文本
     * @return 路由结果
     */
    public KnowledgeBaseRoute route(Long clientAppId, String query) {
        // 获取可访问的知识库 ID 集合
        Set<Long> accessibleIds = accessService.accessibleKnowledgeBaseIds(clientAppId);
        if (accessibleIds.isEmpty()) {
            return new KnowledgeBaseRoute(RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE,
                    List.of(), List.of(), Map.of());
        }

        // 将查询文本转为向量
        List<float[]> embeddings = embeddingService.embed(List.of(query));
        if (embeddings.isEmpty()) {
            return new KnowledgeBaseRoute(RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE,
                    List.of(), List.of(), Map.of());
        }
        float[] queryVector = embeddings.get(0);

        // 第一阶段：画像召回
        List<KnowledgeBaseProfileCandidate> candidates = profileIndex.search(
                queryVector, accessibleIds, properties.getProfileLimit());
        if (candidates.isEmpty()) {
            return new KnowledgeBaseRoute(RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE,
                    List.of(), List.of(), Map.of());
        }

        List<Long> profileShortlist = candidates.stream()
                .map(KnowledgeBaseProfileCandidate::knowledgeBaseId)
                .toList();

        // 第二阶段：内容验证
        Map<Long, Double> verificationScores = new LinkedHashMap<>();
        for (Long kbId : profileShortlist) {
            double score = verifyKnowledgeBase(queryVector, kbId);
            if (score >= properties.getVerificationThreshold()) {
                verificationScores.put(kbId, score);
            }
        }

        if (verificationScores.isEmpty()) {
            // 没有知识库达到验证阈值
            return new KnowledgeBaseRoute(RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE,
                    profileShortlist, List.of(), Map.of());
        }

        // 按分数降序选择 Top M
        List<Long> selected = new ArrayList<>(verificationScores.keySet());
        selected.sort(Comparator.comparingDouble(verificationScores::get).reversed());
        if (selected.size() > properties.getSelectedLimit()) {
            selected = selected.subList(0, properties.getSelectedLimit());
        }

        // 只保留选中知识库的分数
        Map<Long, Double> selectedScores = new LinkedHashMap<>();
        for (Long kbId : selected) {
            selectedScores.put(kbId, verificationScores.get(kbId));
        }

        return new KnowledgeBaseRoute(RoutingDecision.ROUTED,
                profileShortlist, List.copyOf(selected), Map.copyOf(selectedScores));
    }

    /**
     * 对单个知识库进行内容验证。
     * 执行小规模受限向量检索，计算 verificationScore = top1 * 0.7 + top2 * 0.3。
     */
    private double verifyKnowledgeBase(float[] queryVector, Long kbId) {
        SearchScope scope = new SearchScope(Set.of(kbId));
        List<IndexSearchHit> hits = chunkIndex.vectorSearch(queryVector, scope,
                properties.getVerificationPerKbLimit());
        if (hits.isEmpty()) {
            return 0.0;
        }
        double top1 = hits.get(0).score();
        double top2 = hits.size() >= 2 ? hits.get(1).score() : 0.0;
        return top1 * TOP1_WEIGHT + top2 * TOP2_WEIGHT;
    }
}
