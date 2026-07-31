package com.agentto.rag.citation;

import java.util.List;

/**
 * 引用真实性校验结果。
 *
 * @param valid          是否全部引用真实有效
 * @param invalidChunkIds 不在本次证据集合中的 chunkId 列表（无则空列表）
 * @param validCitations  通过校验的引用列表（供答案输出使用）
 * @param reason          校验结论说明，多个失败原因以分号分隔
 */
public record CitationValidationResult(
        boolean valid,
        List<String> invalidChunkIds,
        List<Citation> validCitations,
        String reason) {

    /**
     * 紧凑构造器：对列表做防御性拷贝，null 视为空列表。
     *
     * @param valid           是否全部引用真实有效
     * @param invalidChunkIds 无效 chunkId 列表，允许 null
     * @param validCitations  有效引用列表，允许 null
     * @param reason          校验结论说明
     */
    public CitationValidationResult {
        invalidChunkIds = invalidChunkIds == null ? List.of() : List.copyOf(invalidChunkIds);
        validCitations = validCitations == null ? List.of() : List.copyOf(validCitations);
    }
}
