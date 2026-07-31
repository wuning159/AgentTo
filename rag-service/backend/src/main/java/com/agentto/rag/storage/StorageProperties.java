package com.agentto.rag.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rag.storage")
public record StorageProperties(
        boolean enabled,
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket) {
}
