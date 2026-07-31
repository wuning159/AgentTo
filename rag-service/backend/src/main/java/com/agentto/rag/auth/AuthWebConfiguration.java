package com.agentto.rag.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthWebConfiguration implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public AuthWebConfiguration(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 管理端认证只拦截 /api/admin/** 和 /api/auth/**
        // /api/v1/** 由 ClientApiKeyInterceptor 处理
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/admin/**", "/api/auth/**")
                .excludePathPatterns("/api/auth/login");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5174", "http://127.0.0.1:5174")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id")
                .allowCredentials(false);
    }
}
