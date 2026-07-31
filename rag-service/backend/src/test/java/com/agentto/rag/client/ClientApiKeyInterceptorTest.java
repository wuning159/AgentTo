package com.agentto.rag.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 调用方 API Key 拦截器测试。
 * 验证 /api/v1/** 路径的 Bearer Token 认证行为。
 */
@ExtendWith(MockitoExtension.class)
class ClientApiKeyInterceptorTest {

    @Mock
    private ClientApiKeyAuthenticator authenticator;

    private ClientApiKeyInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ClientApiKeyInterceptor(authenticator);
    }

    /**
     * 缺少 Bearer Token 时返回 401。
     */
    @Test
    void returnsUnauthorizedWhenBearerTokenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/rag/query");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    /**
     * 无效的 Bearer Token 返回 401。
     */
    @Test
    void returnsUnauthorizedWhenTokenInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/rag/query");
        request.addHeader("Authorization", "Bearer invalid_key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authenticator.authenticate("invalid_key"))
                .thenThrow(new InvalidClientCredentialException());

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    /**
     * 有效的 Bearer Token 认证成功，设置调用方主体到请求属性。
     */
    @Test
    void setsPrincipalWhenTokenValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/rag/query");
        request.addHeader("Authorization", "Bearer valid_key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        CallerPrincipal principal = new CallerPrincipal(10L, "app-a", "应用A");
        when(authenticator.authenticate("valid_key")).thenReturn(principal);

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        Object stored = request.getAttribute(CallerRequestContext.PRINCIPAL_ATTRIBUTE);
        assertThat(stored).isSameAs(principal);
    }
}
