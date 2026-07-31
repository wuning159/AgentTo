package com.agentto.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.agentto.rag.citation.Citation;
import com.agentto.rag.query.RagQueryDecision;
import com.agentto.rag.query.RagQueryResponse;

/**
 * 评测指标计算器测试：覆盖全部指标计算、故障分类、
 * 无样本边界（分母为 0）和百分位数插值。
 */
class EvaluationMetricsTest {

    private final EvaluationMetricsCalculator calculator = new EvaluationMetricsCalculator();

    /** 理想结果集：所有指标为 1.0，无故障分类 */
    @Test
    void calculatesPerfectMetricsForIdealResults() {
        List<EvaluationResult> results = new ArrayList<>();
        results.add(result(caseOf("r-1", List.of(101L, 102L), List.of("c-1", "c-2"),
                RagQueryDecision.ANSWERED, false), answered("c-1", "c-2"), 10));
        results.add(result(caseOf("r-2", List.of(201L), List.of("d-1"),
                RagQueryDecision.ANSWERED, true), answered("d-1"), 20));
        results.add(result(caseOf("n-1", List.of(), List.of(),
                RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE, false), refusal(RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE), 5));
        results.add(result(caseOf("i-1", List.of(101L), List.of(),
                RagQueryDecision.INSUFFICIENT_EVIDENCE, false), refusal(RagQueryDecision.INSUFFICIENT_EVIDENCE), 8));
        results.add(result(caseOf("c-1", List.of(103L), List.of("p-1"),
                RagQueryDecision.INVALID_CITATION, false), refusal(RagQueryDecision.INVALID_CITATION), 7));

        EvaluationMetrics metrics = calculator.calculate(results);

        assertThat(metrics.totalCases()).isEqualTo(5);
        assertThat(metrics.routeRecallAt3()).isEqualTo(1.0);
        assertThat(metrics.retrievalHitAt10()).isEqualTo(1.0);
        assertThat(metrics.mrr()).isEqualTo(1.0);
        assertThat(metrics.refusalPrecision()).isEqualTo(1.0);
        assertThat(metrics.refusalRecall()).isEqualTo(1.0);
        assertThat(metrics.citationValidity()).isEqualTo(1.0);
        assertThat(metrics.rewriteRecoveryRate()).isEqualTo(1.0);
        assertThat(metrics.failureCounts()).isEmpty();
    }

    /** 路由故障：期望有知识库却无路由 → NO_ROUTE_FALSE_NEGATIVE；期望无知识库却有路由 → ROUTE_FALSE_POSITIVE */
    @Test
    void classifiesRouteMisses() {
        List<EvaluationResult> results = new ArrayList<>();
        results.add(result(caseOf("f-1", List.of(101L), List.of("c-1"),
                RagQueryDecision.ANSWERED, false), refusal(RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE), 1));
        results.add(result(caseOf("f-2", List.of(), List.of(),
                RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE, false), answered("c-1"), 1));

        EvaluationMetrics metrics = calculator.calculate(results);

        assertThat(metrics.failureCounts())
                .containsEntry(RagFailureCode.NO_ROUTE_FALSE_NEGATIVE, 1)
                .containsEntry(RagFailureCode.ROUTE_FALSE_POSITIVE, 1);
        assertThat(metrics.routeRecallAt3()).isEqualTo(0.0);
    }

    /** 决策级故障：应作答却拒答 → FALSE_REFUSAL；应拒答却作答 → FALSE_ACCEPT */
    @Test
    void classifiesFalseAcceptAndFalseRefusal() {
        List<EvaluationResult> results = new ArrayList<>();
        results.add(result(caseOf("f-3", List.of(101L), List.of("c-1"),
                RagQueryDecision.ANSWERED, false), refusal(RagQueryDecision.INSUFFICIENT_EVIDENCE), 1));
        results.add(result(caseOf("f-4", List.of(), List.of(),
                RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE, false), answered("c-1"), 1));

        EvaluationMetrics metrics = calculator.calculate(results);

        assertThat(metrics.failureCounts())
                .containsEntry(RagFailureCode.FALSE_REFUSAL, 1)
                .containsEntry(RagFailureCode.FALSE_ACCEPT, 1);
        // 唯一一次实际拒答是错误的（应作答却拒答）→ 精确率 0；预期拒答的用例实际作答 → 召回 0
        assertThat(metrics.refusalPrecision()).isEqualTo(0.0);
        assertThat(metrics.refusalRecall()).isEqualTo(0.0);
    }

