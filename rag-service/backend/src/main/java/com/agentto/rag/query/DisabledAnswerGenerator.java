package com.agentto.rag.query;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.agentto.rag.citation.GeneratedAnswer;
import com.agentto.rag.retrieval.RetrievalCandidate;

/**
 * 禁用状态下的答案生成器。
 *
 * 默认装配（rag.answer.enabled 缺省视为 false，即未接入 ChatModel 时启用本实现）。
 * 调用 generate 直接抛出 GenerationUnavailableException，
 * 由编排层返回 GENERATION_UNAVAILABLE，绝不把检索片段拼成伪答案。
 *
 * 不使用 @ConditionalOnBean(ChatModel.class) 是因为用户配置类上的该条件
 * 无法可靠看到自动配置注册的 ChatModel（Spring Boot 条件评估顺序限制）。
 */
@Service
@ConditionalOnProperty(prefix = "rag.answer", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAnswerGenerator implements AnswerGenerator {

    /**
     * 生成答案：直接抛出不可用异常。
     *
     * @param query    原始查询
     * @param evidence 证据候选
     * @return 永不返回
     * @throws GenerationUnavailableException 答案生成未启用
     */
    @Override
    public GeneratedAnswer generate(String query, List<RetrievalCandidate> evidence) {
        throw new GenerationUnavailableException("答案生成未启用（未配置 ChatModel）");
    }
}
