package com.agentto.rag.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<RagDocument, Long> {
    Page<RagDocument> findAllByOrderByUpdatedAtDesc(Pageable pageable);
    long countByStatus(String status);
}
