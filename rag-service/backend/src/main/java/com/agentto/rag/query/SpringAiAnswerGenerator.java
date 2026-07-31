package com.agentto.rag.query;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.agentto.rag.citation.Citation;
import com.agentto.rag.citation.GeneratedAnswer;
import com.agentto.rag.retrieval.RetrievalCandidate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 基于 Spring AI 的答案生成器。
 *
 * 仅在 rag.answer.enabled=true 时装配（接入 ChatModel 的环境），
 * 默认装配 DisabledAnswerGenerator。不用 @ConditionalOnBean(ChatModel.class)
 * 是因为用户配置类上的该条件无法可靠看到自动配置注册的 ChatModel。
 *
 * 要求模型输出严格 JSON：{"text":"答案","citations":[{"chunkId":"切片ID","quote":"逐字原文"}]}。
 * 输出不可解析时返回空答案（text=null、citations=空），绝不放行模型幻觉。
 */
@Service
@ConditionalOnProperty(prefix = "rag.answer", name = "enabled", havingValue = "true", matchIfMissing = false)
public class SpringAiAnswerGenerator implements AnswerGenerator {

    private static final Logger log = LoggerFactory.getLogger(SpringAiAnswerGenerator.class);

    /** 系统提示：约束模型只能基于证据回答并逐字引用 */
    private static final String SYSTEM_PROMPT = """
            你是企业知识库问答助手，回答必须遵守：
            1. 只能基于给定证据内容回答，禁止使用证据之外的知识。
            2. 每个主要结论必须引用证据切片，引用必须真实存在。
            3. quote 必须逐字来自对应切片内容，禁止改写、拼接或编造。
            4. 证据不支持问题时，text 返回空字符串，禁止编造答案。
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    /**
     * 构造生成器。
     *
     * @param chatModel    Spring AI 聊天模型
     * @param objectMapper JSON 解析器，用于解析模型输出的结构化答案
     */
    public SpringAiAnswerGenerator(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
    }

    /**
     * 基于证据生成答案。
     *
     * @param query    原始查询
     * @param evidence 已通过证据门的检索候选，允许为 null
     * @return 生成答案；模型输出不可解析时返回空答案
     */
    @Override
    public GeneratedAnswer generate(String query, List<RetrievalCandidate> evidence) {
        // 拼接证据文本：[chunkId] 内容，逐条列出供模型引用
        StringBuilder evidenceText = new StringBuilder();
        if (evidence != null) {
            for (RetrievalCandidate candidate : evidence) {
                String content = candidate.content() == null ? "" : candidate.content();
                evidenceText.append('[').append(candidate.chunkId()).append("] ").append(content).append('\n');
            }
        }

        String userPrompt = """
                查询：%s

                证据：
                %s

                严格输出 JSON：{"text":"答案","citations":[{"chunkId":"切片ID","quote":"逐字原文"}]}
                """.formatted(query, evidenceText);

        // 调用模型获取原始输出
        String raw = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        return parse(raw);
    }

    /**
     * 解析模型输出为 GeneratedAnswer。
     *
     * 容错规则：
     * - 输出为空白 → 空答案
     * - 剥离 markdown 代码块围栏后解析 JSON
     * - JSON 非法 → 空答案并记录警告
     * - 引用条目缺 chunkId/quote → 跳过该条
     *
     * @param raw 模型原始输出
     * @return 解析后的答案，失败时为空答案
     */
    private GeneratedAnswer parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new GeneratedAnswer(null, List.of());
        }

        String json = extractJson(raw);
        try {
            JsonNode root = objectMapper.readTree(json);
            // text 缺失或非文本节点时置为 null，由调用方判定生成失败
            JsonNode textNode = root.path("text");
            String text = textNode.isTextual() ? textNode.asText() : null;

            List<Citation> citations = new ArrayList<>();
            JsonNode citationsNode = root.path("citations");
            if (citationsNode.isArray()) {
                for (JsonNode item : citationsNode) {
                    JsonNode chunkIdNode = item.path("chunkId");
                    JsonNode quoteNode = item.path("quote");
                    if (chunkIdNode.isTextual() && quoteNode.isTextual()) {
                        citations.add(new Citation(chunkIdNode.asText(), quoteNode.asText()));
                    }
                }
            }
            return new GeneratedAnswer(text, citations);
        } catch (JsonProcessingException e) {
            // 模型输出不可解析：按生成失败处理，不放行不可信内容
            log.warn("模型输出不是合法 JSON，按生成失败处理：{}", e.getMessage());
            return new GeneratedAnswer(null, List.of());
        }
    }

    /**
     * 提取 JSON 主体：剥离模型常见的 markdown 代码块围栏（```json ... ```）。
     *
     * @param raw 模型原始输出
     * @return 纯 JSON 文本
     */
    private String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                // 去掉首行围栏（``` 或 ```json）
                trimmed = trimmed.substring(firstNewline + 1);
            } else {
                // 只有围栏没有内容
                trimmed = "";
            }
        }
        if (trimmed.endsWith("```")) {
            // 去掉结尾围栏
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
