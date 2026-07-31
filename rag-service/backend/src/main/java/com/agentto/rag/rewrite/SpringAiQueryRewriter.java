package com.agentto.rag.rewrite;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 基于 Spring AI RewriteQueryTransformer 的查询改写器。
 *
 * 仅在 rag.rewrite.enabled=true 时装配（接入 ChatModel 的环境）。
 * Prompt 约束模型：保留原意和实体、补足检索关键词、不回答问题、只返回一个改写。
 *
 * 降级策略（一律返回 Optional.empty()）：
 * - 模型异常（超时/限流等）
 * - 改写结果为空或仅空白
 * - 改写结果与原查询相同（视为无实质改写）
 * - 改写结果超过 1000 字符（视为不可信输出）
 */
@Service
@ConditionalOnProperty(prefix = "rag.rewrite", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SpringAiQueryRewriter implements QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(SpringAiQueryRewriter.class);

    /** 改写结果最大长度，超过视为不可信输出 */
    private static final int MAX_REWRITE_LENGTH = 1000;

    /** 改写提示模板：必须包含 {target} 和 {query} 两个占位符 */
    private static final String REWRITE_PROMPT_TEMPLATE = """
            你是企业知识库查询改写助手。请将用户查询改写为更适合检索的形式：
            1. 保留原意和关键实体，不改变查询意图。
            2. 补足被省略的检索关键词，让检索更容易命中。
            3. 不回答问题，只输出改写后的查询。
            4. 只返回一个改写查询，不要任何解释或格式包装。

            目标检索系统：{target}

            原始查询：
            {query}

            改写后的查询：
            """;

    private final RewriteQueryTransformer transformer;

    /**
     * 构造改写器。
     *
     * @param chatModel Spring AI 聊天模型
     */
    public SpringAiQueryRewriter(ChatModel chatModel) {
        this.transformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(chatModel))
                .promptTemplate(new PromptTemplate(REWRITE_PROMPT_TEMPLATE))
                .targetSearchSystem("企业知识库")
                .build();
    }

    /**
     * 改写查询，任何失败都降级为 Optional.empty()。
     *
     * @param originalQuery 原始查询
     * @param failureReason 证据不足原因说明，仅用于诊断日志
     * @return 改写后的查询；不可用时为 Optional.empty()
     */
    @Override
    public Optional<String> rewrite(String originalQuery, String failureReason) {
        // 空查询无需改写
        if (originalQuery == null || originalQuery.isBlank()) {
            return Optional.empty();
        }

        try {
            // 调用模型改写（RewriteQueryTransformer 内部对模型空输出返回原查询）
            Query rewritten = transformer.transform(new Query(originalQuery));

            // 折叠多余空白，模型输出可能带换行/多空格。
            // 注：RewriteQueryTransformer 对模型空输出会返回原查询，此处折叠后必非空
            String collapsed = collapseWhitespace(rewritten.text());

            // 改写与原查询相同：视为无实质改写
            if (collapsed.equals(collapseWhitespace(originalQuery))) {
                log.warn("查询改写无实质变化，放弃重试，原因：{}", failureReason);
                return Optional.empty();
            }

            // 超过最大长度：视为不可信输出
            if (collapsed.length() > MAX_REWRITE_LENGTH) {
                log.warn("改写结果超过 {} 字符，拒绝使用", MAX_REWRITE_LENGTH);
                return Optional.empty();
            }

            return Optional.of(collapsed);
        } catch (RuntimeException e) {
            // 模型异常（超时、限流、解析失败等）一律降级
            log.warn("查询改写失败，降级返回空，原因：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 折叠空白：连续空白（含换行/制表）折叠为单个空格并去除首尾空白。
     *
     * @param text 原始文本
     * @return 清理后的文本，null 视为空串
     */
    private static String collapseWhitespace(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
