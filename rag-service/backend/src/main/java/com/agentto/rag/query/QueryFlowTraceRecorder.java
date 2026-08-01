package com.agentto.rag.query;

/**
 * 公共查询编排 Trace 记录器。
 *
 * <p>编排服务在查询结束时调用，持久化一次查询的完整诊断记录。
 * 记录失败不得影响查询主链路，实现方必须自行兜底。
 */
public interface QueryFlowTraceRecorder {

    /**
     * 记录一次公共查询编排 Trace。
     *
     * @param trace 编排 Trace 快照
     */
    void record(QueryFlowTrace trace);
}
