package com.agentto.rag.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminSessionRepository extends JpaRepository<AdminSession, Long> {

    Optional<AdminSession> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);
}
