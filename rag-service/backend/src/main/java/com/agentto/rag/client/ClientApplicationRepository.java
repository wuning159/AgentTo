package com.agentto.rag.client;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 调用方应用仓储接口，提供按 appUid 查找调用方的能力。
 */
public interface ClientApplicationRepository extends JpaRepository<ClientApplication, Long> {

    /**
     * 按应用唯一标识查找调用方。
     *
     * @param appUid 应用唯一标识
     * @return 调用方实体，不存在时返回空
     */
    Optional<ClientApplication> findByAppUid(String appUid);
}
