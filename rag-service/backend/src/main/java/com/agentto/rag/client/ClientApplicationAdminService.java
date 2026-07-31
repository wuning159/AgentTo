package com.agentto.rag.client;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 调用方应用管理服务。
 * 提供创建调用方应用、生成 API Key 和撤销 Key 的能力。
 *
 * 安全规则：
 * - 原始 Key 使用 SecureRandom 生成至少 32 字节随机量
 * - 只持久化 HMAC-SHA256 哈希值，不存储原始密钥
 * - 日志只允许记录 keyPrefix
 */
@Service
public class ClientApplicationAdminService {

    /** 原始密钥长度（32 字节 = 256 位） */
    private static final int RAW_KEY_BYTES = 32;

    private final ClientApplicationRepository appRepository;
    private final ClientApiKeyRepository keyRepository;
    private final ClientApiProperties properties;

    public ClientApplicationAdminService(ClientApplicationRepository appRepository,
            ClientApiKeyRepository keyRepository, ClientApiProperties properties) {
        this.appRepository = appRepository;
        this.keyRepository = keyRepository;
        this.properties = properties;
    }

    /**
     * 创建调用方应用。
     *
     * @param appUid 应用唯一标识（为空时自动生成）
     * @param name   应用名称
     * @return 已创建的调用方应用实体
     */
    @Transactional
    public ClientApplication createClient(String appUid, String name) {
        String uid = (appUid == null || appUid.isBlank()) ? "app-" + UUID.randomUUID().toString().substring(0, 12) : appUid;
        ClientApplication app = new ClientApplication(uid, name);
        app.setCreatedAt(java.time.Instant.now());
        app.setUpdatedAt(java.time.Instant.now());
        return appRepository.save(app);
    }

    /**
     * 为调用方生成新的 API Key。
     * 原始密钥只在返回值中包含一次，不持久化。
     *
     * @param appUid 调用方应用唯一标识
     * @return 包含原始密钥的创建结果
     * @throws IllegalArgumentException 当调用方不存在时抛出
     */
    @Transactional
    public CreatedClientApiKey createApiKey(String appUid) {
        ClientApplication app = appRepository.findByAppUid(appUid)
                .orElseThrow(() -> new IllegalArgumentException("调用方应用不存在: " + appUid));

        // 使用 SecureRandom 生成 32 字节随机密钥
        byte[] rawBytes = new byte[RAW_KEY_BYTES];
        new SecureRandom().nextBytes(rawBytes);
        String rawKey = "rag_live_" + HexFormat.of().formatHex(rawBytes);

        // 生成前缀（用于日志标识）
        String keyPrefix = rawKey.substring(0, 16);

        // 计算 HMAC-SHA256 哈希
        String keyHash = ClientApiKeyAuthenticator.hmacSha256(properties.pepper(), rawKey);

        // 持久化哈希值，不存储原始密钥
        ClientApiKey key = new ClientApiKey(app.getId(), keyPrefix, keyHash);
        keyRepository.save(key);

        // 原始密钥只在响应中返回一次
        return new CreatedClientApiKey(app.getAppUid(), keyPrefix, rawKey);
    }

    /**
     * 撤销调用方的 API Key。
     *
     * @param appUid    调用方应用唯一标识
     * @param keyPrefix 要撤销的 Key 前缀
     * @throws IllegalArgumentException 当调用方或 Key 不存在时抛出
     */
    @Transactional
    public void revokeApiKey(String appUid, String keyPrefix) {
        ClientApplication app = appRepository.findByAppUid(appUid)
                .orElseThrow(() -> new IllegalArgumentException("调用方应用不存在: " + appUid));

        ClientApiKey key = keyRepository.findByKeyPrefixAndClientAppId(keyPrefix, app.getId())
                .orElseThrow(() -> new IllegalArgumentException("API Key 不存在: " + keyPrefix));

        key.revoke();
        keyRepository.save(key);
    }
}
