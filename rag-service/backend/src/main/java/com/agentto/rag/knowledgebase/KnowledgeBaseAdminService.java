package com.agentto.rag.knowledgebase;

/**
 * 知识库管理服务接口。
 * 提供知识库创建、画像更新和共享授权管理能力。
 */
public interface KnowledgeBaseAdminService {

    /**
     * 创建知识库。
     *
     * @param name        知识库名称
     * @param description 知识库描述（可为空）
     * @param visibility  可见性（PRIVATE 或 SHARED）
     * @param ownerAppId  所有者调用方应用 ID
     * @return 已创建的知识库实体
     * @throws IllegalArgumentException 当所有者应用不存在时抛出
     */
    KnowledgeBase createKnowledgeBase(String name, String description, String visibility, Long ownerAppId);

    /**
     * 更新知识库画像（描述），原子递增 profileVersion。
     *
     * @param kbUid      知识库唯一标识
     * @param description 新的描述内容
     * @return 更新后的知识库实体
     * @throws IllegalArgumentException 当知识库不存在时抛出
     */
    KnowledgeBase updateProfile(String kbUid, String description);

    /**
     * 为 SHARED 知识库添加共享授权。
     *
     * @param kbUid  知识库唯一标识
     * @param appUid 被授权的调用方应用唯一标识
     * @throws IllegalArgumentException 当知识库或调用方不存在时抛出
     * @throws IllegalStateException    当知识库为 PRIVATE 或授权已存在时抛出
     */
    void addGrant(String kbUid, String appUid);

    /**
     * 移除共享授权。
     *
     * @param kbUid  知识库唯一标识
     * @param appUid 被移除授权的调用方应用唯一标识
     */
    void removeGrant(String kbUid, String appUid);
}
