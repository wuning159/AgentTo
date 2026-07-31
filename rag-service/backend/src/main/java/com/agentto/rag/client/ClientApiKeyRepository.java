package com.agentto.rag.client;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 调用方 API Key 仓储接口。
 */
public interface ClientApiKeyRepository extends JpaRepository<ClientApiKey, Long> {

    /**
     * 按哈希值查找 ACTIVE 状态的 Key。
     *
     * @param keyHash HMAC-SHA256 哈希值
     * @return API Key 实体，不存在时返回空
     */
    Optional<ClientApiKey> findByKeyHashAndStatus(String keyHash, String status);

    /**
     * 按前缀和调用方应用 ID 查找 Key（用于撤销）。
     *
     * @param keyPrefix  Key 前缀
     * @param clientAppId 调用方应用 ID
     * @return API Key 实体，不存在时返回空
     */
    Optional<ClientApiKey> findByKeyPrefixAndClientAppId(String keyPrefix, Long clientAppId);

    /**
     * 查找调用方的全部 Key。
     *
     * @param clientAppId 调用方应用 ID
     * @return Key 列表
     */
    java.util.List<ClientApiKey> findByClientAppId(Long clientAppId);
}
