package com.agentto.rag.query;

/**
 * 公共查询编排阶段。
 *
 * <p>描述一次公共 RAG 查询从路由到引用校验的完整阶段，
 * 用于 Trace 事件记录和前端工作台渲染。
 *
 * - ROUTE_PROFILE: 第一阶段画像召回
 * - ROUTE_VERIFY: 第二阶段内容验证与知识库选中
 * - EVIDENCE_GATE: 证据门评估（每次检索尝试后）
 * - QUERY_REWRITE: 证据不足时的查询改写
 * - CITATION_VALIDATE: 生成答案的引用真实性校验
 * - COMPLETE: 编排结束
 */
public enum QueryFlowStage {
    ROUTE_PROFILE,
    ROUTE_VERIFY,
    EVIDENCE_GATE,
    QUERY_REWRITE,
    CITATION_VALIDATE,
    COMPLETE
}
