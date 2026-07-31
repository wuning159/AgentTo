package com.agentto.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import com.agentto.rag.citation.Citation;
import com.agentto.rag.citation.CitationValidator;
import com.agentto.rag.citation.GeneratedAnswer;
import com.agentto.rag.evidence.EvidenceGate;
import com.agentto.rag.evidence.EvidencePolicyProperties;
import com.agentto.rag.query.AnswerGenerator;
import com.agentto.rag.query.RagQueryDecision;
import com.agentto.rag.query.RagQueryResponse;
import com.agentto.rag.query.RagQueryService;
import com.agentto.rag.retrieval.DedupeStatus;
import com.agentto.rag.retrieval.HybridRetrievalService;
import com.agentto.rag.retrieval.RetrievalCandidate;
import com.agentto.rag.retrieval.RetrievalRequest;
import com.agentto.rag.retrieval.RetrievalResponse;
import com.agentto.rag.retrieval.RetrievalTimings;
import com.agentto.rag.rewrite.QueryRewriter;
import com.agentto.rag.routing.KnowledgeBaseRoute;
import com.agentto.rag.routing.KnowledgeBaseRouter;
import com.agentto.rag.routing.RoutingDecision;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RAG 回归评测测试：验证基线数据集结构、理想结果全过发布门禁、
 * 故障污染导致门禁失败，以及评测执行器端到端冒烟。
 */
class RagRegressionTest {

    private EvaluationDatasetLoader loader;
    private EvaluationMetricsCalculator calculator;

    @BeforeEach
    void setUp() {
        loader = new EvaluationDatasetLoader(new ObjectMapper(), new DefaultResourceLoader());
        calculator = new EvaluationMetricsCalculator();
    }

    /** 基线数据集结构：30 条、ID 唯一、五类场景分布正确 */
    @Test
    void baselineDatasetIsWellFormed() {
        List<EvaluationCase> cases = loader.loadDefault();

        assertThat(cases).hasSize(30);
        assertThat(cases).extracting(EvaluationCase::id).doesNotHaveDuplicates();
        assertThat(cases).filteredOn(testCase -> testCase.expectedDecision() == RagQueryDecision.ANSWERED)
                .hasSize(15);
        assertThat(cases).filteredOn(
                testCase -> testCase.expectedDecision() == RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE)
                .hasSize(5);
        assertThat(cases).filteredOn(
                testCase -> testCase.expectedDecision() == RagQueryDecision.INSUFFICIENT_EVIDENCE)
                .hasSize(5);
        assertThat(cases).filteredOn(
                testCase -> testCase.expectedDecision() == RagQueryDecision.INVALID_CITATION)
                .hasSize(5);
    }

    /** 理想结果集：所有指标达标，全部门禁通过 */
    @Test
    void passesAllReleaseGatesWithIdealResults() {
        List<EvaluationCase> cases = loader.loadDefault();
        List<EvaluationResult> results = new ArrayList<>();
        for (EvaluationCase testCase : cases) {
            results.add(new EvaluationResult(testCase, idealResponse(testCase), 10));
        }

        EvaluationMetrics metrics = calculator.calculate(results);
        List<String> gateFailures = RagEvaluationRunner.validateGates(metrics);

        assertThat(metrics.routeRecallAt3()).isEqualTo(1.0);
        assertThat(metrics.retrievalHitAt10()).isEqualTo(1.0);
        assertThat(metrics.mrr()).isEqualTo(1.0);
        assertThat(metrics.refusalPrecision()).isEqualTo(1.0);
        assertThat(metrics.refusalRecall()).isEqualTo(1.0);
        assertThat(metrics.citationValidity()).isEqualTo(1.0);
        assertThat(metrics.rewriteRecoveryRate()).isEqualTo(1.0);
        assertThat(metrics.failureCounts()).isEmpty();
        assertThat(gateFailures).isEmpty();
        assertThat(new RagEvaluationRunner.EvaluationReport(metrics, gateFailures, "rag-eval/baseline.jsonl")
                .gatesPassed()).isTrue();
    }

