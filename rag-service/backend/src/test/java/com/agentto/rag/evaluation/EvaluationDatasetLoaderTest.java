package com.agentto.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import com.agentto.rag.query.RagQueryDecision;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 评测数据集加载器测试：覆盖正常加载、空行跳过、重复 ID、
 * 空查询、缺失期望决策和未知决策枚举等校验路径。
 */
class EvaluationDatasetLoaderTest {

    @TempDir
    Path tempDir;

    private final EvaluationDatasetLoader loader =
            new EvaluationDatasetLoader(new ObjectMapper(), new DefaultResourceLoader());

    /** 默认基线数据集可完整加载且 ID 唯一 */
    @Test
    void loadsAllBaselineCasesFromClasspath() {
        List<EvaluationCase> cases = loader.loadDefault();

        assertThat(cases).hasSize(30);
        assertThat(cases).extracting(EvaluationCase::id).doesNotHaveDuplicates();
        assertThat(cases).allSatisfy(testCase -> {
            assertThat(testCase.clientAppId()).isEqualTo(10L);
            assertThat(testCase.query()).isNotBlank();
            assertThat(testCase.expectedDecision()).isNotNull();
        });
    }

    /** 数据集覆盖五类场景：路由/无知识库/证据不足/引用/改写命中 */
    @Test
    void baselineDatasetCoversAllFiveScenarioGroups() {
        List<EvaluationCase> cases = loader.loadDefault();

        // 10 条正确路由（期望作答、非改写）
        assertThat(cases).filteredOn(testCase -> testCase.expectedDecision() == RagQueryDecision.ANSWERED
                && !testCase.expectsRewrite()).hasSize(10);
        // 5 条无相关知识库
        assertThat(cases).filteredOn(
                testCase -> testCase.expectedDecision() == RagQueryDecision.NO_RELEVANT_KNOWLEDGE_BASE)
                .hasSize(5)
                .allSatisfy(testCase -> assertThat(testCase.expectedKbIds()).isEmpty());
        // 5 条证据不足
        assertThat(cases).filteredOn(
                testCase -> testCase.expectedDecision() == RagQueryDecision.INSUFFICIENT_EVIDENCE)
                .hasSize(5)
                .allSatisfy(testCase -> {
                    assertThat(testCase.expectedKbIds()).isNotEmpty();
                    assertThat(testCase.expectedChunkIds()).isEmpty();
                });
        // 5 条引用真实性（期望系统拦截无效引用）
        assertThat(cases).filteredOn(
                testCase -> testCase.expectedDecision() == RagQueryDecision.INVALID_CITATION)
                .hasSize(5);
        // 5 条查询改写后命中
        assertThat(cases).filteredOn(EvaluationCase::expectsRewrite)
                .hasSize(5)
                .allSatisfy(testCase -> assertThat(testCase.expectedDecision())
                        .isEqualTo(RagQueryDecision.ANSWERED));
    }

    /** 跳过空行并保持文件行序 */
    @Test
    void skipsBlankLinesAndKeepsOrder() throws IOException {
        Path file = writeDataset(tempDir, "case-b.jsonl",
                "{\"id\":\"b-1\",\"clientAppId\":10,\"query\":\"q1\",\"expectedKbIds\":[101],\"expectedChunkIds\":[],\"expectedDecision\":\"ANSWERED\"}",
                "",
                "   ",
                "{\"id\":\"b-2\",\"clientAppId\":10,\"query\":\"q2\",\"expectedKbIds\":[],\"expectedChunkIds\":[],\"expectedDecision\":\"NO_RELEVANT_KNOWLEDGE_BASE\"}");

        List<EvaluationCase> cases = loader.load(file.toUri().toString());

        assertThat(cases).extracting(EvaluationCase::id).containsExactly("b-1", "b-2");
    }

    /** 重复用例 ID 被拒绝 */
    @Test
    void rejectsDuplicateIds() throws IOException {
        Path file = writeDataset(tempDir, "duplicate.jsonl",
                "{\"id\":\"dup-1\",\"clientAppId\":10,\"query\":\"q1\",\"expectedKbIds\":[101],\"expectedChunkIds\":[],\"expectedDecision\":\"ANSWERED\"}",
                "{\"id\":\"dup-1\",\"clientAppId\":10,\"query\":\"q2\",\"expectedKbIds\":[],\"expectedChunkIds\":[],\"expectedDecision\":\"NO_RELEVANT_KNOWLEDGE_BASE\"}");

        assertThatThrownBy(() -> loader.load(file.toUri().toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ID 重复");
    }

    /** 空查询被拒绝 */
    @Test
    void rejectsBlankQuery() throws IOException {
        Path file = writeDataset(tempDir, "blank-query.jsonl",
                "{\"id\":\"blank-1\",\"clientAppId\":10,\"query\":\"  \",\"expectedKbIds\":[101],\"expectedChunkIds\":[],\"expectedDecision\":\"ANSWERED\"}");

        assertThatThrownBy(() -> loader.load(file.toUri().toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 缺失期望决策被拒绝 */
    @Test
    void rejectsMissingExpectedDecision() throws IOException {
        Path file = writeDataset(tempDir, "missing-decision.jsonl",
                "{\"id\":\"no-decision-1\",\"clientAppId\":10,\"query\":\"预算怎么审批\",\"expectedKbIds\":[101],\"expectedChunkIds\":[]}");

        assertThatThrownBy(() -> loader.load(file.toUri().toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 未知决策枚举被拒绝（格式非法） */
    @Test
    void rejectsUnknownDecisionEnum() throws IOException {
        Path file = writeDataset(tempDir, "unknown-decision.jsonl",
                "{\"id\":\"unknown-1\",\"clientAppId\":10,\"query\":\"预算怎么审批\",\"expectedKbIds\":[101],\"expectedChunkIds\":[],\"expectedDecision\":\"ANSWER\"}");

        assertThatThrownBy(() -> loader.load(file.toUri().toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("格式非法");
    }

    /** 数据集不存在时抛出明确异常 */
    @Test
    void rejectsMissingDataset() {
        assertThatThrownBy(() -> loader.load("classpath:rag-eval/not-exist.jsonl"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");
    }

    /** 写入 JSONL 测试文件 */
    private Path writeDataset(Path dir, String name, String... lines) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return file;
    }
}
