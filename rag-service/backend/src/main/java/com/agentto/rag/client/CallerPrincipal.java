package com.agentto.rag.client;

/**
 * 调用方主体身份。
 * 通过 API Key 认证后填充，包含调用方应用的核心标识信息。
 */
public record CallerPrincipal(Long appId, String appUid, String appName) {
}
