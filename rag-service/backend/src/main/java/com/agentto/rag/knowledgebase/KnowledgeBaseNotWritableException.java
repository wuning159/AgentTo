package com.agentto.rag.knowledgebase;

/**
 * 知识库不可写异常。
 * 在上传文件到不存在或非活跃的知识库时抛出。
 */
public class KnowledgeBaseNotWritableException extends RuntimeException {

    public KnowledgeBaseNotWritableException(String message) {
        super(message);
    }
}
