package com.agentto.rag.client;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 调用方请求上下文工具。
 * 从 HTTP 请求属性中提取或设置 CallerPrincipal。
 */
public final class CallerRequestContext {

    static final String PRINCIPAL_ATTRIBUTE = "rag.callerPrincipal";

    private CallerRequestContext() {
    }

    /**
     * 从请求中提取调用方主体。
     *
     * @param request HTTP 请求
     * @return 调用方主体，不存在时抛出异常
     */
    public static CallerPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(PRINCIPAL_ATTRIBUTE);
        if (value instanceof CallerPrincipal principal) {
            return principal;
        }
        throw new IllegalStateException("当前请求没有调用方身份");
    }

    /**
     * 从请求中提取 Bearer Token。
     *
     * @param request HTTP 请求
     * @return Token 字符串，不存在时返回空字符串
     */
    public static String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "";
        }
        return authorization.substring(7).trim();
    }
}
