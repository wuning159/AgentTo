package com.agentto.rag.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.agentto.rag.citation.Citation;
import com.agentto.rag.query.RagQueryDecision;
import com.agentto.rag.query.RagQueryResponse;

/**
 * 评测指标计算器：从评测结果计算全部指标，并按故障分类规则独立计数。
 *
 * <p>指标定义：
 * <ul>
 *   <li>Route Recall@3：期望有知识库（expectedKbIds 非空）的用例中未出现
 *       NO_ROUTE_FALSE_NEGATIVE 的比例；无此类用例时为 1.0</li>
 *   <li>Retrieval Hit@10：期望有文档（expectedChunkIds 非空）且实际作答的用例中，
 *       输出引用命中任一期望文档块的比例；无此类用例时为 1.0</li>
 *   <li>MRR：作答用例中第一个命中期望文档块的引用排名的倒数均值</li>
 *   <li>Refusal Precision/Recall：拒答 = 决策非 ANSWERED（含生成不可用）；
 *       预期拒答 = 期望决策非 ANSWERED</li>
 *   <li>Citation Validity：作答用例中引用非空且全部来自期望文档块的比例</li>
 *   <li>Rewrite Recovery Rate：标记 expectsRewrite 的用例中最终作答的比例</li>
 *   <li>P50/P95：全部用例耗时的百分位数（线性插值）</li>
 * </ul>
 *
 * <p>故障分类判定规则（独立判定，可多命中）：
 * <ul>
 *   <li>期望有知识库且决策为 NO_RELEVANT_KNOWLEDGE_BASE → NO_ROUTE_FALSE_NEGATIVE</li>
 *   <li>期望无知识库且决策非 NO_RELEVANT_KNOWLEDGE_BASE → ROUTE_FALSE_POSITIVE</li>
 *   <li>期望作答但实际拒答 → FALSE_REFUSAL</li>
 *   <li>期望拒答但实际作答 → FALSE_ACCEPT</li>
 *   <li>期望作答且决策为 INVALID_CITATION → INVALID_CITATION（模型编造引用被拦截）</li>
 *   <li>期望作答且决策为 GENERATION_UNAVAILABLE → MODEL_FAILURE</li>
 *   <li>期望有文档且作答但引用未命中任何期望文档块 → RETRIEVAL_MISS</li>
 *   <li>期望有文档且作答且命中但最佳排名超过 10 → RERANK_MISS</li>
 *   <li>执行期异常（响应为 null）→ INDEX_FAILURE</li>
 * </ul>
 */
@Component
public class EvaluationMetricsCalculator {

    /** 精排输出允许的最大排名（Hit@10 与 RERANK_MISS 的边界） */
    private static final int TOP_K = 10;

