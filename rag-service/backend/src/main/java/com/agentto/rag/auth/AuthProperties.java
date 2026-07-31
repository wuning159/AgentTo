package com.agentto.rag.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rag.auth")
public record AuthProperties(
        boolean bootstrapEnabled,
        String bootstrapUsername,
        String bootstrapPassword,
        long sessionHours) {
}