    /** 引用与模型故障：应作答却引用无效 → INVALID_CITATION；应作答但生成不可用 → MODEL_FAILURE */
    @Test
    void classifiesInvalidCitationAndModelFailure() {
        List<EvaluationResult> results = new ArrayList<>();
        results.add(result(caseOf("f-5", List.of(101L), List.of("c-1"),
                RagQueryDecision.ANSWERED, false), refusal(RagQueryDecision.INVALID_CITATION), 1));
        results.add(result(caseOf("f-6", List.of(101L), List.of("c-1"),
                RagQueryDecision.ANSWERED, false), refusal(RagQueryDecision.GENERATION_UNAVAILABLE), 1));

        EvaluationMetrics metrics = calculator.calculate(results);

        assertThat(metrics.failureCounts())
                .containsEntry(RagFailureCode.INVALID_CITATION, 1)
                .containsEntry(RagFailureCode.MODEL_FAILURE, 1);
    }

    /** 检索/精排故障：引用无命中 → RETRIEVAL_MISS；命中但排名超 10 → RERANK_MISS */
    @Test
    void classifiesRetrievalMissAndRerankMiss() {
        // 无命中：引用块 ID 不在期望集合内
        List<EvaluationResult> results = new ArrayList<>();
        results.add(result(caseOf("f-7", List.of(101L), List.of("c-1"),
                RagQueryDecision.ANSWERED, false), answered("other-1"), 1));
        // 命中但排名第 12（超出 top-10）：构造 12 条引用，期望块位于末尾
        List<Citation> longCitations = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            longCitations.add(new Citation("slot-" + index, "内容 " + index));
        }
        longCitations.set(11, new Citation("c-1", "内容"));
        results.add(result(caseOf("f-8", List.of(101L), List.of("c-1"),
                RagQueryDecision.ANSWERED, false), new RagQueryResponse(
                        RagQueryDecision.ANSWERED, "答案", List.copyOf(longCitations), List.of(), "t"), 1));

        EvaluationMetrics metrics = calculator.calculate(results);

