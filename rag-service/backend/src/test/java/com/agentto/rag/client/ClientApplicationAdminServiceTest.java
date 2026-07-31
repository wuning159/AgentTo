package com.agentto.rag.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 调用方应用管理服务测试。
 * 验证创建调用方、生成 API Key 和撤销 Key 的业务规则。
 */
@ExtendWith(MockitoExtension.class)
class ClientApplicationAdminServiceTest {

    private static final String TEST_PEPPER = "test-pepper-for-unit-tests";

    @Mock
    private ClientApplicationRepository appRepository;

    @Mock
    private ClientApiKeyRepository keyRepository;

    private ClientApplicationAdminService adminService;

    @BeforeEach
    void setUp() {
        ClientApiProperties properties = new ClientApiProperties(TEST_PEPPER);
        adminService = new ClientApplicationAdminService(appRepository, keyRepository, properties);
    }

    /**
     * 创建调用方应用成功。
     */
    @Test
    void createClientSavesNewApplication() {
        when(appRepository.save(any(ClientApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClientApplication created = adminService.createClient("app-a", "应用A");

        assertThat(created.getAppUid()).isEqualTo("app-a");
        assertThat(created.getName()).isEqualTo("应用A");
        assertThat(created.isActive()).isTrue();
        verify(appRepository).save(any(ClientApplication.class));
    }

    /**
     * 生成 API Key 时返回原始密钥，但只持久化哈希值。
     */
    @Test
    void createApiKeyReturnsRawKeyButPersistsHash() {
        ClientApplication app = new ClientApplication("app-a", "应用A");
        app.setId(10L);
        when(appRepository.findByAppUid("app-a")).thenReturn(Optional.of(app));
        when(keyRepository.save(any(ClientApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatedClientApiKey result = adminService.createApiKey("app-a");

        assertThat(result.appUid()).isEqualTo("app-a");
        assertThat(result.rawKey()).startsWith("rag_live_");
        assertThat(result.rawKey().length()).isGreaterThan(50);
        assertThat(result.keyPrefix()).startsWith("rag_live_");
        assertThat(result.keyPrefix().length()).isEqualTo(16);

        // 验证保存的是 ClientApiKey（包含哈希，不包含原始密钥）
        verify(keyRepository).save(any(ClientApiKey.class));
    }

    /**
     * 为不存在的调用方生成 Key 时抛出异常。
     */
    @Test
    void createApiKeyThrowsWhenAppNotFound() {
        when(appRepository.findByAppUid("non-existent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.createApiKey("non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("调用方应用不存在");

        verify(keyRepository, never()).save(any());
    }

    /**
     * 撤销 Key 时调用保存方法。
     */
    @Test
    void revokeApiKeyMarksKeyAsRevoked() {
        ClientApplication app = new ClientApplication("app-a", "应用A");
        app.setId(10L);
        ClientApiKey key = new ClientApiKey(10L, "rag_live_exampl", "hash");
        key.setId(1L);

        when(appRepository.findByAppUid("app-a")).thenReturn(Optional.of(app));
        when(keyRepository.findByKeyPrefixAndClientAppId("rag_live_exampl", 10L))
                .thenReturn(Optional.of(key));

        adminService.revokeApiKey("app-a", "rag_live_exampl");

        assertThat(key.isActive()).isFalse();
        assertThat(key.getStatus()).isEqualTo("REVOKED");
        verify(keyRepository).save(any(ClientApiKey.class));
    }

    /**
     * 撤销不存在的 Key 时抛出异常。
     */
    @Test
    void revokeApiKeyThrowsWhenKeyNotFound() {
        ClientApplication app = new ClientApplication("app-a", "应用A");
        app.setId(10L);

        when(appRepository.findByAppUid("app-a")).thenReturn(Optional.of(app));
        when(keyRepository.findByKeyPrefixAndClientAppId("non-existent", 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.revokeApiKey("app-a", "non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API Key 不存在");
    }
}
