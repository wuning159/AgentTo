package com.agentto.rag.query;

/**
 * 答案生成不可用异常。
 *
 * 当未配置 ChatModel（没有 Spring AI 模型 Bean）时，
 * DisabledAnswerGenerator 抛出本异常，编排层捕获后返回 GENERATION_UNAVAILABLE，
 * 绝不把检索片段拼装成伪答案。
 */
public class GenerationUnavailableException extends RuntimeException {

    /**
     * 构造异常。
     *
     * @param message 异常说明
     */
    public GenerationUnavailableException(String message) {
        super(message);
    }
}
