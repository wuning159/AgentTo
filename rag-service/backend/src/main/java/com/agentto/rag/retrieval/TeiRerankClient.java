package com.agentto.rag.retrieval;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.agentto.rag.embedding.TeiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(prefix = "rag.tei", name = "rerank-enabled", havingValue = "true", matchIfMissing = true)
public class TeiRerankClient implements RerankService {

    private final String baseUrl;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public TeiRerankClient(TeiProperties properties, ObjectMapper objectMapper) {
        this(properties.rerankUrl(), Duration.ofSeconds(properties.readTimeoutSeconds()), objectMapper,
                Duration.ofSeconds(properties.connectTimeoutSeconds()));
    }

    public TeiRerankClient(String baseUrl, Duration timeout, ObjectMapper objectMapper) {
        this(baseUrl, timeout, objectMapper, timeout);
    }

    private TeiRerankClient(String baseUrl, Duration timeout, ObjectMapper objectMapper, Duration connectTimeout) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("缺少 TEI Rerank 地址");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.requestTimeout = timeout;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    @Override
    public List<RerankScore> rerank(String query, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            String json = objectMapper.writeValueAsString(Map.of("query", query, "texts", texts));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/rerank"))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("TEI Rerank 请求失败，HTTP " + response.statusCode());
            }
            TeiResponse[] items = objectMapper.readValue(response.body(), TeiResponse[].class);
            List<RerankScore> result = new ArrayList<>(items.length);
            for (TeiResponse item : items) {
                if (item.index() < 0 || item.index() >= texts.size()) {
                    throw new IllegalStateException("TEI Rerank 返回了无效索引");
                }
                result.add(new RerankScore(item.index(), item.score(), 0));
            }
            result.sort(Comparator.comparingDouble(RerankScore::score).reversed()
                    .thenComparingInt(RerankScore::originalIndex));
            List<RerankScore> ranked = new ArrayList<>(result.size());
            for (int i = 0; i < result.size(); i++) {
                RerankScore item = result.get(i);
                ranked.add(new RerankScore(item.originalIndex(), item.score(), i + 1));
            }
            return List.copyOf(ranked);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TEI Rerank 请求被中断", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException state) {
                throw state;
            }
            throw new IllegalStateException("TEI Rerank 调用失败", exception);
        }
    }

    @Override
    public boolean healthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                    .timeout(requestTimeout).GET().build();
            return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() < 400;
        } catch (Exception exception) {
            return false;
        }
    }

    public record TeiResponse(int index, double score) {
    }
}
