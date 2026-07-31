package com.agentto.rag.knowledgebase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 知识库实体。
 * 每个知识库属于一个调用方（owner），文档和切片都绑定到特定知识库。
 * 通过共享授权可以让其他调用方读取 SHARED 知识库。
 */
@Entity
@Table(name = "rag_knowledge_base")
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kb_uid", nullable = false, unique = true, length = 64)
    private String kbUid;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 32)
    private String visibility = "PRIVATE";

    @Column(name = "owner_app_id")
    private Long ownerAppId;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "profile_version", nullable = false)
    private int profileVersion = 1;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;

    protected KnowledgeBase() {
    }

    public KnowledgeBase(String kbUid, String name, String visibility, Long ownerAppId) {
        this.kbUid = kbUid;
        this.name = name;
        this.visibility = visibility;
        this.ownerAppId = ownerAppId;
        this.createdAt = java.time.Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    /** 设置 ID，仅供测试和数据迁移使用 */
    public void setId(Long id) {
        this.id = id;
    }

    public String getKbUid() {
        return kbUid;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isShared() {
        return "SHARED".equals(visibility);
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public Long getOwnerAppId() {
        return ownerAppId;
    }

    public int getProfileVersion() {
        return profileVersion;
    }

    public void incrementProfileVersion() {
        this.profileVersion++;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public java.time.Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.Instant createdAt) {
        this.createdAt = createdAt;
    }

    public java.time.Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
