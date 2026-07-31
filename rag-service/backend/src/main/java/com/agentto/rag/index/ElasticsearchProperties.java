package com.agentto.rag.index;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rag.elasticsearch")
public record ElasticsearchProperties(
        boolean enabled,
        String url,
        String username,
        String password,
        String index,
        int dimensions) {
}