    /** 故障污染：应作答的用例被拒答 → 误拒答，拒答精确率/召回率下降，门禁失败 */
    @Test
    void failsGatesWhenRefusalIsWrong() {
        List<EvaluationCase> cases = loader.loadDefault();
        List<EvaluationResult> results = new ArrayList<>();
        for (EvaluationCase testCase : cases) {
            RagQueryResponse response = idealResponse(testCase);
            // 污染一条应作答的用例：改为拒答
            if (testCase.expectedDecision() == RagQueryDecision.ANSWERED && !testCase.expectsRewrite()) {
                response = new RagQueryResponse(RagQueryDecision.INSUFFICIENT_EVIDENCE, null,
                        List.of(), List.of(), "t");
            }
            results.add(new EvaluationResult(testCase, response, 10));
        }

        EvaluationMetrics metrics = calculator.calculate(results);
        List<String> gateFailures = RagEvaluationRunner.validateGates(metrics);

        assertThat(metrics.failureCounts()).containsKey(RagFailureCode.FALSE_REFUSAL);
        assertThat(metrics.refusalPrecision()).isLessThan(1.0);
        assertThat(gateFailures).isNotEmpty()
                .anySatisfy(failure -> assertThat(failure).contains("Refusal"));
    }

    /** 评测执行器冒烟：桩链路执行全部用例，指标可计算，延迟被记录 */
    @Test
    void runnerSmokeTestExecutesAllCases() {
        StubRouter router = new StubRouter(new KnowledgeBaseRoute(RoutingDecision.ROUTED,
                List.of(101L), List.of(101L), Map.of(101L, 0.9)));
        RagQueryService queryService = new RagQueryService(router,
                new AlwaysAnsweredRetrieval(), new EvidenceGate(new EvidencePolicyProperties()),
                new NoOpRewriter(), new AlwaysAnsweredGenerator(), new CitationValidator());
        RagEvaluationRunner runner = new RagEvaluationRunner(loader, calculator, queryService);

        RagEvaluationRunner.EvaluationReport report = runner.run("classpath:rag-eval/baseline.jsonl");

        assertThat(report.metrics().totalCases()).isEqualTo(30);
        assertThat(report.metrics().latencyP50Millis()).isGreaterThanOrEqualTo(0);
        // 桩恒作答：无知识库/证据不足/引用类用例全部答错 → 门禁必然失败（证明门禁链路生效）
        assertThat(report.gateFailures()).isNotEmpty();
        assertThat(report.gatesPassed()).isFalse();
    }

    // --- 辅助方法 ---

    /** 按用例期望构造理想响应（用于门禁达标验证） */
    private RagQueryResponse idealResponse(EvaluationCase testCase) {
        if (testCase.expectedDecision() == RagQueryDecision.ANSWERED) {
            List<Citation> citations = testCase.expectedChunkIds().stream()
                    .map(chunkId -> new Citation(chunkId, "引用片段"))
                    .toList();
            return new RagQueryResponse(RagQueryDecision.ANSWERED, "理想答案", citations, List.of(), "trace-ideal");
        }
        return new RagQueryResponse(testCase.expectedDecision(), null, List.of(), List.of(), null);
    }

    /** 桩路由器：恒返回已路由结果 */
    static final class StubRouter extends KnowledgeBaseRouter {

        private final KnowledgeBaseRoute route;

        StubRouter(KnowledgeBaseRoute route) {
            super(null, null, null, null, null);
            this.route = route;
        }

        @Override
        public KnowledgeBaseRoute route(Long clientAppId, String query) {
            return route;
        }
    }

    /** 桩检索服务：恒返回高分候选（证据充足） */
    static final class AlwaysAnsweredRetrieval extends HybridRetrievalService {

        AlwaysAnsweredRetrieval() {
            super(null, null, null, null);
        }

        @Override
        public RetrievalResponse search(RetrievalRequest request) {
            return new RetrievalResponse("trace-eval",
                    List.of(candidate("chunk-1", "预算审批分为三步。"), candidate("chunk-2", "单笔超五十万需总经理审批。")),
                    null, new RetrievalTimings(0, 0, 0, 0, 0, 0));
        }

        private RetrievalCandidate candidate(String chunkId, String content) {
            return new RetrievalCandidate(chunkId, content, null, null, null, null, Map.of(),
                    null, null, null, null, 0.9, null, 0.9, null, null,
                    null, DedupeStatus.KEPT, null);
        }
    }

    /** 桩改写器：不可用（不重试） */
    static final class NoOpRewriter implements QueryRewriter {

        @Override
        public Optional<String> rewrite(String originalQuery, String failureReason) {
            return Optional.empty();
        }
    }

    /** 桩答案生成器：恒返回带真实引用的答案 */
    static final class AlwaysAnsweredGenerator implements AnswerGenerator {

        @Override
        public GeneratedAnswer generate(String query, List<RetrievalCandidate> evidence) {
            return new GeneratedAnswer("预算审批分为三步。",
                    List.of(new Citation("chunk-1", "预算审批分为三步")));
        }
    }
}
