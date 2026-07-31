package com.agentto.rag.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 知识库访问控制服务测试。
 * 验证 ACL 规则：所有者可访问自己的知识库，非所有者只能访问被授权的共享知识库，
 * DISABLED 知识库永远不可访问。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseAccessTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private KnowledgeBaseGrantRepository grantRepository;

    @InjectMocks
    private JpaKnowledgeBaseAccessService service;

    /**
     * 所有者可访问自己的私有和共享知识库，加上被授权的他人共享知识库，
     * 但不能访问未授权的他人知识库。
     */
    @Test
    void returnsOwnedPrivateAndGrantedSharedKnowledgeBasesOnly() {
        // 调用方 10 拥有知识库 101（PRIVATE）和 102（SHARED）
        KnowledgeBase kb101 = testKb(101L, "PRIVATE", "ACTIVE", 10L);
        KnowledgeBase kb102 = testKb(102L, "SHARED", "ACTIVE", 10L);
        // 知识库 103 由调用方 20 拥有，SHARED，已授权给调用方 10
        KnowledgeBase kb103 = testKb(103L, "SHARED", "ACTIVE", 20L);
        // 知识库 104 由调用方 30 拥有，SHARED，未授权给调用方 10
        KnowledgeBase kb104 = testKb(104L, "SHARED", "ACTIVE", 30L);

        when(knowledgeBaseRepository.findByOwnerAppId(10L)).thenReturn(List.of(kb101, kb102));
        // 只授权 103 给调用方 10，不授权 104
        when(grantRepository.findByClientAppId(10L)).thenReturn(List.of(testGrant(103L, 10L)));
        when(knowledgeBaseRepository.findById(103L)).thenReturn(Optional.of(kb103));

        Set<Long> accessible = service.accessibleKnowledgeBaseIds(10L);

        assertThat(accessible).containsExactlyInAnyOrder(101L, 102L, 103L);
        assertThat(accessible).doesNotContain(104L);
    }

    /**
     * DISABLED 知识库即使被授权也不应被返回。
     */
    @Test
    void disabledKnowledgeBaseIsNeverAccessible() {
        KnowledgeBase kb201 = testKb(201L, "SHARED", "DISABLED", 10L);
        KnowledgeBase kb202 = testKb(202L, "SHARED", "ACTIVE", 20L);

        when(knowledgeBaseRepository.findByOwnerAppId(20L)).thenReturn(List.of());
        when(grantRepository.findByClientAppId(20L)).thenReturn(List.of(
                testGrant(201L, 20L), testGrant(202L, 20L)));
        when(knowledgeBaseRepository.findById(201L)).thenReturn(Optional.of(kb201));
        when(knowledgeBaseRepository.findById(202L)).thenReturn(Optional.of(kb202));

        Set<Long> accessible = service.accessibleKnowledgeBaseIds(20L);

        assertThat(accessible).containsExactly(202L);
    }

    /**
     * 非所有者访问未授权的私有知识库时应抛出异常。
     */
    @Test
    void requireReadableThrowsForUnauthorizedKnowledgeBase() {
        KnowledgeBase kb301 = testKb(301L, "PRIVATE", "ACTIVE", 10L);

        when(knowledgeBaseRepository.findByOwnerAppId(20L)).thenReturn(List.of());
        when(grantRepository.findByClientAppId(20L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.requireReadable(20L, 301L))
                .isInstanceOf(KnowledgeBaseNotWritableException.class);
    }

    /**
     * 非所有者尝试管理他人知识库时应抛出异常。
     */
    @Test
    void requireManageableThrowsForNonOwner() {
        KnowledgeBase kb401 = testKb(401L, "SHARED", "ACTIVE", 10L);

        when(knowledgeBaseRepository.findById(401L)).thenReturn(Optional.of(kb401));

        assertThatThrownBy(() -> service.requireManageable(20L, 401L))
                .isInstanceOf(KnowledgeBaseNotWritableException.class);
    }

    /** 构造测试用知识库实体 */
    private KnowledgeBase testKb(Long id, String visibility, String status, Long ownerAppId) {
        KnowledgeBase kb = new KnowledgeBase("kb-" + id, "测试知识库" + id, visibility, ownerAppId);
        kb.setId(id);
        kb.setStatus(status);
        kb.setCreatedAt(Instant.now());
        kb.setUpdatedAt(Instant.now());
        return kb;
    }

    /** 构造测试用授权实体 */
    private KnowledgeBaseGrant testGrant(Long kbId, Long appId) {
        KnowledgeBaseGrant grant = new KnowledgeBaseGrant(kbId, appId);
        grant.setCreatedAt(Instant.now());
        return grant;
    }
}
