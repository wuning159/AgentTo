package com.agentto.rag.knowledgebase;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 知识库共享授权实体。
 * 一条记录表示一个调用方被授权读取某个 SHARED 知识库。
 */
@Entity
@Table(name = "rag_knowledge_base_grant")
public class KnowledgeBaseGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "client_app_id", nullable = false)
    private Long clientAppId;

    @Column(nullable = false, length = 32)
    private String permission = "READ";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KnowledgeBaseGrant() {
    }

    public KnowledgeBaseGrant(Long knowledgeBaseId, Long clientAppId) {
        this.knowledgeBaseId = knowledgeBaseId;
        this.clientAppId = clientAppId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public Long getClientAppId() {
        return clientAppId;
    }

    public String getPermission() {
        return permission;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
