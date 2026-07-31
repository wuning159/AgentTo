package com.agentto.rag.evaluation;

/**
 * RAG 故障分类。
 *
 * <p>每个枚举值对应一类可诊断的回归故障，评测时按规则对每条用例
 * 独立判定，可能同时命中多个分类（例如索引故障伴随拒答）。
 */
public enum RagFailureCode {

    /** 有相关知识库，但路由阶段误判为无知识库（路由漏召） */
    NO_ROUTE_FALSE_NEGATIVE,

    /** 无相关知识库，但路由阶段仍返回了知识库（路由误召） */
    ROUTE_FALSE_POSITIVE,

    /** 期望命中的文档未被检索召回 */
    RETRIEVAL_MISS,

    /** 期望命中的文档被检索到，但精排将其挤出最终输出（排名超出 top-10） */
    RERANK_MISS,

    /** 不应给出答案（预期拒答）却给出了答案 */
    FALSE_ACCEPT,

    /** 应给出答案却拒答 */
    FALSE_REFUSAL,

    /** 模型生成的答案引用未通过真实性校验（被系统拦截，属于模型侧故障） */
    INVALID_CITATION,

    /** 模型/答案生成服务不可用（未配置 ChatModel 或生成失败） */
    MODEL_FAILURE,

    /** 索引/检索基础设施故障（执行期异常） */
    INDEX_FAILURE
}
