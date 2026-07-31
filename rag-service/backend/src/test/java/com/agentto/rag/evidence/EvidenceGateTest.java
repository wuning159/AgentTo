package com.agentto.rag.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.agentto.rag.retrieval.RetrievalCandidate;
import com.agentto.rag.routing.KnowledgeBaseRoute;
import com.agentto.rag.routing.RoutingDecision;

/**
 * 证据门测试。
 * 验证阈值和最小证据数量边界条件。
 */
class EvidenceGateTest {

    private EvidencePolicyProperties properties;
    private EvidenceGate gate;

    @BeforeEach
    void setUp() {
        properties = new EvidencePolicyProperties();
        gate = new EvidenceGate(properties);
    }

    /** 路由已拒绝时直接返回 NO_RELEVANT_KNOWLEDGE_BASE */
    @Test
    void returnsNoRelevantKnowledgeBaseWhenRouteRejected() {
        KnowledgeBaseRoute route = new KnowledgeBaseRoute(
                RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE, List.of(), List.of(), Map.of());
        List<RetrievalCandidate> candidates = List.of(
                createCandidate("c1", 0.9, 0.8));

        EvidenceAssessment result = gate.assess(route, candidates);

        assertThat(result.decision()).isEqualTo(EvidenceDecision.NO_RELEVANT_KNOWLEDGE_BASE);
        assertThat(result.reason()).contains("路由");
    }

    /** 候选列表为空时返回 INSUFFICIENT_EVIDENCE */
    @Test
    void returnsInsufficientWhenCandidatesEmpty() {
        KnowledgeBaseRoute route = routedRoute();
        EvidenceAssessment result = gate.assess(route, List.of());

        assertThat(result.decision()).isEqualTo(EvidenceDecision.INSUFFICIENT_EVIDENCE);
        assertThat(result.qualifyingEvidenceCount()).isZero();
    }

    /** 候选列表为 null 时也返回 INSUFFICIENT_EVIDENCE */
    @Test
    void returnsInsufficientWhenCandidatesNull() {
        KnowledgeBaseRoute route = routedRoute();
        EvidenceAssessment result = gate.assess(route, null);

        assertThat(result.decision()).isEqualTo(EvidenceDecision.INSUFFICIENT_EVIDENCE);
    }

    /**
     * 参数化测试：验证阈值和最小证据数量。
     * 0.54 低于 0.55 阈值 → INSUFFICIENT
     * 0.55 等于阈值且 2 个合格 → SUFFICIENT
     */
    @ParameterizedTest
    @CsvSource({
            "0.54, 1, INSUFFICIENT_EVIDENCE",
            "0.55, 2, SUFFICIENT",
            "0.55, 1, INSUFFICIENT_EVIDENCE",
            "0.90, 3, SUFFICIENT"
    })
    void appliesThresholdAndMinimumEvidenceCount(
            double topScore, int count, EvidenceDecision expected) {
        KnowledgeBaseRoute route = routedRoute();
        List<RetrievalCandidate> candidates = candidates(topScore, count);

        EvidenceAssessment result = gate.assess(route, candidates);

        assertThat(result.decision()).isEqualTo(expected);
    }

    /** 分数恰好等于阈值时通过 */
    @Test
    void scoreExactlyAtThresholdPasses() {
        KnowledgeBaseRoute route = routedRoute();
        List<RetrievalCandidate> candidates = List.of(
                createCandidate("c1", 0.55, 0.55),
                createCandidate("c2", 0.55, 0.55));

        EvidenceAssessment result = gate.assess(route, candidates);

        assertThat(result.decision()).isEqualTo(EvidenceDecision.SUFFICIENT);
        assertThat(result.topScore()).isEqualTo(0.55);
        assertThat(result.qualifyingEvidenceCount()).isEqualTo(2);
    }

    /** 分数恰好低于阈值时拒绝 */
    @Test
    void scoreJustBelowThresholdRejected() {
        KnowledgeBaseRoute route = routedRoute();
        List<RetrievalCandidate> candidates = List.of(
                createCandidate("c1", 0.549, 0.549),
                createCandidate("c2", 0.549, 0.549));

        EvidenceAssessment result = gate.assess(route, candidates);

        assertThat(result.decision()).isEqualTo(EvidenceDecision.INSUFFICIENT_EVIDENCE);
    }

    /** Top 1 合格但合格证据数不足时拒绝 */
    @Test
    void topScoreQualifiedButCountInsufficientRejected() {
        KnowledgeBaseRoute route = routedRoute();
        List<RetrievalCandidate> candidates = List.of(
                createCandidate("c1", 0.90, 0.85),
                createCandidate("c2", 0.30, 0.25));

        EvidenceAssessment result = gate.assess(route, candidates);

        assertThat(result.decision()).isEqualTo(EvidenceDecision.INSUFFICIENT_EVIDENCE);
        assertThat(result.topScore()).isEqualTo(0.90);
        assertThat(result.qualifyingEvidenceCount()).isEqualTo(1);
        assertThat(result.reason()).contains("不足");
    }

    /** 没有 rerank 时使用 rrf 分数，reason 标记降级 */
    @Test
    void usesRrfScoreWhenRerankNotAvailable() {
        KnowledgeBaseRoute route = routedRoute();
        List<RetrievalCandidate> candidates = List.of(
                RetrievalCandidate.keyword("c1", "内容1", 0.6, 1),
                RetrievalCandidate.keyword("c2", "内容2", 0.6, 2));

        EvidenceAssessment result = gate.assess(route, candidates);

        // keyword-only candidates have no rrfScore or rerankScore → score is 0
        assertThat(result.decision()).isEqualTo(EvidenceDecision.INSUFFICIENT_EVIDENCE);
        assertThat(result.reason()).contains("rrf");
    }

    /** 有 rerank 分数时优先使用 */
    @Test
    void prefersRerankScoreWhenAvailable() {
        KnowledgeBaseRoute route = routedRoute();
        List<RetrievalCandidate> candidates = List.of(
                createCandidate("c1", 0.90, 0.85),
                createCandidate("c2", 0.80, 0.75));

        EvidenceAssessment result = gate.assess(route, candidates);

        assertThat(result.decision()).isEqualTo(EvidenceDecision.SUFFICIENT);
        assertThat(result.reason()).contains("rerank");
        assertThat(result.topScore()).isEqualTo(0.90);
    }

    // --- 辅助方法 ---

    /** 构造已路由的 KnowledgeBaseRoute */
    private KnowledgeBaseRoute routedRoute() {
        return new KnowledgeBaseRoute(RoutingDecision.ROUTED,
                List.of(1L), List.of(1L), Map.of(1L, 0.8));
    }

    /** 构造指定分数和数量的候选列表，使用 rerankScore */
    private List<RetrievalCandidate> candidates(double topScore, int count) {
        List<RetrievalCandidate> result = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(createCandidate("c" + i, topScore, topScore * 0.9));
        }
        return result;
    }

    /** 构造带 rerank 和 rrf 分数的候选 */
    private RetrievalCandidate createCandidate(String chunkId, double rerankScore, double rrfScore) {
        return new RetrievalCandidate(chunkId, "内容", "标题", null, null, null, Map.of(),
                null, null, null, null, rrfScore, null, rerankScore, null, null,
                null, com.agentto.rag.retrieval.DedupeStatus.KEPT, null);
    }
}
