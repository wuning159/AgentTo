package com.agentto.rag.knowledgebase;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentto.rag.client.ClientApplication;
import com.agentto.rag.client.ClientApplicationRepository;

/**
 * 知识库管理服务 JPA 实现。
 * 负责知识库创建、画像更新（profileVersion 原子递增）和共享授权管理。
 *
 * 业务规则：
 * - 创建知识库时验证所有者应用存在
 * - PRIVATE 知识库不能添加共享授权
 * - 共享授权不可重复
 * - 画像字段变化时原子递增 profileVersion
 */
@Service
public class JpaKnowledgeBaseAdminService implements KnowledgeBaseAdminService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseGrantRepository grantRepository;
    private final ClientApplicationRepository clientApplicationRepository;

    public JpaKnowledgeBaseAdminService(KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeBaseGrantRepository grantRepository,
            ClientApplicationRepository clientApplicationRepository) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.grantRepository = grantRepository;
        this.clientApplicationRepository = clientApplicationRepository;
    }

    /**
     * 创建知识库。
     * 验证所有者应用存在后，生成唯一标识并保存。
     */
    @Override
    @Transactional
    public KnowledgeBase createKnowledgeBase(String name, String description, String visibility, Long ownerAppId) {
        // 验证所有者应用存在
        ClientApplication owner = clientApplicationRepository.findById(ownerAppId)
                .orElseThrow(() -> new IllegalArgumentException("所有者应用不存在: " + ownerAppId));

        // 生成知识库唯一标识
        String kbUid = "kb-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        KnowledgeBase knowledgeBase = new KnowledgeBase(kbUid, name, visibility, owner.getId());
        knowledgeBase.setDescription(description);
        knowledgeBase.setStatus("ACTIVE");
        return knowledgeBaseRepository.save(knowledgeBase);
    }

    /**
     * 更新知识库画像（描述）。
     * 原子递增 profileVersion，供 Task 6 重建画像索引。
     */
    @Override
    @Transactional
    public KnowledgeBase updateProfile(String kbUid, String description) {
        KnowledgeBase kb = knowledgeBaseRepository.findByKbUid(kbUid)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + kbUid));

        kb.setDescription(description);
        kb.incrementProfileVersion();
        kb.setUpdatedAt(java.time.Instant.now());
        return knowledgeBaseRepository.save(kb);
    }

    /**
     * 为 SHARED 知识库添加共享授权。
     * PRIVATE 知识库不可添加授权，授权不可重复。
     */
    @Override
    @Transactional
    public void addGrant(String kbUid, String appUid) {
        KnowledgeBase kb = knowledgeBaseRepository.findByKbUid(kbUid)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + kbUid));

        ClientApplication grantee = clientApplicationRepository.findByAppUid(appUid)
                .orElseThrow(() -> new IllegalArgumentException("调用方应用不存在: " + appUid));

        // 私有知识库不能添加共享授权
        if (!kb.isShared()) {
            throw new IllegalStateException("私有知识库不能添加共享授权: " + kbUid);
        }

        // 授权不可重复
        if (grantRepository.existsByKnowledgeBaseIdAndClientAppId(kb.getId(), grantee.getId())) {
            throw new IllegalStateException("授权已存在: kbUid=" + kbUid + ", appUid=" + appUid);
        }

        KnowledgeBaseGrant grant = new KnowledgeBaseGrant(kb.getId(), grantee.getId());
        grant.setCreatedAt(java.time.Instant.now());
        grantRepository.save(grant);
    }

    /**
     * 移除共享授权。
     */
    @Override
    @Transactional
    public void removeGrant(String kbUid, String appUid) {
        KnowledgeBase kb = knowledgeBaseRepository.findByKbUid(kbUid)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在: " + kbUid));

        ClientApplication grantee = clientApplicationRepository.findByAppUid(appUid)
                .orElseThrow(() -> new IllegalArgumentException("调用方应用不存在: " + appUid));

        grantRepository.deleteByKnowledgeBaseIdAndClientAppId(kb.getId(), grantee.getId());
    }
}
