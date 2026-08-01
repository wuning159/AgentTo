package com.agentto.rag.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;

import com.agentto.rag.client.ClientApplication;
import com.agentto.rag.client.ClientApplicationRepository;
import com.agentto.rag.common.api.BusinessException;

/**
 * 知识库管理服务测试。
 * 验证创建知识库、更新画像、共享授权管理的业务规则。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseAdminServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private KnowledgeBaseGrantRepository grantRepository;

    @Mock
    private ClientApplicationRepository clientApplicationRepository;

    @InjectMocks
    private JpaKnowledgeBaseAdminService service;

    /**
     * 创建知识库时，所有者应用必须存在。
     */
    @Test
    void createKnowledgeBaseThrowsWhenOwnerAppNotFound() {
        when(clientApplicationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createKnowledgeBase("测试KB", "描述", "PRIVATE", 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("所有者应用不存在")
                .satisfies(exception -> {
                    BusinessException business = (BusinessException) exception;
                    assertThat(business.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(business.code()).isEqualTo("OWNER_NOT_FOUND");
                });

        verify(knowledgeBaseRepository, never()).save(any());
    }

    /**
     * 创建知识库成功时，返回带 ACTIVE 状态和初始 profileVersion=1 的实体。
     */
    @Test
    void createKnowledgeBaseSavesWithActiveStatusAndInitialProfileVersion() {
        ClientApplication owner = new ClientApplication("app-a", "应用A");
        owner.setId(10L);
        when(clientApplicationRepository.findById(10L)).thenReturn(Optional.of(owner));
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBase created = service.createKnowledgeBase("财务知识库", "财务相关文档", "PRIVATE", 10L);

        assertThat(created.getKbUid()).isNotBlank();
        assertThat(created.getName()).isEqualTo("财务知识库");
        assertThat(created.getDescription()).isEqualTo("财务相关文档");
        assertThat(created.isShared()).isFalse();
        assertThat(created.isActive()).isTrue();
        assertThat(created.getProfileVersion()).isEqualTo(1);
        assertThat(created.getOwnerAppId()).isEqualTo(10L);
        verify(knowledgeBaseRepository).save(any(KnowledgeBase.class));
    }

    /**
     * 更新画像时，profileVersion 原子递增。
     */
    @Test
    void updateProfileIncrementsProfileVersion() {
        KnowledgeBase kb = new KnowledgeBase("kb-001", "测试KB", "SHARED", 10L);
        kb.setId(1L);
        kb.setStatus("ACTIVE");
        int before = kb.getProfileVersion();

        when(knowledgeBaseRepository.findByKbUid("kb-001")).thenReturn(Optional.of(kb));
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KnowledgeBase updated = service.updateProfile("kb-001", "新描述");

        assertThat(updated.getDescription()).isEqualTo("新描述");
        assertThat(updated.getProfileVersion()).isEqualTo(before + 1);
    }

    /**
     * 更新画像时，知识库不存在应抛出异常。
     */
    @Test
    void updateProfileThrowsWhenKbNotFound() {
        when(knowledgeBaseRepository.findByKbUid("not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile("not-exist", "描述"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识库不存在")
                .satisfies(exception -> {
                    BusinessException business = (BusinessException) exception;
                    assertThat(business.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(business.code()).isEqualTo("KNOWLEDGE_BASE_NOT_FOUND");
                });
    }

    /**
     * 为 SHARED 知识库添加授权成功。
     */
    @Test
    void addGrantSucceedsForSharedKnowledgeBase() {
        KnowledgeBase kb = new KnowledgeBase("kb-001", "共享KB", "SHARED", 10L);
        kb.setId(1L);
        kb.setStatus("ACTIVE");
        ClientApplication grantee = new ClientApplication("app-b", "应用B");
        grantee.setId(20L);

        when(knowledgeBaseRepository.findByKbUid("kb-001")).thenReturn(Optional.of(kb));
        when(clientApplicationRepository.findByAppUid("app-b")).thenReturn(Optional.of(grantee));
        when(grantRepository.existsByKnowledgeBaseIdAndClientAppId(1L, 20L)).thenReturn(false);

        service.addGrant("kb-001", "app-b");

        verify(grantRepository).save(any(KnowledgeBaseGrant.class));
    }

    /**
     * 为 PRIVATE 知识库添加授权应失败。
     */
    @Test
    void addGrantThrowsForPrivateKnowledgeBase() {
        KnowledgeBase kb = new KnowledgeBase("kb-002", "私有KB", "PRIVATE", 10L);
        kb.setId(2L);
        kb.setStatus("ACTIVE");
        ClientApplication grantee = new ClientApplication("app-b", "应用B");
        grantee.setId(20L);

        when(knowledgeBaseRepository.findByKbUid("kb-002")).thenReturn(Optional.of(kb));
        when(clientApplicationRepository.findByAppUid("app-b")).thenReturn(Optional.of(grantee));

        assertThatThrownBy(() -> service.addGrant("kb-002", "app-b"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("私有知识库")
                .satisfies(exception -> {
                    BusinessException business = (BusinessException) exception;
                    assertThat(business.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(business.code()).isEqualTo("STATE_CONFLICT");
                });

        verify(grantRepository, never()).save(any());
    }

    /**
     * 重复授权应失败。
     */
    @Test
    void addGrantThrowsWhenAlreadyGranted() {
        KnowledgeBase kb = new KnowledgeBase("kb-001", "共享KB", "SHARED", 10L);
        kb.setId(1L);
        kb.setStatus("ACTIVE");
        ClientApplication grantee = new ClientApplication("app-b", "应用B");
        grantee.setId(20L);

        when(knowledgeBaseRepository.findByKbUid("kb-001")).thenReturn(Optional.of(kb));
        when(clientApplicationRepository.findByAppUid("app-b")).thenReturn(Optional.of(grantee));
        when(grantRepository.existsByKnowledgeBaseIdAndClientAppId(1L, 20L)).thenReturn(true);

        assertThatThrownBy(() -> service.addGrant("kb-001", "app-b"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在")
                .satisfies(exception -> {
                    BusinessException business = (BusinessException) exception;
                    assertThat(business.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(business.code()).isEqualTo("STATE_CONFLICT");
                });

        verify(grantRepository, never()).save(any());
    }

    /**
     * 移除授权时调用删除方法。
     */
    @Test
    void removeGrantDeletesExistingGrant() {
        KnowledgeBase kb = new KnowledgeBase("kb-001", "共享KB", "SHARED", 10L);
        kb.setId(1L);
        kb.setStatus("ACTIVE");
        ClientApplication grantee = new ClientApplication("app-b", "应用B");
        grantee.setId(20L);

        when(knowledgeBaseRepository.findByKbUid("kb-001")).thenReturn(Optional.of(kb));
        when(clientApplicationRepository.findByAppUid("app-b")).thenReturn(Optional.of(grantee));

        service.removeGrant("kb-001", "app-b");

        verify(grantRepository).deleteByKnowledgeBaseIdAndClientAppId(1L, 20L);
    }

    /**
     * requireActive 在知识库不存在时抛出异常。
     */
    @Test
    void requireActiveThrowsWhenKnowledgeBaseNotFound() {
        when(knowledgeBaseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActive(999L))
                .isInstanceOf(KnowledgeBaseNotWritableException.class)
                .hasMessageContaining("不存在");
    }

    /**
     * requireActive 在知识库被禁用时抛出异常。
     */
    @Test
    void requireActiveThrowsWhenKnowledgeBaseDisabled() {
        KnowledgeBase kb = new KnowledgeBase("kb-001", "禁用KB", "PRIVATE", 10L);
        kb.setId(1L);
        kb.setStatus("DISABLED");

        when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(kb));

        assertThatThrownBy(() -> service.requireActive(1L))
                .isInstanceOf(KnowledgeBaseNotWritableException.class)
                .hasMessageContaining("禁用");
    }

    /**
     * requireActive 在知识库活跃时正常返回。
     */
    @Test
    void requireActivePassesForActiveKnowledgeBase() {
        KnowledgeBase kb = new KnowledgeBase("kb-001", "活跃KB", "PRIVATE", 10L);
        kb.setId(1L);
        kb.setStatus("ACTIVE");

        when(knowledgeBaseRepository.findById(1L)).thenReturn(Optional.of(kb));

        service.requireActive(1L);

        verify(knowledgeBaseRepository).findById(1L);
    }
}
