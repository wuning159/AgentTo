package com.agentto.rag.client;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 外部调用方应用实体。
 * 每个调用方代表一个通过 API Key 访问 RAG 服务的外部系统。
 */
@Entity
@Table(name = "rag_client_application")
public class ClientApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_uid", nullable = false, unique = true, length = 64)
    private String appUid;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClientApplication() {
    }

    public ClientApplication(String appUid, String name) {
        this.appUid = appUid;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    /** 设置 ID，仅供测试和数据迁移使用 */
    public void setId(Long id) {
        this.id = id;
    }

    public String getAppUid() {
        return appUid;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
