package com.agentto.rag.client;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 调用方 API Key 实体。
 * 只存储 HMAC-SHA256 哈希值和前缀，不存储原始密钥。
 * 创建时返回一次完整密钥后即丢弃。
 */
@Entity
@Table(name = "rag_client_api_key")
public class ClientApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_app_id", nullable = false)
    private Long clientAppId;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true, length = 128)
    private String keyHash;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ClientApiKey() {
    }

    public ClientApiKey(Long clientAppId, String keyPrefix, String keyHash) {
        this.clientAppId = clientAppId;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }

    /**
     * 检查 Key 是否可用：状态为 ACTIVE 且未过期。
     */
    public void requireUsable(Instant now) {
        if (!"ACTIVE".equals(status)) {
            throw new InvalidClientCredentialException("API Key 已被撤销");
        }
        if (expiresAt != null && now.isAfter(expiresAt)) {
            throw new InvalidClientCredentialException("API Key 已过期");
        }
    }

    /**
     * 标记最后使用时间。
     */
    public void markUsed(Instant now) {
        this.lastUsedAt = now;
    }

    /**
     * 撤销 Key。
     */
    public void revoke() {
        this.status = "REVOKED";
    }

    public Long getId() { return id; }
    public Long getClientAppId() { return clientAppId; }
    public String getKeyPrefix() { return keyPrefix; }
    public String getKeyHash() { return keyHash; }
    public String getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getCreatedAt() { return createdAt; }

    /** 设置 ID，仅供测试使用 */
    public void setId(Long id) { this.id = id; }

    /** 设置状态，仅供测试使用 */
    public void setStatus(String status) { this.status = status; }

    public boolean isActive() { return "ACTIVE".equals(status); }
}
