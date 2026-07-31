package com.agentto.rag.client;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 调用方 API Key 认证 Web 配置。
 * 注册 ClientApiKeyInterceptor，只拦截 /api/v1/** 路径。
 */
@Configuration
public class ClientApiWebConfiguration implements WebMvcConfigurer {

    private final ClientApiKeyInterceptor clientApiKeyInterceptor;

    public ClientApiWebConfiguration(ClientApiKeyInterceptor clientApiKeyInterceptor) {
        this.clientApiKeyInterceptor = clientApiKeyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只拦截公共 RAG API 路径
        registry.addInterceptor(clientApiKeyInterceptor)
                .addPathPatterns("/api/v1/**");
    }
}
