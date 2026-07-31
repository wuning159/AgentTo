package com.agentto.rag.citation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.agentto.rag.retrieval.RetrievalCandidate;

/**
 * 引用真实性校验器：纯函数，只证明"引用来源真实"。
 *
 * 校验规则（按顺序）：
 * 1. chunkId 必须属于本次证据集合
 * 2. quote 经过 Unicode NFKC、空白折叠后必须是对应切片内容的子串
 * 3. 引用不得出现空 quote
 * 4. 答案非空时至少有一个有效引用
 *
 * 该校验不声称自动证明答案中每个自然语言结论都被证据支持，
 * 只保证输出引用全部指向真实检索到的原文。
 */
@Component
public class CitationValidator {

    /**
     * 校验生成答案的所有引用。
     *
     * @param answer   生成答案，text 或 citations 允许为 null
     * @param evidence 本次检索的证据集合（已通过证据门），允许为 null
     * @return 校验结果，包含有效引用列表和无效 chunkId 列表
     */
    public CitationValidationResult validate(GeneratedAnswer answer, List<RetrievalCandidate> evidence) {
        // 证据映射：chunkId -> 归一化后的切片内容
        Map<String, String> evidenceByChunk = new HashMap<>();
        if (evidence != null) {
            for (RetrievalCandidate candidate : evidence) {
                evidenceByChunk.put(candidate.chunkId(), normalize(candidate.content()));
            }
        }

        // 引用列表为空：只有答案也为空白才视为有效（空答案不泄露任何信息）
        if (answer.citations() == null || answer.citations().isEmpty()) {
            boolean ok = answer.text() == null || answer.text().isBlank();
            return new CitationValidationResult(
                    ok, List.of(), List.of(),
                    ok ? "无引用且答案为空白" : "答案非空但没有引用");
        }

        List<String> invalidChunkIds = new ArrayList<>();
        List<Citation> validCitations = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        for (Citation citation : answer.citations()) {
            String chunkId = citation.chunkId();
            String quote = citation.quote();

            // 规则 3：空 quote 无效
            if (quote == null || quote.isBlank()) {
                reasons.add("引用 " + chunkId + " 的 quote 为空");
                continue;
            }

            // 规则 1：chunkId 必须在证据集合中
            String normalizedContent = evidenceByChunk.get(chunkId);
            if (normalizedContent == null) {
                invalidChunkIds.add(chunkId);
                reasons.add("chunkId " + chunkId + " 不在证据集合中");
                continue;
            }

            // 规则 2：quote 归一化后必须是切片内容子串
            if (!normalizedContent.contains(normalize(quote))) {
                reasons.add("quote 与切片 " + chunkId + " 原文不一致");
                continue;
            }

            validCitations.add(citation);
        }

        // 规则 4：答案非空时至少有一个有效引用；且不得存在无效引用
        boolean hasText = answer.text() != null && !answer.text().isBlank();
        boolean valid = validCitations.size() == answer.citations().size()
                && (!hasText || !validCitations.isEmpty());

        String reason = reasons.isEmpty() ? "全部引用真实有效" : String.join("；", reasons);
        return new CitationValidationResult(valid, invalidChunkIds, validCitations, reason);
    }

    /**
     * 归一化文本：Unicode NFKC + 空白折叠（含全角空格/不间断空格）+ 去首尾空白。
     * 用于将模型引用的原文与切片内容做等价比较，容忍字体差异和排版空白。
     */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Zs}\\t\\n\\x0B\\f\\r]+", " ")
                .trim();
    }
}