    /**
     * 计算全部指标。
     *
     * @param results 评测结果列表
     * @return 指标快照
     */
    public EvaluationMetrics calculate(List<EvaluationResult> results) {
        Map<RagFailureCode, Integer> failureCounts = new HashMap<>();
        List<Long> latencies = new ArrayList<>();
        // 路由相关累加器
        int routable = 0;
        int routeHits = 0;
        // 检索/精排相关累加器
        int chunkExpectedAnswered = 0;
        int hitAt10 = 0;
        double reciprocalSum = 0;
        int answeredCases = 0;
        int validCitationCases = 0;
        // 拒答相关累加器
        int actualRefusals = 0;
        int expectedRefusals = 0;
        int correctRefusals = 0;
        // 改写恢复累加器
        int rewriteCases = 0;
        int rewriteRecovered = 0;

        for (EvaluationResult result : results) {
            EvaluationCase testCase = result.testCase();
            latencies.add(result.latencyMillis());
            RagQueryResponse response = result.response();

            // 执行期异常：索引/基础设施故障，指标统计跳过该条
            if (response == null) {
                failureCounts.merge(RagFailureCode.INDEX_FAILURE, 1, Integer::sum);
                continue;
            }

            RagQueryDecision decision = response.decision();
            boolean expectedAnswer = testCase.expectedDecision() == RagQueryDecision.ANSWERED;
            boolean answered = decision == RagQueryDecision.ANSWERED;
            boolean refusal = !answered;

            // 路由：期望有知识库的用例统计路由召回；期望无知识库的用例统计误召
            if (!testCase.expectedKbIds().isEmpty()) {
                routable++;
                if (decision == RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE) {
                    failureCounts.merge(RagFailureCode.NO_ROUTE_FALSE_NEGATIVE, 1, Integer::sum);
                } else {
                    routeHits++;
                }
            } else if (decision != RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE) {
                failureCounts.merge(RagFailureCode.ROUTE_FALSE_POSITIVE, 1, Integer::sum);
            }

            // 决策级故障分类
            if (expectedAnswer && refusal) {
                failureCounts.merge(RagFailureCode.FALSE_REFUSAL, 1, Integer::sum);
            }
            if (!expectedAnswer && answered) {
                failureCounts.merge(RagFailureCode.FALSE_ACCEPT, 1, Integer::sum);
            }
            if (expectedAnswer && decision == RagQueryDecision.INVALID_CITATION) {
                failureCounts.merge(RagFailureCode.INVALID_CITATION, 1, Integer::sum);
            }
            if (expectedAnswer && decision == RagQueryDecision.GENERATION_UNAVAILABLE) {
                failureCounts.merge(RagFailureCode.MODEL_FAILURE, 1, Integer::sum);
            }

            // 检索/精排：仅统计期望有文档且实际作答的用例
            if (!testCase.expectedChunkIds().isEmpty() && answered) {
                chunkExpectedAnswered++;
                int bestRank = bestChunkRank(testCase, response);
                if (bestRank > 0) {
                    hitAt10++;
                    reciprocalSum += 1.0 / bestRank;
                    if (bestRank > TOP_K) {
                        failureCounts.merge(RagFailureCode.RERANK_MISS, 1, Integer::sum);
                    }
                } else {
                    failureCounts.merge(RagFailureCode.RETRIEVAL_MISS, 1, Integer::sum);
                }
            }

            // 拒答精确率/召回率
            if (refusal) {
                actualRefusals++;
                if (!expectedAnswer) {
                    correctRefusals++;
                }
            }
            if (!expectedAnswer) {
                expectedRefusals++;
            }

            // 引用真实性：仅统计实际作答的用例
            if (answered) {
                answeredCases++;
                if (hasValidCitations(testCase, response)) {
                    validCitationCases++;
                }
            }

            // 改写恢复率
            if (Boolean.TRUE.equals(testCase.expectsRewrite())) {
                rewriteCases++;
                if (answered) {
                    rewriteRecovered++;
                }
            }
        }

        double routeRecall = safeRatio(routeHits, routable);
        double hitRate = safeRatio(hitAt10, chunkExpectedAnswered);
        double mrr = answeredCases == 0 ? 0.0 : reciprocalSum / answeredCases;
        double refusalPrecision = safeRatio(correctRefusals, actualRefusals);
        double refusalRecall = safeRatio(correctRefusals, expectedRefusals);
        double citationValidity = safeRatio(validCitationCases, answeredCases);
        double rewriteRecovery = safeRatio(rewriteRecovered, rewriteCases);
        long[] percentiles = percentiles(latencies);

        return new EvaluationMetrics(routeRecall, hitRate, mrr, refusalPrecision, refusalRecall,
                citationValidity, rewriteRecovery, percentiles[0], percentiles[1], results.size(),
                failureCounts);
    }

    /**
     * 计算期望文档块在输出引用中的最佳排名（1 起），未命中返回 0。
     *
     * @param testCase 评测用例
     * @param response 查询响应
     * @return 最佳排名，未命中为 0
     */
    private int bestChunkRank(EvaluationCase testCase, RagQueryResponse response) {
        List<Citation> citations = response.citations();
        for (int index = 0; index < citations.size(); index++) {
            if (testCase.expectedChunkIds().contains(citations.get(index).chunkId())) {
                return index + 1;
            }
        }
        return 0;
    }

    /**
     * 判断输出引用是否全部真实：非空且每个引用块 ID 都来自期望文档块。
     *
     * @param testCase 评测用例
     * @param response 查询响应
     * @return true 表示引用全部真实
     */
    private boolean hasValidCitations(EvaluationCase testCase, RagQueryResponse response) {
        List<Citation> citations = response.citations();
        if (citations.isEmpty()) {
            return false;
        }
        for (Citation citation : citations) {
            if (!testCase.expectedChunkIds().contains(citation.chunkId())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 安全比例：分母为 0 时返回 1.0（无样本视为不扣分）。
     */
    private double safeRatio(int numerator, int denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }

    /**
     * 计算 P50 与 P95 百分位数（线性插值）。
     *
     * @param values 耗时列表
     * @return [P50, P95]
     */
    private long[] percentiles(List<Long> values) {
        if (values.isEmpty()) {
            return new long[] { 0, 0 };
        }
        List<Long> sorted = values.stream().sorted().toList();
        return new long[] { sorted.get(sorted.size() / 2), percentile(sorted, 0.95) };
    }

    /**
     * 计算指定分位数（线性插值）。
     *
     * @param sorted 升序排列的耗时列表（非空）
     * @param quantile 分位数（0~1）
     * @return 分位数值
     */
    private long percentile(List<Long> sorted, double quantile) {
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double position = quantile * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sorted.get(lower);
        }
        double fraction = position - lower;
        return Math.round(sorted.get(lower) + fraction * (sorted.get(upper) - sorted.get(lower)));
    }
}
