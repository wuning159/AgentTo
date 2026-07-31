package com.agentto.rag.knowledgebase;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 知识库仓储接口，提供按所有者、可见性和唯一标识查询知识库的能力。
 */
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    /**
     * 按所有者查找其名下的全部知识库。
     *
     * @param ownerAppId 调用方应用 ID
     * @return 该调用方拥有的知识库列表
     */
    List<KnowledgeBase> findByOwnerAppId(Long ownerAppId);

    /**
     * 查找所有共享且活跃的知识库，用于第二阶段授权匹配。
     *
     * @param visibility 可见性（SHARED）
     * @param status     状态（ACTIVE）
     * @return 符合条件的知识库列表
     */
    List<KnowledgeBase> findByVisibilityAndStatus(String visibility, String status);

    /**
     * 按唯一标识查找知识库。
     *
     * @param kbUid 知识库唯一标识
     * @return 知识库实体，不存在时返回空
     */
    Optional<KnowledgeBase> findByKbUid(String kbUid);
}
