package com.agentto.rag.auth;

import jakarta.servlet.http.HttpServletRequest;

public final class AuthRequestContext {

    static final String PRINCIPAL_ATTRIBUTE = "rag.adminPrincipal";

    private AuthRequestContext() {
    }

    public static AdminPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminPrincipal principal) {
            return principal;
        }
        throw new IllegalStateException("当前请求没有管理员身份");
    }

    public static String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "";
        }
        return authorization.substring(7).trim();
    }
}
