package com.agentto.rag.evaluation;

import java.util.Map;

/**
 * 评测指标快照。
 *
 * @param routeRecallAt3      路由召回率@3：期望有知识库的用例中成功路由的比例
 * @param retrievalHitAt10    检索命中率@10：期望命中文档被输出引用的比例
 * @param mrr                 平均倒数排名（Mean Reciprocal Rank）
 * @param refusalPrecision    拒答精确率：实际拒答中符合预期的比例
 * @param refusalRecall       拒答召回率：预期拒答中正确拒答的比例
 * @param citationValidity    引用真实性：输出答案中引用全部真实的用例比例
 * @param rewriteRecoveryRate 改写恢复率：期望改写恢复的用例中最终作答的比例
 * @param latencyP50Millis    端到端延迟 P50（毫秒）
 * @param latencyP95Millis    端到端延迟 P95（毫秒）
 * @param totalCases          总用例数
 * @param failureCounts       各故障分类计数
 */
public record EvaluationMetrics(
        double routeRecallAt3,
        double retrievalHitAt10,
        double mrr,
        double refusalPrecision,
        double refusalRecall,
        double citationValidity,
        double rewriteRecoveryRate,
        long latencyP50Millis,
        long latencyP95Millis,
        int totalCases,
        Map<RagFailureCode, Integer> failureCounts) {

    /**
     * 紧凑构造器：防御性拷贝故障计数。
     */
    public EvaluationMetrics {
        failureCounts = failureCounts == null ? Map.of() : Map.copyOf(failureCounts);
    }
}
