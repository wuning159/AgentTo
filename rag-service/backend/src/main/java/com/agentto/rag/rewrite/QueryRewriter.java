package com.agentto.rag.rewrite;

import java.util.Optional;

/**
 * 查询改写器：当首次检索证据不足时，把原始查询改写为更适合检索的形式。
 *
 * 实现约定：
 * - 改写失败（模型异常、输出非法）一律降级返回 Optional.empty()，绝不返回不可信改写
 * - 改写超过 1000 字符拒绝
 * - 该接口隔离 Spring AI 具体 API，使编排层单测无需真实模型
 */
public interface QueryRewriter {

    /**
     * 改写查询。
     *
     * @param originalQuery 原始查询
     * @param failureReason 首次检索证据不足的原因说明（用于诊断，可空）
     * @return 改写后的查询；不可用时返回 Optional.empty()
     */
    Optional<String> rewrite(String originalQuery, String failureReason);
}
