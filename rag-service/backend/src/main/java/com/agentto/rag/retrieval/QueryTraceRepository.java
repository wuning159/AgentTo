package com.agentto.rag.retrieval;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryTraceRepository extends JpaRepository<QueryTraceEntity, Long> {
    Optional<QueryTraceEntity> findByTraceUid(String traceUid);
    List<QueryTraceEntity> findTop50ByOrderByCreatedAtDesc();
}
