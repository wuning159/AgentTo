package com.agentto.rag.knowledgebase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * 基于 JPA 的知识库访问控制服务实现。
 *
 * 访问规则：
 * - 所有者可管理和读取自己的全部知识库（无论 PRIVATE 或 SHARED）；
 * - 非所有者只能读取被显式授权且 visibility=SHARED 的知识库；
 * - status=DISABLED 的知识库永远不被返回。
 */
@Service
public class JpaKnowledgeBaseAccessService implements KnowledgeBaseAccessService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseGrantRepository grantRepository;

    public JpaKnowledgeBaseAccessService(KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeBaseGrantRepository grantRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.grantRepository = grantRepository;
    }

    @Override
    public Set<Long> accessibleKnowledgeBaseIds(Long clientAppId) {
        Set<Long> ids = new HashSet<>();

        // 所有者自己的知识库（活跃状态）
        List<KnowledgeBase> owned = knowledgeBaseRepository.findByOwnerAppId(clientAppId);
        for (KnowledgeBase kb : owned) {
            if (kb.isActive()) {
                ids.add(kb.getId());
            }
        }

        // 被显式授权的共享知识库
        List<KnowledgeBaseGrant> grants = grantRepository.findByClientAppId(clientAppId);
        for (KnowledgeBaseGrant grant : grants) {
            knowledgeBaseRepository.findById(grant.getKnowledgeBaseId()).ifPresent(kb -> {
                // 只有 SHARED 且 ACTIVE 的知识库才可被授权方访问
                if (kb.isActive() && kb.isShared()) {
                    ids.add(kb.getId());
                }
            });
        }

        return ids;
    }

    @Override
    public void requireReadable(Long clientAppId, Long knowledgeBaseId) {
        Set<Long> accessible = accessibleKnowledgeBaseIds(clientAppId);
        if (!accessible.contains(knowledgeBaseId)) {
            throw new KnowledgeBaseNotWritableException(
                    "调用方 " + clientAppId + " 无权读取知识库 " + knowledgeBaseId);
        }
    }

    @Override
    public void requireManageable(Long clientAppId, Long knowledgeBaseId) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new KnowledgeBaseNotWritableException(
                        "知识库 " + knowledgeBaseId + " 不存在"));
        if (!clientAppId.equals(kb.getOwnerAppId())) {
            throw new KnowledgeBaseNotWritableException(
                    "调用方 " + clientAppId + " 不是知识库 " + knowledgeBaseId + " 的所有者");
        }
    }
}
