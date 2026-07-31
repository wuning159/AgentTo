package com.agentto.rag.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Spring AI 查询改写器测试。
 * 使用桩 ChatModel 覆盖：空白折叠、原查询回退、空输出、超长拒绝、模型异常降级，
 * 以及禁用实现和空入参处理。
 */
class SpringAiQueryRewriterTest {

    /** 正常改写：模型返回带多余空白的改写查询，折叠后返回 */
    @Test
    void returnsCollapsedRewrite() {
        StubChatModel model = new StubChatModel(() -> "  预算审批流程  需要哪些材料\n（2026版）  ");
        SpringAiQueryRewriter rewriter = new SpringAiQueryRewriter(model);

        Optional<String> result = rewriter.rewrite("预算审批需要哪些材料", "证据不足");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("预算审批流程 需要哪些材料 （2026版）");
        // 模型确实收到了原始查询
        assertThat(model.receivedPrompts().get(0).getContents()).contains("预算审批需要哪些材料");
    }

    /** 模型返回原查询：视为无实质改写，返回 empty */
    @Test
    void returnsEmptyWhenRewriteSameAsOriginal() {
        StubChatModel model = new StubChatModel(() -> "预算审批需要哪些材料");
        SpringAiQueryRewriter rewriter = new SpringAiQueryRewriter(model);

        Optional<String> result = rewriter.rewrite("预算审批需要哪些材料", "证据不足");

        assertThat(result).isEmpty();
    }

    /** 模型返回仅空白：RewriteQueryTransformer 回退为原查询，视为无改写 */
    @Test
    void returnsEmptyWhenModelOutputIsBlank() {
        StubChatModel model = new StubChatModel(() -> "   \n  ");
        SpringAiQueryRewriter rewriter = new SpringAiQueryRewriter(model);

        Optional<String> result = rewriter.rewrite("预算审批需要哪些材料", "证据不足");

        assertThat(result).isEmpty();
    }

    /** 模型返回 null：回退为原查询，视为无改写 */
    @Test
    void returnsEmptyWhenModelOutputIsNull() {
        StubChatModel model = new StubChatModel(() -> null);
        SpringAiQueryRewriter rewriter = new SpringAiQueryRewriter(model);

        Optional<String> result = rewriter.rewrite("预算审批需要哪些材料", "证据不足");

        assertThat(result).isEmpty();
    }

    /** 改写结果超过 1000 字符：视为不可信输出拒绝 */
    @Test
    void rejectsRewriteLongerThanLimit() {
        String tooLong = "预算审批".repeat(250); // 1000 字符
        StubChatModel model = new StubChatModel(() -> tooLong + "额外字符");
        SpringAiQueryRewriter rewriter = new SpringAiQueryRewriter(model);

        Optional<String> result = rewriter.rewrite("预算审批", "证据不足");

        assertThat(result).isEmpty();
    }

    /** 改写结果恰好 1000 字符：边界允许 */
    @Test
    void acceptsRewriteAtLimitBoundary() {
        String exactly = "预".repeat(1000);
        StubChatModel model = new StubChatModel(() -> exactly);
        SpringAiQueryRewriter rewriter = new SpringAiQueryRewriter(model);

        Optional<String> result = rewriter.rewrite("预算", "证据不足");

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1000);
    }

    /** 模型异常（超时/限流）：降级返回 empty */
    @Test
    void returnsEmptyWhenModelThrows() {
        StubChatModel model = new StubChatModel(() -> {
            throw new RuntimeException("模型调用超时");
        });
        SpringAiQueryRewriter rewriter = new SpringAiQueryRewriter(model);

        Optional<String> result = rewriter.rewrite("预算审批", "证据不足");

        assertThat(result).isEmpty();
    }

    /** 原始查询为 null：无需改写 */
    @Test
    void returnsEmptyForNullOriginalQuery() {
        StubChatModel model = new StubChatModel(() -> "改写结果");
        SpringAiQueryRewriter rewriter = new SpringAiQueryRewriter(model);

        Optional<String> result = rewriter.rewrite(null, "证据不足");

        assertThat(result).isEmpty();
    }

    /** 原始查询为空白：无需改写 */
    @Test
    void returnsEmptyForBlankOriginalQuery() {
        StubChatModel model = new StubChatModel(() -> "改写结果");
        SpringAiQueryRewriter rewriter = new SpringAiQueryRewriter(model);

        Optional<String> result = rewriter.rewrite("   ", "证据不足");

        assertThat(result).isEmpty();
    }

    /** 禁用改写器：永远返回 empty */
    @Test
    void disabledRewriterAlwaysReturnsEmpty() {
        DisabledQueryRewriter rewriter = new DisabledQueryRewriter();

        assertThat(rewriter.rewrite("预算审批", "证据不足")).isEmpty();
    }

    // --- 辅助类 ---

    /** 桩聊天模型：按响应器返回内容并记录收到的 prompt；响应器可抛异常模拟模型故障 */
    static final class StubChatModel implements ChatModel {

        private final Supplier<String> responder;
        private final List<Prompt> receivedPrompts = new ArrayList<>();

        StubChatModel(Supplier<String> responder) {
            this.responder = responder;
        }

        /** 返回收到的 prompt 列表（测试断言用） */
        List<Prompt> receivedPrompts() {
            return receivedPrompts;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            receivedPrompts.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(responder.get()))));
        }
    }
}
