package com.agentto.rag.client;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * 调用方 API Key 认证器。
 * 使用 HMAC-SHA256(pepper, rawKey) 计算哈希，通过哈希查找活跃的 Key。
 * 认证成功后返回 CallerPrincipal，不持久化原始密钥。
 */
@Service
public class ClientApiKeyAuthenticator {

    private final ClientApiKeyRepository keyRepository;
    private final ClientApplicationRepository appRepository;
    private final ClientApiProperties properties;

    public ClientApiKeyAuthenticator(ClientApiKeyRepository keyRepository,
            ClientApplicationRepository appRepository,
            ClientApiProperties properties) {
        this.keyRepository = keyRepository;
        this.appRepository = appRepository;
        this.properties = properties;
    }

    /**
     * 认证调用方 API Key。
     *
     * @param rawKey 原始密钥（Bearer Token 值）
     * @return 调用方主体身份
     * @throws InvalidClientCredentialException 当 Key 不存在、已撤销或已过期时抛出
     */
    public CallerPrincipal authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new InvalidClientCredentialException("API Key 不能为空");
        }

        // 使用 HMAC-SHA256 计算哈希，不持久化原始密钥
        String hash = hmacSha256(properties.pepper(), rawKey);

        // 通过哈希查找活跃的 Key
        ClientApiKey key = keyRepository.findByKeyHashAndStatus(hash, "ACTIVE")
                .orElseThrow(() -> new InvalidClientCredentialException("API Key 无效"));

        // 检查可用性（未过期、未撤销）
        Instant now = Instant.now();
        key.requireUsable(now);

        // 查找调用方应用信息
        ClientApplication app = appRepository.findById(key.getClientAppId())
                .orElseThrow(() -> new InvalidClientCredentialException("调用方应用不存在"));

        // 检查应用是否活跃
        if (!app.isActive()) {
            throw new InvalidClientCredentialException("调用方应用已被禁用");
        }

        // 更新最后使用时间
        key.markUsed(now);
        keyRepository.save(key);

        return new CallerPrincipal(app.getId(), app.getAppUid(), app.getName());
    }

    /**
     * 使用 HMAC-SHA256 计算密钥哈希。
     *
     * @param pepper  服务器密钥
     * @param rawKey  原始密钥
     * @return 十六进制哈希字符串
     */
    static String hmacSha256(String pepper, String rawKey) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                    pepper.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hashBytes = mac.doFinal(rawKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hashBytes);
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", exception);
        }
    }
}
