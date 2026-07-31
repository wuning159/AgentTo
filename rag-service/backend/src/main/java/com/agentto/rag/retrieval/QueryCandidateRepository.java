package com.agentto.rag.retrieval;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryCandidateRepository extends JpaRepository<QueryCandidateEntity, Long> {
    List<QueryCandidateEntity> findByTraceIdOrderByFinalRankAscRrfRankAsc(Long traceId);
}
