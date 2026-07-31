package com.agentto.rag.knowledgebase;

import java.util.Set;

/**
 * 知识库访问控制服务接口。
 * 负责计算一个调用方可以访问哪些知识库，以及校验读写权限。
 *
 * 访问规则：
 * - 所有者可管理和读取自己的私有/共享知识库；
 * - 非所有者只能读取显式授权且 visibility=SHARED 的知识库；
 * - DISABLED 知识库永远不可路由。
 */
public interface KnowledgeBaseAccessService {

    /**
     * 返回某个调用方可访问的全部知识库 ID 集合。
     * 包括自己的私有/共享知识库，以及被显式授权的他人共享知识库。
     *
     * @param clientAppId 调用方应用 ID
     * @return 可访问的知识库 ID 集合，不含 DISABLED 知识库
     */
    Set<Long> accessibleKnowledgeBaseIds(Long clientAppId);

    /**
     * 校验调用方对某知识库是否有读取权限，无权限时抛出异常。
     *
     * @param clientAppId      调用方应用 ID
     * @param knowledgeBaseId 知识库 ID
     * @throws KnowledgeBaseNotWritableException 当知识库不存在或不可访问时抛出
     */
    void requireReadable(Long clientAppId, Long knowledgeBaseId);

    /**
     * 校验调用方对某知识库是否有管理权限，无权限时抛出异常。
     *
     * @param clientAppId      调用方应用 ID
     * @param knowledgeBaseId 知识库 ID
     * @throws KnowledgeBaseNotWritableException 当知识库不存在或调用方非所有者时抛出
     */
    void requireManageable(Long clientAppId, Long knowledgeBaseId);
}
