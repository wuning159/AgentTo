package com.agentto.rag.routing;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 基于 Elasticsearch 的知识库画像索引实现。
 * 当 Elasticsearch 启用时自动装配，使用独立的画像索引存储知识库的向量表示。
 *
 * 画像文档结构：
 *   - knowledge_base_id: long
 *   - name: text
 *   - description: text
 *   - embedding: dense_vector
 */
@Service
@ConditionalOnProperty(prefix = "rag.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchKnowledgeBaseProfileIndex implements KnowledgeBaseProfileIndex {

    private static final String PROFILE_INDEX_SUFFIX = "_kb_profiles";

    private final String baseUrl;
    private final String username;
    private final String password;
    private final String profileIndex;
    private final int dimensions;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /**
     * 注入构造器：从配置读取 Elasticsearch 连接参数。
     *
     * @param properties  Elasticsearch 配置
     * @param objectMapper JSON 序列化器
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ElasticsearchKnowledgeBaseProfileIndex(
            com.agentto.rag.index.ElasticsearchProperties properties, ObjectMapper objectMapper) {
        this(properties.url(), properties.username(), properties.password(),
                properties.index() + PROFILE_INDEX_SUFFIX, properties.dimensions(),
                Duration.ofSeconds(30), objectMapper);
    }

    public ElasticsearchKnowledgeBaseProfileIndex(String baseUrl, String username, String password,
            String profileIndex, int dimensions, Duration timeout, ObjectMapper objectMapper) {
        this.baseUrl = trimSlash(require(baseUrl, "Elasticsearch 地址"));
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.profileIndex = require(profileIndex, "画像索引名");
        this.dimensions = dimensions;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public List<KnowledgeBaseProfileCandidate> search(float[] queryVector,
            Set<Long> accessibleKnowledgeBaseIds, int limit) {
        if (accessibleKnowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        // 惰性确保画像索引存在，避免全新 Elasticsearch 环境下查询直接失败
        ensureIndex();
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("field", "embedding");
        knn.put("query_vector", queryVector);
        knn.put("k", limit);
        knn.put("num_candidates", Math.max(limit * 3, 50));
        knn.put("filter", Map.of("bool", Map.of("filter", List.of(
                Map.of("terms", Map.of("knowledge_base_id",
                        List.copyOf(accessibleKnowledgeBaseIds)))))));
        Map<String, Object> request = Map.of("size", limit, "knn", knn);
        HttpResponse<String> response = sendJson("POST", "/" + profileIndex + "/_search", request);
        requireSuccess("画像检索", response);
        try {
            List<KnowledgeBaseProfileCandidate> result = new ArrayList<>();
            for (JsonNode hit : objectMapper.readTree(response.body()).path("hits").path("hits")) {
                JsonNode source = hit.path("_source");
                result.add(new KnowledgeBaseProfileCandidate(
                        source.path("knowledge_base_id").isNumber()
                                ? source.path("knowledge_base_id").asLong() : null,
                        source.path("name").asText(""),
                        hit.path("_score").asDouble()));
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalStateException("画像检索响应格式不正确", exception);
        }
    }

    /**
     * 确保画像索引存在（幂等）。
     *
     * <p>索引缺失时创建，mapping 与分块索引保持一致：
     * knowledge_base_id 为 long，name/description 使用 IK 分词，
     * embedding 为 dense_vector（cosine 相似度）。
     * 创建失败（非 404 的 HEAD 错误或 PUT 失败）时抛出异常，由调用方决定降级策略。
     */
    public void ensureIndex() {
        HttpResponse<String> head = send("HEAD", "/" + profileIndex, null, "application/json");
        if (head.statusCode() == 200) {
            return;
        }
        if (head.statusCode() != 404) {
            throw httpFailure("检查画像索引", head);
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("knowledge_base_id", Map.of("type", "long"));
        properties.put("name", Map.of("type", "text", "analyzer", "ik_max_word",
                "search_analyzer", "ik_smart"));
        properties.put("description", Map.of("type", "text", "analyzer", "ik_max_word",
                "search_analyzer", "ik_smart"));
        properties.put("embedding", Map.of("type", "dense_vector", "dims", dimensions,
                "index", true, "similarity", "cosine"));
        Map<String, Object> mapping = Map.of(
                "settings", Map.of("number_of_shards", 1, "number_of_replicas", 0),
                "mappings", Map.of("dynamic", "strict", "properties", properties));
        HttpResponse<String> created = sendJson("PUT", "/" + profileIndex, mapping);
        requireSuccess("创建画像索引", created);
    }

    @Override
    public String indexVersion() {
        return profileIndex;
    }

    @Override
    public boolean healthy() {
        try {
            return send("GET", "/_cluster/health", null, "application/json").statusCode() < 400;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private HttpResponse<String> sendJson(String method, String path, Object body) {
        return send(method, path, json(body), "application/json");
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout);
            if (!username.isBlank()) {
                String basic = Base64.getEncoder().encodeToString(
                        (username + ":" + password).getBytes(StandardCharsets.UTF_8));
                builder.header("Authorization", "Basic " + basic);
            }
            if (body != null) builder.header("Content-Type", contentType);
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception exception) {
            throw new IllegalStateException("Elasticsearch 请求失败: " + method + " " + path, exception);
        }
    }

    private void requireSuccess(String action, HttpResponse<String> response) {
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(action + "失败: HTTP " + response.statusCode()
                    + " " + response.body());
        }
    }

    private IllegalStateException httpFailure(String operation, HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body();
        return new IllegalStateException(operation + "失败，HTTP " + response.statusCode() + "："
                + body.substring(0, Math.min(500, body.length())));
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("JSON 序列化失败", exception); }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
