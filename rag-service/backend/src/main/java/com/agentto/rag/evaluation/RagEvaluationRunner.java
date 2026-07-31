package com.agentto.rag.evaluation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.agentto.rag.query.RagQueryCommand;
import com.agentto.rag.query.RagQueryService;
import com.agentto.rag.query.RagQueryResponse;

/**
 * RAG 评测执行器：加载数据集、逐条执行查询（计时）、计算指标并校验发布门禁。
 *
 * <p>首版发布门禁（延迟只报告不设门禁，待真实环境稳定后冻结）：
 * <ul>
 *   <li>Route Recall@3 &gt;= 0.95</li>
 *   <li>Retrieval Hit@10 &gt;= 0.90</li>
 *   <li>Refusal Precision &gt;= 0.95</li>
 *   <li>Refusal Recall &gt;= 0.90</li>
 *   <li>Citation Validity == 1.00</li>
 * </ul>
 */
@Component
public class RagEvaluationRunner {

    /** 路由召回门禁 */
    public static final double ROUTE_RECALL_AT_3_GATE = 0.95;

    /** 检索命中门禁 */
    public static final double RETRIEVAL_HIT_AT_10_GATE = 0.90;

    /** 拒答精确率门禁 */
    public static final double REFUSAL_PRECISION_GATE = 0.95;

    /** 拒答召回率门禁 */
    public static final double REFUSAL_RECALL_GATE = 0.90;

    /** 引用真实性门禁 */
    public static final double CITATION_VALIDITY_GATE = 1.00;

    private final EvaluationDatasetLoader loader;
    private final EvaluationMetricsCalculator calculator;
    private final RagQueryService queryService;

    /**
     * 构造评测执行器。
     *
     * @param loader      数据集加载器
     * @param calculator  指标计算器
     * @param queryService 公共查询编排服务
     */
    public RagEvaluationRunner(EvaluationDatasetLoader loader, EvaluationMetricsCalculator calculator,
            RagQueryService queryService) {
        this.loader = loader;
        this.calculator = calculator;
        this.queryService = queryService;
    }

    /**
     * 执行评测。
     *
     * @param datasetLocation 数据集资源位置（classpath 或 file）
     * @return 评测报告（指标 + 门禁结果）
     */
    public EvaluationReport run(String datasetLocation) {
        List<EvaluationCase> cases = loader.load(datasetLocation);
        List<EvaluationResult> results = new ArrayList<>();
        for (EvaluationCase testCase : cases) {
            long started = System.nanoTime();
            RagQueryResponse response;
            try {
                response = queryService.query(new RagQueryCommand(testCase.clientAppId(),
                        testCase.query(), 8));
            } catch (RuntimeException e) {
                // 执行期异常：记录为索引/基础设施故障，不中断整体评测
                response = null;
            }
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            results.add(new EvaluationResult(testCase, response, elapsedMillis));
        }
        EvaluationMetrics metrics = calculator.calculate(results);
        List<String> gateFailures = validateGates(metrics);
        return new EvaluationReport(metrics, gateFailures, datasetLocation);
    }

    /**
     * 校验发布门禁。
     *
     * @param metrics 评测指标
     * @return 未通过的门禁描述列表，空表示全部通过
     */
    public static List<String> validateGates(EvaluationMetrics metrics) {
        List<String> failures = new ArrayList<>();
        if (metrics.routeRecallAt3() < ROUTE_RECALL_AT_3_GATE) {
            failures.add(String.format("Route Recall@3 低于门禁：%.3f < %.2f",
                    metrics.routeRecallAt3(), ROUTE_RECALL_AT_3_GATE));
        }
        if (metrics.retrievalHitAt10() < RETRIEVAL_HIT_AT_10_GATE) {
            failures.add(String.format("Retrieval Hit@10 低于门禁：%.3f < %.2f",
                    metrics.retrievalHitAt10(), RETRIEVAL_HIT_AT_10_GATE));
        }
        if (metrics.refusalPrecision() < REFUSAL_PRECISION_GATE) {
            failures.add(String.format("Refusal Precision 低于门禁：%.3f < %.2f",
                    metrics.refusalPrecision(), REFUSAL_PRECISION_GATE));
        }
        if (metrics.refusalRecall() < REFUSAL_RECALL_GATE) {
            failures.add(String.format("Refusal Recall 低于门禁：%.3f < %.2f",
                    metrics.refusalRecall(), REFUSAL_RECALL_GATE));
        }
        if (metrics.citationValidity() != CITATION_VALIDITY_GATE) {
            failures.add(String.format("Citation Validity 未达门禁：%.3f != %.2f",
                    metrics.citationValidity(), CITATION_VALIDITY_GATE));
        }
        return failures;
    }

    /**
     * 评测报告。
     *
     * @param metrics      评测指标
     * @param gateFailures 未通过的门禁描述列表
     * @param datasetLocation 数据集资源位置
     */
    public record EvaluationReport(EvaluationMetrics metrics, List<String> gateFailures,
            String datasetLocation) {

        /**
         * 紧凑构造器：防御性拷贝门禁失败列表。
         */
        public EvaluationReport {
            gateFailures = gateFailures == null ? List.of() : List.copyOf(gateFailures);
        }

        /**
         * 是否全部门禁通过。
         *
         * @return true 表示可发布
         */
        public boolean gatesPassed() {
            return gateFailures.isEmpty();
        }
    }
}
