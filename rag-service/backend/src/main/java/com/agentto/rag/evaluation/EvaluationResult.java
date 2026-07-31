package com.agentto.rag.evaluation;

import com.agentto.rag.query.RagQueryResponse;

/**
 * 单条评测执行结果。
 *
 * @param testCase      评测用例
 * @param response      RAG 查询响应；执行期异常（索引故障等）时为 null
 * @param latencyMillis 端到端耗时（毫秒）
 */
public record EvaluationResult(EvaluationCase testCase, RagQueryResponse response, long latencyMillis) {

    /**
     * 紧凑构造器：校验必填参数。
     */
    public EvaluationResult {
        if (testCase == null) {
            throw new IllegalArgumentException("评测用例不能为空");
        }
        if (latencyMillis < 0) {
            throw new IllegalArgumentException("耗时不能为负数");
        }
    }
}
