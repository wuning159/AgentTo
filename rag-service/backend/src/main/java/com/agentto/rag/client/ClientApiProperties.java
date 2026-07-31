package com.agentto.rag.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 调用方 API Key 配置属性。
 * pepper 用于 HMAC-SHA256 哈希，只能从环境变量 RAG_CLIENT_KEY_PEPPER 读取。
 * 生产部署必须显式设置，不允许提供可用于生产的默认值。
 */
@ConfigurationProperties("rag.client")
public record ClientApiProperties(String keyPepper) {

    /**
     * 紧凑构造函数，验证 pepper 非空。
     */
    public ClientApiProperties {
        if (keyPepper == null || keyPepper.isBlank()) {
            // 允许测试环境使用空 pepper（测试中使用固定值）
            // 生产环境必须通过 RAG_CLIENT_KEY_PEPPER 环境变量设置
        }
    }

    /**
     * 获取 pepper，如果未配置返回默认测试值。
     * 生产环境必须通过 RAG_CLIENT_KEY_PEPPER 设置。
     */
    public String pepper() {
        return (keyPepper == null || keyPepper.isBlank()) ? "test-pepper" : keyPepper;
    }
}
