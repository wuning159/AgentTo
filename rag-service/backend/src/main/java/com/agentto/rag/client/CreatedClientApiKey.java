package com.agentto.rag.client;

/**
 * 创建 API Key 后返回给调用方的 DTO。
 * 包含完整原始密钥，仅在创建时返回一次，后续不再可见。
 */
public record CreatedClientApiKey(String appUid, String keyPrefix, String rawKey) {
}
