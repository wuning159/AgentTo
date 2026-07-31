package com.agentto.rag.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.agentto.rag.citation.Citation;
import com.agentto.rag.citation.GeneratedAnswer;
import com.agentto.rag.retrieval.DedupeStatus;
import com.agentto.rag.retrieval.RetrievalCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Spring AI 答案生成器测试。
 * 使用桩 ChatModel 覆盖结构化输出解析、markdown 围栏剥离、
 * JSON 容错（非法输出/缺失字段）、证据拼接和禁用兜底行为。
 */
class SpringAiAnswerGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 正常 JSON 输出：解析出 text 和 citations，且 prompt 携带查询与证据 */
    @Test
    void parsesStructuredAnswerFromModel() {
        StubChatModel model = new StubChatModel("""
                {"text":"预算审批分为三步。","citations":[
                  {"chunkId":"c1","quote":"预算审批分为三步"},
                  {"chunkId":"c2","quote":"单笔超五十万需总经理审批"}]}
                """);
        SpringAiAnswerGenerator generator = new SpringAiAnswerGenerator(model, objectMapper);

        GeneratedAnswer answer = generator.generate("预算如何审批", List.of(
                RetrievalCandidate.keyword("c1", "预算审批分为三步", 0.8, 1),
                RetrievalCandidate.keyword("c2", "单笔超五十万需总经理审批", 0.7, 2)));

        assertThat(answer.text()).isEqualTo("预算审批分为三步。");
        assertThat(answer.citations()).hasSize(2);
        assertThat(answer.citations().get(0)).isEqualTo(new Citation("c1", "预算审批分为三步"));
        // prompt 必须携带查询和证据内容
        String promptText = model.receivedPrompts().get(0).getContents();
        assertThat(promptText).contains("预算如何审批").contains("[c1] 预算审批分为三步");
    }

    /** markdown 代码块围栏：剥离 ```json 围栏后仍能解析 */
    @Test
    void stripsMarkdownFenceBeforeParsing() {
        StubChatModel model = new StubChatModel("""
                ```json
                {"text":"答案","citations":[{"chunkId":"c1","quote":"原文"}]}
                ```
                """);
        SpringAiAnswerGenerator generator = new SpringAiAnswerGenerator(model, objectMapper);

        GeneratedAnswer answer = generator.generate("q", List.of(candidate("c1", "原文")));

        assertThat(answer.text()).isEqualTo("答案");
        assertThat(answer.citations()).hasSize(1);
    }

    /** 非法 JSON：按生成失败处理，返回空答案 */
    @Test
    void returnsEmptyAnswerWhenModelOutputIsNotJson() {
        StubChatModel model = new StubChatModel("抱歉，我无法回答这个问题。");
        SpringAiAnswerGenerator generator = new SpringAiAnswerGenerator(model, objectMapper);

        GeneratedAnswer answer = generator.generate("q", List.of(candidate("c1", "原文")));

        assertThat(answer.text()).isNull();
        assertThat(answer.citations()).isEmpty();
    }

    /** 模型输出 null：返回空答案 */
    @Test
    void returnsEmptyAnswerWhenModelOutputIsNull() {
        StubChatModel model = new StubChatModel(null);
        SpringAiAnswerGenerator generator = new SpringAiAnswerGenerator(model, objectMapper);

        GeneratedAnswer answer = generator.generate("q", List.of(candidate("c1", "原文")));

        assertThat(answer.text()).isNull();
        assertThat(answer.citations()).isEmpty();
    }

    /** 模型输出空白：返回空答案 */
    @Test
    void returnsEmptyAnswerWhenModelOutputIsBlank() {
        StubChatModel model = new StubChatModel("   \n  ");
        SpringAiAnswerGenerator generator = new SpringAiAnswerGenerator(model, objectMapper);

        GeneratedAnswer answer = generator.generate("q", List.of(candidate("c1", "原文")));

        assertThat(answer.text()).isNull();
        assertThat(answer.citations()).isEmpty();
    }

    /** text 字段缺失：text 为 null，citations 仍正常解析 */
    @Test
    void treatsMissingTextAsNull() {
        StubChatModel model = new StubChatModel("{\"citations\":[{\"chunkId\":\"c1\",\"quote\":\"原文\"}]}");
        SpringAiAnswerGenerator generator = new SpringAiAnswerGenerator(model, objectMapper);

        GeneratedAnswer answer = generator.generate("q", List.of(candidate("c1", "原文")));

        assertThat(answer.text()).isNull();
        assertThat(answer.citations()).hasSize(1);
    }

    /** citations 字段缺失：引用列表为空 */
    @Test
    void treatsMissingCitationsAsEmpty() {
        StubChatModel model = new StubChatModel("{\"text\":\"答案\"}");
        SpringAiAnswerGenerator generator = new SpringAiAnswerGenerator(model, objectMapper);

        GeneratedAnswer answer = generator.generate("q", List.of(candidate("c1", "原文")));

        assertThat(answer.text()).isEqualTo("答案");
        assertThat(answer.citations()).isEmpty();
    }

    /** 引用条目缺字段或字段非文本：跳过该条，保留合法条目 */
    @Test
    void skipsMalformedCitationEntries() {
        StubChatModel model = new StubChatModel("""
                {"text":"答案","citations":[
                  {"chunkId":"c1","quote":"原文"},
                  {"chunkId":123,"quote":"原文"},
                  {"chunkId":"c2","quote":false}]}
                """);
        SpringAiAnswerGenerator generator = new SpringAiAnswerGenerator(model, objectMapper);

        GeneratedAnswer answer = generator.generate("q", List.of(candidate("c1", "原文")));

        assertThat(answer.citations()).hasSize(1);
        assertThat(answer.citations().get(0).chunkId()).isEqualTo("c1");
    }

    /** evidence 为 null：仍可正常生成（证据为空） */
    @Test
    void generatesWithNullEvidence() {
        StubChatModel model = new StubChatModel("{\"text\":\"\",\"citations\":[]}");
        SpringAiAnswerGenerator generator = new SpringAiAnswerGenerator(model, objectMapper);

        GeneratedAnswer answer = generator.generate("q", null);

        assertThat(answer.text()).isEmpty();
        assertThat(answer.citations()).isEmpty();
    }

    /** evidence 含 content 为 null 的候选：拼接时空内容不抛异常 */
    @Test
    void generatesWithNullContentCandidate() {
        StubChatModel model = new StubChatModel("{\"text\":\"答案\"}");
        SpringAiAnswerGenerator generator = new SpringAiAnswerGenerator(model, objectMapper);

        GeneratedAnswer answer = generator.generate("q", List.of(
                new RetrievalCandidate("c-null", null, null, null, null, null, Map.of(),
                        null, null, null, null, null, null, null, null, null,
                        null, DedupeStatus.PENDING, null)));

        assertThat(answer.text()).isEqualTo("答案");
    }

    /** 只有围栏没有内容：按非法 JSON 处理返回空答案 */
    @Test
    void returnsEmptyAnswerWhenOnlyFencePresent() {
        StubChatModel model = new StubChatModel("```");
        SpringAiAnswerGenerator generator = new SpringAiAnswerGenerator(model, objectMapper);

        GeneratedAnswer answer = generator.generate("q", List.of(candidate("c1", "原文")));

        assertThat(answer.text()).isNull();
        assertThat(answer.citations()).isEmpty();
    }

    /** 禁用生成器：直接抛出 GenerationUnavailableException，不拼装伪答案 */
    @Test
    void disabledGeneratorThrowsUnavailable() {
        DisabledAnswerGenerator disabled = new DisabledAnswerGenerator();

        assertThatThrownBy(() -> disabled.generate("q", List.of(candidate("c1", "原文"))))
                .isInstanceOf(GenerationUnavailableException.class)
                .hasMessageContaining("未启用");
    }

    // --- 辅助方法 ---

    /** 构造内容非空的检索候选 */
    private RetrievalCandidate candidate(String chunkId, String content) {
        return RetrievalCandidate.keyword(chunkId, content, 0.6, 1);
    }

    /** 桩聊天模型：返回固定响应并记录收到的 prompt */
    static final class StubChatModel implements ChatModel {

        private final String response;
        private final List<Prompt> receivedPrompts = new ArrayList<>();

        StubChatModel(String response) {
            this.response = response;
        }

        /** 返回收到的 prompt 列表（测试断言用） */
        List<Prompt> receivedPrompts() {
            return receivedPrompts;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            receivedPrompts.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
        }
    }
}
