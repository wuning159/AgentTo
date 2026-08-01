package com.agentto.rag.index;

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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.agentto.rag.common.api.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(prefix = "rag.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchChunkIndex implements ChunkIndex {

    private final String baseUrl;
    private final String username;
    private final String password;
    private final String index;
    private final int dimensions;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ElasticsearchChunkIndex(ElasticsearchProperties properties, ObjectMapper objectMapper) {
        this(properties.url(), properties.username(), properties.password(), properties.index(), properties.dimensions(),
                Duration.ofSeconds(30), objectMapper);
    }

    public ElasticsearchChunkIndex(String baseUrl, String username, String password, String index, int dimensions,
            Duration timeout, ObjectMapper objectMapper) {
        this.baseUrl = trimSlash(require(baseUrl, "Elasticsearch 地址"));
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.index = require(index, "Elasticsearch 索引名");
        this.dimensions = dimensions;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public void ensureIndex() {
        HttpResponse<String> head = send("HEAD", "/" + index, null, "application/json");
        if (head.statusCode() == 200) {
            return;
        }
        if (head.statusCode() != 404) {
            throw httpFailure("检查索引", head);
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("chunk_uid", Map.of("type", "keyword"));
        properties.put("document_id", Map.of("type", "long"));
        properties.put("version_id", Map.of("type", "long"));
        properties.put("knowledge_base_id", Map.of("type", "long"));
        properties.put("ordinal_no", Map.of("type", "integer"));
        properties.put("title", Map.of("type", "text", "analyzer", "ik_max_word", "search_analyzer", "ik_smart"));
        properties.put("content", Map.of("type", "text", "analyzer", "ik_max_word", "search_analyzer", "ik_smart"));
        properties.put("metadata", Map.of("type", "object", "enabled", false));
        properties.put("active", Map.of("type", "boolean"));
        properties.put("embedding", Map.of("type", "dense_vector", "dims", dimensions, "index", true,
                "similarity", "cosine"));
        Map<String, Object> mapping = Map.of(
                "settings", Map.of("number_of_shards", 1, "number_of_replicas", 0),
                "mappings", Map.of("dynamic", "strict", "properties", properties));
        HttpResponse<String> created = sendJson("PUT", "/" + index, mapping);
        requireSuccess("创建索引", created);
    }

    @Override
    public void replaceVersionChunks(Long versionId, List<IndexedChunk> chunks) {
        HttpResponse<String> deleted = sendJson("POST", "/" + index + "/_delete_by_query?refresh=true",
                Map.of("query", Map.of("term", Map.of("version_id", versionId))));
        requireSuccess("清理旧版本切片", deleted);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        StringBuilder bulk = new StringBuilder();
        for (IndexedChunk chunk : chunks) {
            bulk.append(json(Map.of("index", Map.of("_index", index, "_id", chunk.chunkId())))).append('\n');
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("chunk_uid", chunk.chunkId());
            source.put("document_id", chunk.documentId());
            source.put("version_id", chunk.versionId());
            source.put("knowledge_base_id", chunk.knowledgeBaseId());
            source.put("ordinal_no", chunk.ordinal());
            source.put("title", chunk.title() == null ? "" : chunk.title());
            source.put("content", chunk.content());
            source.put("metadata", chunk.metadata() == null ? Map.of() : chunk.metadata());
            source.put("active", true);
            source.put("embedding", chunk.embedding());
            bulk.append(json(source)).append('\n');
        }
        HttpResponse<String> response = send("POST", "/" + index + "/_bulk?refresh=true", bulk.toString(),
                "application/x-ndjson");
        requireSuccess("批量写入切片", response);
        try {
            if (objectMapper.readTree(response.body()).path("errors").asBoolean(false)) {
                throw new IllegalStateException("Elasticsearch 批量写入包含失败项");
            }
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Elasticsearch 批量响应格式不正确", exception);
        }
    }

    @Override
    @Deprecated
    public List<IndexSearchHit> keywordSearch(String query, int limit) {
        Map<String, Object> multiMatch = Map.of("query", query, "fields", List.of("content^2", "title"));
        Map<String, Object> bool = Map.of("must", Map.of("multi_match", multiMatch),
                "filter", List.of(Map.of("term", Map.of("active", true))));
        return search(Map.of("size", limit, "query", Map.of("bool", bool)));
    }

    @Override
    @Deprecated
    public List<IndexSearchHit> vectorSearch(float[] queryVector, int limit) {
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("field", "embedding");
        knn.put("query_vector", queryVector);
        knn.put("k", limit);
        knn.put("num_candidates", Math.max(limit * 3, 50));
        knn.put("filter", Map.of("term", Map.of("active", true)));
        return search(Map.of("size", limit, "knn", knn));
    }

    /**
     * 关键词检索（带知识库范围过滤）。
     * 使用 terms 过滤限定 knowledge_base_id。
     */
    @Override
    public List<IndexSearchHit> keywordSearch(String query, SearchScope scope, int limit) {
        Map<String, Object> multiMatch = Map.of("query", query, "fields", List.of("content^2", "title"));
        List<Map<String, Object>> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("active", true)));
        filters.add(Map.of("terms", Map.of("knowledge_base_id", List.copyOf(scope.knowledgeBaseIds()))));
        Map<String, Object> bool = Map.of("must", Map.of("multi_match", multiMatch),
                "filter", filters);
        return search(Map.of("size", limit, "query", Map.of("bool", bool)));
    }

    /**
     * 向量检索（带知识库范围过滤）。
     * 使用 terms 过滤限定 knowledge_base_id。
     */
    @Override
    public List<IndexSearchHit> vectorSearch(float[] queryVector, SearchScope scope, int limit) {
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("field", "embedding");
        knn.put("query_vector", queryVector);
        knn.put("k", limit);
        knn.put("num_candidates", Math.max(limit * 3, 50));
        List<Object> kbFilter = List.copyOf(scope.knowledgeBaseIds());
        knn.put("filter", Map.of("bool", Map.of("filter", List.of(
                Map.of("term", Map.of("active", true)),
                Map.of("terms", Map.of("knowledge_base_id", kbFilter))))));
        return search(Map.of("size", limit, "knn", knn));
    }

    @Override
    public void clearAll() {
        ensureIndex();
        HttpResponse<String> response = sendJson("POST", "/" + index + "/_delete_by_query?refresh=true",
                Map.of("query", Map.of("match_all", Map.of())));
        requireSuccess("清空 RAG 索引", response);
    }

    @Override
    public boolean healthy() {
        try {
            return send("GET", "/_cluster/health", null, "application/json").statusCode() < 400;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public String indexVersion() {
        return index;
    }

    private List<IndexSearchHit> search(Map<String, Object> request) {
        HttpResponse<String> response = sendJson("POST", "/" + index + "/_search", request);
        if (response.statusCode() == 404 && response.body() != null
                && response.body().contains("index_not_found_exception")) {
            throw new BusinessException("INDEX_NOT_READY", "检索索引尚未创建，请先导入文档", HttpStatus.SERVICE_UNAVAILABLE);
        }
        requireSuccess("执行检索", response);
        try {
            List<IndexSearchHit> result = new ArrayList<>();
            for (JsonNode hit : objectMapper.readTree(response.body()).path("hits").path("hits")) {
                JsonNode source = hit.path("_source");
                Map<String, String> metadata = new LinkedHashMap<>();
                source.path("metadata").fields().forEachRemaining(entry -> metadata.put(entry.getKey(), entry.getValue().asText()));
                result.add(new IndexSearchHit(
                        source.path("chunk_uid").asText(hit.path("_id").asText()),
                        source.path("content").asText(),
                        source.path("title").asText(),
                        source.path("document_id").isNumber() ? source.path("document_id").asLong() : null,
                        source.path("version_id").isNumber() ? source.path("version_id").asLong() : null,
                        source.path("knowledge_base_id").isNumber() ? source.path("knowledge_base_id").asLong() : null,
                        source.path("ordinal_no").asInt(),
                        hit.path("_score").asDouble(), metadata));
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Elasticsearch 检索响应格式不正确", exception);
        }
    }

    private HttpResponse<String> sendJson(String method, String path, Object body) {
        return send(method, path, json(body), "application/json");
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout);
            if (!username.isBlank()) {
                String basic = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                builder.header("Authorization", "Basic " + basic);
            }
            if (body != null) builder.header("Content-Type", contentType);
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Elasticsearch 请求被中断", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Elasticsearch 请求失败", exception);
        }
    }

    private void requireSuccess(String operation, HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw httpFailure(operation, response);
    }

    private IllegalStateException httpFailure(String operation, HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body();
        return new IllegalStateException(operation + "失败，HTTP " + response.statusCode() + "：" + body.substring(0, Math.min(500, body.length())));
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("JSON 序列化失败", exception); }
    }

    private String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException("缺少" + name);
        return value;
    }

    private String trimSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
}
