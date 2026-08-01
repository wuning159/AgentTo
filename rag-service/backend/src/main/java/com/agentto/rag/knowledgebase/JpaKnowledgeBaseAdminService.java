package com.agentto.rag.knowledgebase;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentto.rag.client.ClientApplication;
import com.agentto.rag.client.ClientApplicationRepository;
import com.agentto.rag.common.api.BusinessException;

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
     * 校验知识库存在且处于 ACTIVE 状态。
     * 用于文档入库前确认目标知识库可写。
     */
    @Override
    public void requireActive(Long knowledgeBaseId) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new KnowledgeBaseNotWritableException("知识库不存在: " + knowledgeBaseId));
        if (!kb.isActive()) {
            throw new KnowledgeBaseNotWritableException("知识库已被禁用: " + knowledgeBaseId);
        }
    }

    /**
     * 创建知识库。
     * 验证所有者应用存在后，生成唯一标识并保存。
     */
    @Override
    @Transactional
    public KnowledgeBase createKnowledgeBase(String name, String description, String visibility, Long ownerAppId) {
        if (ownerAppId == null) {
            throw new BusinessException("VALIDATION_ERROR", "所有者应用 ID 不能为空", HttpStatus.BAD_REQUEST);
        }
        // 验证所有者应用存在
        ClientApplication owner = clientApplicationRepository.findById(ownerAppId)
                .orElseThrow(() -> new BusinessException("OWNER_NOT_FOUND", "所有者应用不存在: " + ownerAppId,
                        HttpStatus.BAD_REQUEST));

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
                .orElseThrow(() -> new BusinessException("KNOWLEDGE_BASE_NOT_FOUND", "知识库不存在: " + kbUid,
                        HttpStatus.NOT_FOUND));

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
                .orElseThrow(() -> new BusinessException("KNOWLEDGE_BASE_NOT_FOUND", "知识库不存在: " + kbUid,
                        HttpStatus.NOT_FOUND));

        ClientApplication grantee = clientApplicationRepository.findByAppUid(appUid)
                .orElseThrow(() -> new BusinessException("APP_NOT_FOUND", "调用方应用不存在: " + appUid,
                        HttpStatus.NOT_FOUND));

        // 私有知识库不能添加共享授权
        if (!kb.isShared()) {
            throw new BusinessException("STATE_CONFLICT", "私有知识库不能添加共享授权: " + kbUid,
                    HttpStatus.CONFLICT);
        }

        // 授权不可重复
        if (grantRepository.existsByKnowledgeBaseIdAndClientAppId(kb.getId(), grantee.getId())) {
            throw new BusinessException("STATE_CONFLICT", "授权已存在: kbUid=" + kbUid + ", appUid=" + appUid,
                    HttpStatus.CONFLICT);
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
                .orElseThrow(() -> new BusinessException("KNOWLEDGE_BASE_NOT_FOUND", "知识库不存在: " + kbUid,
                        HttpStatus.NOT_FOUND));

        ClientApplication grantee = clientApplicationRepository.findByAppUid(appUid)
                .orElseThrow(() -> new BusinessException("APP_NOT_FOUND", "调用方应用不存在: " + appUid,
                        HttpStatus.NOT_FOUND));

        grantRepository.deleteByKnowledgeBaseIdAndClientAppId(kb.getId(), grantee.getId());
    }
}
