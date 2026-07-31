package com.agentto.rag.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AdminPrincipal principal = authService.authenticate(AuthRequestContext.bearerToken(request));
        request.setAttribute(AuthRequestContext.PRINCIPAL_ATTRIBUTE, principal);
        return true;
    }
}
