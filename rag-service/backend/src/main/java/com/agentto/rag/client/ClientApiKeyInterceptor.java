package com.agentto.rag.client;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 调用方 API Key 拦截器。
 * 只拦截 /api/v1/** 路径，通过 Bearer Token 认证调用方身份。
 * 缺少或无效的 Token 返回 HTTP 401。
 * 管理端 /api/admin/** 继续使用现有管理员 Session，不受此拦截器影响。
 */
@Component
public class ClientApiKeyInterceptor implements HandlerInterceptor {

    private final ClientApiKeyAuthenticator authenticator;

    public ClientApiKeyInterceptor(ClientApiKeyAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = CallerRequestContext.bearerToken(request);

        // 空 Token 返回 401
        if (token.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"缺少有效的 Bearer Token\"}");
            return false;
        }

        try {
            // 认证并设置调用方主体
            CallerPrincipal principal = authenticator.authenticate(token);
            request.setAttribute(CallerRequestContext.PRINCIPAL_ATTRIBUTE, principal);
            return true;
        } catch (InvalidClientCredentialException exception) {
            // 无效凭证返回 401
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"API Key 无效或已过期\"}");
            return false;
        }
    }
}
