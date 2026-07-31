package com.agentto.rag.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Elasticsearch 知识库画像索引测试。
 * 使用嵌入式 HTTP 服务器模拟 Elasticsearch，验证 KNN 检索请求和响应解析。
 */
class ElasticsearchKnowledgeBaseProfileIndexTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void searchSendsKnnQueryWithAccessibleIdFilterAndParsesResults() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/kb-profile-test", exchange -> handle(exchange, requests));
        server.start();

        ElasticsearchKnowledgeBaseProfileIndex index = new ElasticsearchKnowledgeBaseProfileIndex(
                "http://127.0.0.1:" + server.getAddress().getPort(), "", "", "kb-profile-test", 3,
                Duration.ofSeconds(2), new ObjectMapper());

        float[] queryVector = { 0.1f, 0.2f, 0.3f };
        List<KnowledgeBaseProfileCandidate> results = index.search(
                queryVector, Set.of(101L, 102L), 10);

        // 验证请求包含 KNN 查询和 terms 过滤
        assertThat(requests).anyMatch(body -> body.contains("\"knn\"")
                && body.contains("\"query_vector\""));
        assertThat(requests).anyMatch(body -> body.contains("\"terms\"")
                && body.contains("knowledge_base_id")
                && body.contains("101")
                && body.contains("102"));

        // 验证响应解析
        assertThat(results).hasSize(1);
        assertThat(results.get(0).knowledgeBaseId()).isEqualTo(101L);
        assertThat(results.get(0).name()).isEqualTo("财务知识库");
        assertThat(results.get(0).score()).isEqualTo(0.92);
    }

    @Test
    void searchReturnsEmptyWhenAccessibleIdsIsEmpty() {
        ElasticsearchKnowledgeBaseProfileIndex index = new ElasticsearchKnowledgeBaseProfileIndex(
                "http://127.0.0.1:9999", "", "", "kb-profile-test", 3,
                Duration.ofSeconds(1), new ObjectMapper());

        List<KnowledgeBaseProfileCandidate> results = index.search(
                new float[] { 0.1f, 0.2f, 0.3f }, Set.of(), 10);

        assertThat(results).isEmpty();
    }

    @Test
    void indexVersionReturnsProfileIndexName() {
        ElasticsearchKnowledgeBaseProfileIndex index = new ElasticsearchKnowledgeBaseProfileIndex(
                "http://127.0.0.1:9999", "", "", "my-profile-index", 3,
                Duration.ofSeconds(1), new ObjectMapper());

        assertThat(index.indexVersion()).isEqualTo("my-profile-index");
    }

    private void handle(HttpExchange exchange, List<String> requests) throws IOException {
        String method = exchange.getRequestMethod();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!body.isBlank()) requests.add(body);

        if (pathEndsWith(exchange, "/_search")) {
            respond(exchange, 200, "{\"hits\":{\"hits\":[{\"_id\":\"1\",\"_score\":0.92,"
                    + "\"_source\":{\"knowledge_base_id\":101,\"name\":\"财务知识库\"}}]}}");
        } else {
            respond(exchange, 200, "{}");
        }
    }

    private boolean pathEndsWith(HttpExchange exchange, String suffix) {
        return exchange.getRequestURI().getPath().endsWith(suffix);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
