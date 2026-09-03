package com.agentto.rag.asset;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ObjectCleanupTaskRepository extends JpaRepository<ObjectCleanupTask, Long> {

    @Query("select t from ObjectCleanupTask t where t.status = 'PENDING' "
            + "or (t.status = 'RETRYABLE_FAILURE' and (t.nextRetryAt is null or t.nextRetryAt <= :now)) "
            + "order by t.id")
    List<ObjectCleanupTask> findDue(@Param("now") Instant now);
}
