package com.agentto.rag.query;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 公共查询编排 Trace 仓库。
 */
public interface QueryFlowTraceRepository extends JpaRepository<QueryFlowTraceEntity, Long> {

    Optional<QueryFlowTraceEntity> findByFlowTraceUid(String flowTraceUid);

    List<QueryFlowTraceEntity> findTop50ByOrderByCreatedAtDesc();
}
