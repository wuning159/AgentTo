package com.agentto.rag.citation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentto.rag.retrieval.DedupeStatus;
import com.agentto.rag.retrieval.RetrievalCandidate;

/**
 * 引用真实性校验器测试。
 * 覆盖 chunkId 归属、quote 子串（NFKC + 空白折叠）、空 quote、答案非空必须有引用等规则。
 */
class CitationValidatorTest {

    private CitationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CitationValidator();
    }

    /** 全部引用真实有效时校验通过，并保留有效引用 */
    @Test
    void acceptsAllValidCitations() {
        GeneratedAnswer answer = new GeneratedAnswer("预算审批分为三步。",
                List.of(
                        new Citation("c1", "预算审批分为三步"),
                        new Citation("c2", "单笔超五十万需总经理审批")));
        List<RetrievalCandidate> evidence = List.of(
                candidate("c1", "预算审批分为三步。第一步部门申报。"),
                candidate("c2", "单笔超五十万需总经理审批。"));

        CitationValidationResult result = validator.validate(answer, evidence);

        assertThat(result.valid()).isTrue();
        assertThat(result.validCitations()).hasSize(2);
        assertThat(result.invalidChunkIds()).isEmpty();
        assertThat(result.reason()).isEqualTo("全部引用真实有效");
    }

    /** 引用指向未检索到的切片：校验失败并记录 invalidChunkId */
    @Test
    void rejectsCitationToChunkThatWasNotRetrieved() {
        GeneratedAnswer answer = new GeneratedAnswer("答案",
                List.of(new Citation("invented", "不存在")));
        List<RetrievalCandidate> evidence = List.of(candidate("real-1", "真实制度内容"));

        CitationValidationResult result = validator.validate(answer, evidence);

        assertThat(result.valid()).isFalse();
        assertThat(result.invalidChunkIds()).containsExactly("invented");
        assertThat(result.reason()).contains("不在证据集合中");
    }

    /** quote 不在切片原文中：校验失败 */
    @Test
    void rejectsQuoteThatDoesNotExistInTheSourceChunk() {
        GeneratedAnswer answer = new GeneratedAnswer("答案",
                List.of(new Citation("real-1", "伪造原文")));
        List<RetrievalCandidate> evidence = List.of(candidate("real-1", "真实制度内容"));

        CitationValidationResult result = validator.validate(answer, evidence);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("原文不一致");
    }

    /** quote 为空或空白：校验失败 */
    @Test
    void rejectsEmptyQuote() {
        GeneratedAnswer answer = new GeneratedAnswer("答案",
                List.of(new Citation("real-1", "   ")));
        List<RetrievalCandidate> evidence = List.of(candidate("real-1", "真实制度内容"));

        CitationValidationResult result = validator.validate(answer, evidence);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("quote 为空");
    }

    /** quote 为 null：校验失败 */
    @Test
    void rejectsNullQuote() {
        GeneratedAnswer answer = new GeneratedAnswer("答案",
                List.of(new Citation("real-1", null)));
        List<RetrievalCandidate> evidence = List.of(candidate("real-1", "真实制度内容"));

        CitationValidationResult result = validator.validate(answer, evidence);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("quote 为空");
    }

    /** NFKC 归一化：全角字符与半角等价时校验通过 */
    @Test
    void acceptsQuoteAfterNfkcNormalization() {
        // 切片内容是全角括号（2026年度），引用用半角括号
        GeneratedAnswer answer = new GeneratedAnswer("答案",
                List.of(new Citation("c1", "预算(2026年度)说明")));
        List<RetrievalCandidate> evidence = List.of(candidate("c1", "预算（2026年度）说明"));

        CitationValidationResult result = validator.validate(answer, evidence);

        assertThat(result.valid()).isTrue();
    }

    /** 空白折叠：换行折叠为空格后与原文匹配时校验通过 */
    @Test
    void acceptsQuoteAfterWhitespaceCollapse() {
        GeneratedAnswer answer = new GeneratedAnswer("答案",
                List.of(new Citation("c1", "预算审批\n流程说明")));
        List<RetrievalCandidate> evidence = List.of(candidate("c1", "预算审批 流程说明"));

        CitationValidationResult result = validator.validate(answer, evidence);

        assertThat(result.valid()).isTrue();
    }

    /** 答案非空但没有引用：校验失败 */
    @Test
    void rejectsAnswerWithoutAnyCitation() {
        GeneratedAnswer answer = new GeneratedAnswer("有内容的答案", List.of());
        List<RetrievalCandidate> evidence = List.of(candidate("c1", "预算审批"));

        CitationValidationResult result = validator.validate(answer, evidence);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("答案非空但没有引用");
    }

    /** 答案空白且无引用：校验通过（空答案不泄露信息） */
    @Test
    void acceptsBlankAnswerWithoutCitation() {
        GeneratedAnswer answer = new GeneratedAnswer("", List.of());
        List<RetrievalCandidate> evidence = List.of(candidate("c1", "预算审批"));

        CitationValidationResult result = validator.validate(answer, evidence);

        assertThat(result.valid()).isTrue();
        assertThat(result.reason()).contains("无引用且答案为空白");
    }

    /** citations 为 null 且 text 为 null：GeneratedAnswer 防御性构造后视为空答案，校验通过 */
    @Test
    void acceptsNullCitationsWithNullText() {
        GeneratedAnswer answer = new GeneratedAnswer(null, null);
        List<RetrievalCandidate> evidence = List.of(candidate("c1", "预算审批"));

        CitationValidationResult result = validator.validate(answer, evidence);

        assertThat(answer.citations()).isEmpty();
        assertThat(result.valid()).isTrue();
    }

    /** evidence 为 null：任何 chunkId 都不在证据集合中 */
    @Test
    void rejectsAllCitationsWhenEvidenceMissing() {
        GeneratedAnswer answer = new GeneratedAnswer("答案",
                List.of(new Citation("c1", "预算审批")));

        CitationValidationResult result = validator.validate(answer, null);

        assertThat(result.valid()).isFalse();
        assertThat(result.invalidChunkIds()).containsExactly("c1");
    }

    /** 切片 content 为 null：归一化后为空串，任意非空 quote 都无法匹配 */
    @Test
    void rejectsQuoteWhenChunkContentNull() {
        GeneratedAnswer answer = new GeneratedAnswer("答案",
                List.of(new Citation("c-null", "任何原文")));
        List<RetrievalCandidate> evidence = List.of(
                new RetrievalCandidate("c-null", null, null, null, null, null, Map.of(),
                        null, null, null, null, null, null, null, null, null,
                        null, DedupeStatus.PENDING, null));

        CitationValidationResult result = validator.validate(answer, evidence);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("原文不一致");
    }

    /** 混合引用：一条有效一条无效，整体校验失败，有效引用仍被保留 */
    @Test
    void rejectsMixedCitationsAndKeepsValidOnes() {
        GeneratedAnswer answer = new GeneratedAnswer("答案",
                List.of(
                        new Citation("c1", "预算审批分为三步"),
                        new Citation("invented", "编造内容")));
        List<RetrievalCandidate> evidence = List.of(candidate("c1", "预算审批分为三步"));

        CitationValidationResult result = validator.validate(answer, evidence);

        assertThat(result.valid()).isFalse();
        assertThat(result.validCitations()).extracting(Citation::chunkId).containsExactly("c1");
        assertThat(result.invalidChunkIds()).containsExactly("invented");
    }

    /** 答案非空且全部引用有效：满足"答案非空至少有引用"规则 */
    @Test
    void acceptsNonBlankAnswerWithValidCitation() {
        GeneratedAnswer answer = new GeneratedAnswer("预算审批分为三步。",
                List.of(new Citation("c1", "预算审批分为三步")));
        List<RetrievalCandidate> evidence = List.of(candidate("c1", "预算审批分为三步。第一步部门申报。"));

        CitationValidationResult result = validator.validate(answer, evidence);

        assertThat(result.valid()).isTrue();
    }

    // --- 辅助方法 ---

    /** 构造内容非空的检索候选 */
    private RetrievalCandidate candidate(String chunkId, String content) {
        return RetrievalCandidate.keyword(chunkId, content, 0.6, 1);
    }
}
