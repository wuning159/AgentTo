package com.agentto.rag.knowledgebase;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 知识库共享授权仓储接口，提供按调用方查找授权记录的能力。
 */
public interface KnowledgeBaseGrantRepository extends JpaRepository<KnowledgeBaseGrant, Long> {

    /**
     * 查找某个调用方被授权的全部知识库授权记录。
     *
     * @param clientAppId 调用方应用 ID
     * @return 授权记录列表
     */
    List<KnowledgeBaseGrant> findByClientAppId(Long clientAppId);

    /**
     * 查找某个知识库的全部授权记录。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 授权记录列表
     */
    List<KnowledgeBaseGrant> findByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 检查授权是否已存在，避免重复授权。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param clientAppId    调用方应用 ID
     * @return 是否已存在授权
     */
    boolean existsByKnowledgeBaseIdAndClientAppId(Long knowledgeBaseId, Long clientAppId);

    /**
     * 按知识库 ID 和调用方应用 ID 删除授权记录。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param clientAppId    调用方应用 ID
     */
    void deleteByKnowledgeBaseIdAndClientAppId(Long knowledgeBaseId, Long clientAppId);
}
