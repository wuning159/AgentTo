package com.agentto.rag.citation;

import java.util.List;

/**
 * 生成答案：由答案生成器产出，包含答案文本和引用列表。
 *
 * @param text      答案文本，证据不足时模型应返回空白文本
 * @param citations 引用列表，答案中每个主要结论应至少对应一个引用
 */
public record GeneratedAnswer(String text, List<Citation> citations) {

    /**
     * 紧凑构造器：对引用列表做防御性拷贝，避免外部修改；null 视为空列表。
     *
     * @param text      答案文本
     * @param citations 引用列表，允许 null
     */
    public GeneratedAnswer {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
