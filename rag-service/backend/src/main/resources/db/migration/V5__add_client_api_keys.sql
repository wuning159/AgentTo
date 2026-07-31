-- V5: 创建调用方 API Key 表
-- 只存储 HMAC-SHA256 哈希值和前缀，不存储原始密钥

CREATE TABLE rag_client_api_key (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    client_app_id   BIGINT       NOT NULL,
    key_prefix      VARCHAR(16)  NOT NULL COMMENT 'Key 前缀，用于日志标识',
    key_hash        VARCHAR(128) NOT NULL COMMENT 'HMAC-SHA256(pepper, rawKey) 哈希值',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 或 REVOKED',
    expires_at      DATETIME     NULL     COMMENT '过期时间，NULL 表示不过期',
    last_used_at    DATETIME     NULL     COMMENT '最后使用时间',
    created_at      DATETIME     NOT NULL,
    CONSTRAINT uk_client_api_key_hash UNIQUE (key_hash),
    CONSTRAINT fk_client_api_key_app FOREIGN KEY (client_app_id) REFERENCES rag_client_application (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_client_api_key_app ON rag_client_api_key (client_app_id);
CREATE INDEX idx_client_api_key_prefix ON rag_client_api_key (key_prefix, client_app_id);