        assertThat(metrics.failureCounts())
                .containsEntry(RagFailureCode.RETRIEVAL_MISS, 1)
                .containsEntry(RagFailureCode.RERANK_MISS, 1);
        assertThat(metrics.retrievalHitAt10()).isEqualTo(0.5);
    }

    /** MRR：第一个命中期望文档的引用排名倒数均值 */
    @Test
    void calculatesMrrUsingFirstHitRank() {
        List<EvaluationResult> results = new ArrayList<>();
        // rank 1：期望块是第一个引用
        results.add(result(caseOf("m-1", List.of(101L), List.of("c-1"),
                RagQueryDecision.ANSWERED, false),
                new RagQueryResponse(RagQueryDecision.ANSWERED, "答案",
                        List.of(new Citation("c-1", "内容"), new Citation("c-2", "内容")), List.of(), "t"), 1));
        // rank 3：期望块是第三个引用
        results.add(result(caseOf("m-2", List.of(101L), List.of("c-3"),
                RagQueryDecision.ANSWERED, false),
                new RagQueryResponse(RagQueryDecision.ANSWERED, "答案",
                        List.of(new Citation("c-1", "内容"), new Citation("c-2", "内容"),
                                new Citation("c-3", "内容")), List.of(), "t"), 1));

        EvaluationMetrics metrics = calculator.calculate(results);

        assertThat(metrics.mrr()).isEqualTo((1.0 + 1.0 / 3.0) / 2.0);
        assertThat(metrics.retrievalHitAt10()).isEqualTo(1.0);
    }

    /** 百分位数：P50 取中位数，P95 线性插值 */
    @Test
    void calculatesP50AndP95Percentiles() {
        List<EvaluationResult> results = new ArrayList<>();
        for (long latency : new long[] { 10, 20, 30, 40, 100 }) {
            results.add(result(caseOf("p-" + latency, List.of(101L), List.of("c-1"),
                    RagQueryDecision.ANSWERED, false), answered("c-1"), latency));
        }

        EvaluationMetrics metrics = calculator.calculate(results);

        assertThat(metrics.latencyP50Millis()).isEqualTo(30);
        // 0.95 * (5-1) = 3.8 → 40 + 0.8 * (100 - 40) = 88
        assertThat(metrics.latencyP95Millis()).isEqualTo(88);
    }

    /** 执行期异常（响应为 null）：记为 INDEX_FAILURE，指标统计跳过该条 */
    @Test
    void treatsExecutionFailureAsIndexFailure() {
        List<EvaluationResult> results = new ArrayList<>();
        results.add(result(caseOf("x-1", List.of(101L), List.of("c-1"),
                RagQueryDecision.ANSWERED, false), null, 50));

        EvaluationMetrics metrics = calculator.calculate(results);

        assertThat(metrics.totalCases()).isEqualTo(1);
        assertThat(metrics.failureCounts()).containsEntry(RagFailureCode.INDEX_FAILURE, 1);
        assertThat(metrics.routeRecallAt3()).isEqualTo(1.0);
        assertThat(metrics.mrr()).isEqualTo(0.0);
    }

    /** 无样本边界：分母为 0 的指标返回 1.0，不抛异常 */
    @Test
    void returnsOneForEmptyDenominators() {
        EvaluationMetrics metrics = calculator.calculate(List.of());

        assertThat(metrics.totalCases()).isZero();
        assertThat(metrics.routeRecallAt3()).isEqualTo(1.0);
        assertThat(metrics.retrievalHitAt10()).isEqualTo(1.0);
        assertThat(metrics.refusalPrecision()).isEqualTo(1.0);
        assertThat(metrics.refusalRecall()).isEqualTo(1.0);
        assertThat(metrics.citationValidity()).isEqualTo(1.0);
        assertThat(metrics.rewriteRecoveryRate()).isEqualTo(1.0);
        assertThat(metrics.latencyP50Millis()).isZero();
        assertThat(metrics.latencyP95Millis()).isZero();
        assertThat(metrics.failureCounts()).isEmpty();
    }

    /** 改写恢复率：期望改写用例中最终作答的比例 */
    @Test
    void calculatesRewriteRecoveryRate() {
        List<EvaluationResult> results = new ArrayList<>();
        results.add(result(caseOf("w-1", List.of(101L), List.of("c-1"),
                RagQueryDecision.ANSWERED, true), answered("c-1"), 10));
        results.add(result(caseOf("w-2", List.of(101L), List.of("c-1"),
                RagQueryDecision.ANSWERED, true), refusal(RagQueryDecision.INSUFFICIENT_EVIDENCE), 10));
        // 非改写用例不参与统计
        results.add(result(caseOf("w-3", List.of(101L), List.of("c-1"),
                RagQueryDecision.ANSWERED, false), answered("c-1"), 10));

        EvaluationMetrics metrics = calculator.calculate(results);

        assertThat(metrics.rewriteRecoveryRate()).isEqualTo(0.5);
    }

    // --- 辅助方法 ---

    /** 构造评测结果 */
    private EvaluationResult result(EvaluationCase testCase, RagQueryResponse response, long latency) {
        return new EvaluationResult(testCase, response, latency);
    }

    /** 构造评测用例 */
    private EvaluationCase caseOf(String id, List<Long> kbIds, List<String> chunkIds,
            RagQueryDecision decision, boolean expectsRewrite) {
        return new EvaluationCase(id, 10L, "查询", kbIds, chunkIds, decision, expectsRewrite);
    }

    /** 构造作答响应（引用全部有效） */
    private RagQueryResponse answered(String... chunkIds) {
        List<Citation> citations = new ArrayList<>();
        for (String chunkId : chunkIds) {
            citations.add(new Citation(chunkId, "内容"));
        }
        return new RagQueryResponse(RagQueryDecision.ANSWERED, "答案", citations, List.of(), "trace");
    }

    /** 构造拒答响应 */
    private RagQueryResponse refusal(RagQueryDecision decision) {
        return new RagQueryResponse(decision, null, List.of(), List.of(), null);
    }
}
