package com.agentto.rag.client;

/**
 * 调用方凭证无效异常。
 * 当 API Key 不存在、已撤销或已过期时抛出。
 */
public class InvalidClientCredentialException extends RuntimeException {

    public InvalidClientCredentialException() {
        super("调用方凭证无效或已过期");
    }

    public InvalidClientCredentialException(String message) {
        super(message);
    }
}
