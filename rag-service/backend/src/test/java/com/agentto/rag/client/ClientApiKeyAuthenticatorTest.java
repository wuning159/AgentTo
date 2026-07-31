package com.agentto.rag.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 调用方 API Key 认证器测试。
 * 验证 HMAC-SHA256 哈希认证流程，不持久化原始密钥。
 */
@ExtendWith(MockitoExtension.class)
class ClientApiKeyAuthenticatorTest {

    private static final String TEST_PEPPER = "test-pepper-for-unit-tests";
    private static final String RAW_KEY = "rag_live_example_secret_key_for_testing";

    @Mock
    private ClientApiKeyRepository keyRepository;

    @Mock
    private ClientApplicationRepository appRepository;

    private ClientApiKeyAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        ClientApiProperties properties = new ClientApiProperties(TEST_PEPPER);
        authenticator = new ClientApiKeyAuthenticator(keyRepository, appRepository, properties);
    }

    /**
     * 活跃的 Key 认证成功，返回正确的调用方主体，不持久化原始密钥。
     */
    @Test
    void authenticatesActiveKeyWithoutPersistingRawSecret() {
        String expectedHash = ClientApiKeyAuthenticator.hmacSha256(TEST_PEPPER, RAW_KEY);

        ClientApiKey key = new ClientApiKey(10L, "rag_live_exampl", expectedHash);
        key.setId(1L);

        ClientApplication app = new ClientApplication("app-a", "应用A");
        app.setId(10L);

        when(keyRepository.findByKeyHashAndStatus(expectedHash, "ACTIVE")).thenReturn(Optional.of(key));
        when(appRepository.findById(10L)).thenReturn(Optional.of(app));

        CallerPrincipal principal = authenticator.authenticate(RAW_KEY);

        assertThat(principal.appId()).isEqualTo(10L);
        assertThat(principal.appUid()).isEqualTo("app-a");
        assertThat(principal.appName()).isEqualTo("应用A");

        // 验证通过哈希查找，不查找原始密钥
        verify(keyRepository).findByKeyHashAndStatus(expectedHash, "ACTIVE");
    }

    /**
     * 已撤销的 Key 被拒绝。
     */
    @Test
    void rejectsRevokedKey() {
        String expectedHash = ClientApiKeyAuthenticator.hmacSha256(TEST_PEPPER, RAW_KEY);

        ClientApiKey key = new ClientApiKey(10L, "rag_live_exampl", expectedHash);
        key.setStatus("REVOKED");
        key.setId(1L);

        when(keyRepository.findByKeyHashAndStatus(expectedHash, "ACTIVE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator.authenticate(RAW_KEY))
                .isInstanceOf(InvalidClientCredentialException.class);

        verify(keyRepository).findByKeyHashAndStatus(expectedHash, "ACTIVE");
    }

    /**
     * 空密钥被拒绝。
     */
    @Test
    void rejectsEmptyKey() {
        assertThatThrownBy(() -> authenticator.authenticate(""))
                .isInstanceOf(InvalidClientCredentialException.class);
    }

    /**
     * 不存在的密钥被拒绝。
     */
    @Test
    void rejectsNonExistentKey() {
        String expectedHash = ClientApiKeyAuthenticator.hmacSha256(TEST_PEPPER, "nonexistent_key");

        when(keyRepository.findByKeyHashAndStatus(expectedHash, "ACTIVE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator.authenticate("nonexistent_key"))
                .isInstanceOf(InvalidClientCredentialException.class);
    }

    /**
     * 调用方应用被禁用时拒绝认证。
     */
    @Test
    void rejectsDisabledApplication() {
        String expectedHash = ClientApiKeyAuthenticator.hmacSha256(TEST_PEPPER, RAW_KEY);

        ClientApiKey key = new ClientApiKey(10L, "rag_live_exampl", expectedHash);
        key.setId(1L);

        ClientApplication app = new ClientApplication("app-a", "应用A");
        app.setId(10L);
        app.setStatus("DISABLED");

        when(keyRepository.findByKeyHashAndStatus(expectedHash, "ACTIVE")).thenReturn(Optional.of(key));
        when(appRepository.findById(10L)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> authenticator.authenticate(RAW_KEY))
                .isInstanceOf(InvalidClientCredentialException.class);
    }

    /**
     * 认证成功后更新最后使用时间。
     */
    @Test
    void updatesLastUsedAtAfterSuccessfulAuthentication() {
        String expectedHash = ClientApiKeyAuthenticator.hmacSha256(TEST_PEPPER, RAW_KEY);

        ClientApiKey key = new ClientApiKey(10L, "rag_live_exampl", expectedHash);
        key.setId(1L);

        ClientApplication app = new ClientApplication("app-a", "应用A");
        app.setId(10L);

        when(keyRepository.findByKeyHashAndStatus(expectedHash, "ACTIVE")).thenReturn(Optional.of(key));
        when(appRepository.findById(10L)).thenReturn(Optional.of(app));

        authenticator.authenticate(RAW_KEY);

        // 验证保存了更新后的 Key（包含 lastUsedAt）
        verify(keyRepository).save(any(ClientApiKey.class));
    }
}
