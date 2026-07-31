package com.agentto.rag.rewrite;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 禁用状态下的查询改写器。
 *
 * 默认装配（rag.rewrite.enabled 缺省视为 false，即未接入 ChatModel 时启用本实现）。
 * rewrite 永远返回 Optional.empty()，编排层据此不再重试检索，
 * 保证没有模型时也不会伪造改写结果。
 */
@Service
@ConditionalOnProperty(prefix = "rag.rewrite", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledQueryRewriter implements QueryRewriter {

    /**
     * 改写查询：直接返回空，表示改写不可用。
     *
     * @param originalQuery 原始查询
     * @param failureReason 证据不足原因说明
     * @return 恒为 Optional.empty()
     */
    @Override
    public Optional<String> rewrite(String originalQuery, String failureReason) {
        return Optional.empty();
    }
}
