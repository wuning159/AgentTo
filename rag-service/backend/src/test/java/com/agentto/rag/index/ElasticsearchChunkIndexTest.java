package com.agentto.rag.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class ElasticsearchChunkIndexTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void createsExpectedMappingAndSupportsKeywordVectorAndBulkIndex() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rag-test", exchange -> handle(exchange, requests));
        server.start();

        ElasticsearchChunkIndex index = new ElasticsearchChunkIndex(
                "http://127.0.0.1:" + server.getAddress().getPort(), "", "", "rag-test", 3,
                Duration.ofSeconds(2), new ObjectMapper());

        index.ensureIndex();
        index.replaceVersionChunks(7L, List.of(new IndexedChunk(
                "chunk-1", 2L, 7L, 1L, 0, "制度", "预算必须经过审查", Map.of("page", "1"),
                new float[] { 0.1f, 0.2f, 0.3f })));
        List<IndexSearchHit> keyword = index.keywordSearch("预算", 5);
        List<IndexSearchHit> vector = index.vectorSearch(new float[] { 0.1f, 0.2f, 0.3f }, 5);

        assertThat(requests).anyMatch(body -> body.contains("\"dense_vector\"")
                && body.contains("\"dims\":3") && body.contains("ik_max_word"));
        assertThat(requests).anyMatch(body -> body.contains("\"chunk_uid\":\"chunk-1\"")
                && body.contains("\"embedding\":[0.1,0.2,0.3]"));
        assertThat(requests).anyMatch(body -> body.contains("\"multi_match\"") && body.contains("预算"));
        assertThat(requests).anyMatch(body -> body.contains("\"knn\"") && body.contains("query_vector"));
        assertThat(keyword.get(0).chunkId()).isEqualTo("chunk-keyword");
        assertThat(keyword.get(0).score()).isEqualTo(8.2);
        assertThat(vector.get(0).chunkId()).isEqualTo("chunk-vector");
        assertThat(vector.get(0).score()).isEqualTo(0.91);
    }

    private void handle(HttpExchange exchange, List<String> requests) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!body.isBlank()) requests.add(body);

        if ("HEAD".equals(method)) {
            respond(exchange, 404, "");
        } else if ("PUT".equals(method) && "/rag-test".equals(path)) {
            respond(exchange, 200, "{\"acknowledged\":true}");
        } else if (path.endsWith("/_delete_by_query")) {
            respond(exchange, 200, "{\"deleted\":0}");
        } else if (path.endsWith("/_bulk")) {
            respond(exchange, 200, "{\"errors\":false,\"items\":[]}");
        } else if (path.endsWith("/_search") && body.contains("\"knn\"")) {
            respond(exchange, 200, hit("chunk-vector", 0.91, "向量内容"));
        } else if (path.endsWith("/_search")) {
            respond(exchange, 200, hit("chunk-keyword", 8.2, "关键词内容"));
        } else {
            respond(exchange, 200, "{}");
        }
    }

    private String hit(String id, double score, String content) {
        return "{\"hits\":{\"hits\":[{\"_id\":\"" + id + "\",\"_score\":" + score
                + ",\"_source\":{\"chunk_uid\":\"" + id + "\",\"content\":\"" + content
                + "\",\"document_id\":2,\"version_id\":7,\"ordinal_no\":0,\"title\":\"制度\"}}]}}";
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
