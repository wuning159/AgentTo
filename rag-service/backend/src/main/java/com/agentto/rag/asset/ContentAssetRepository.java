package com.agentto.rag.asset;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ContentAssetRepository extends JpaRepository<ContentAsset, Long> {

    Optional<ContentAsset> findBySha256(String sha256);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ContentAsset a where a.id = :id")
    Optional<ContentAsset> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ContentAsset a where a.sha256 = :sha256")
    Optional<ContentAsset> findBySha256ForUpdate(@Param("sha256") String sha256);

    List<ContentAsset> findByStorageStateOrderByIdAsc(ContentAssetState storageState);

    @Query("select a from ContentAsset a where a.storageState = com.agentto.rag.asset.ContentAssetState.PENDING "
            + "and (a.leaseExpiresAt is null or a.leaseExpiresAt <= :now) order by a.id")
    List<ContentAsset> findExpiredPending(@Param("now") Instant now);

    @Query("select a from ContentAsset a where a.storageState = com.agentto.rag.asset.ContentAssetState.DELETE_PENDING "
            + "or (a.storageState = com.agentto.rag.asset.ContentAssetState.DELETE_FAILED "
            + "and (a.nextDeleteRetryAt is null or a.nextDeleteRetryAt <= :now)) order by a.id")
    List<ContentAsset> findDueDeleteRetries(@Param("now") Instant now);

    long countByStorageState(ContentAssetState storageState);

    boolean existsByBucketAndObjectKey(String bucket, String objectKey);
}
