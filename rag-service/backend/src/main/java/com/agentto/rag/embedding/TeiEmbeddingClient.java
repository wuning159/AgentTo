package com.agentto.rag.embedding;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(prefix = "rag.tei", name = "embedding-enabled", havingValue = "true", matchIfMissing = true)
public class TeiEmbeddingClient implements EmbeddingService {

    private final String baseUrl;
    private final int dimensions;
    private final Duration requestTimeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public TeiEmbeddingClient(TeiProperties properties, ElasticsearchDimensions dimensions, ObjectMapper objectMapper) {
        this(properties.embeddingUrl(), dimensions.value(), Duration.ofSeconds(properties.readTimeoutSeconds()),
                objectMapper, Duration.ofSeconds(properties.connectTimeoutSeconds()));
    }

    public TeiEmbeddingClient(String baseUrl, int dimensions, Duration timeout, ObjectMapper objectMapper) {
        this(baseUrl, dimensions, timeout, objectMapper, timeout);
    }

    private TeiEmbeddingClient(String baseUrl, int dimensions, Duration requestTimeout, ObjectMapper objectMapper,
            Duration connectTimeout) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.dimensions = dimensions;
        this.requestTimeout = requestTimeout;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        double[][] response = post("/embed", Map.of("inputs", texts), double[][].class);
        if (response.length != texts.size()) {
            throw new IllegalStateException("TEI Embedding 返回数量与输入不一致");
        }
        List<float[]> result = new ArrayList<>(response.length);
        for (double[] values : response) {
            if (values.length != dimensions) {
                throw new IllegalStateException("TEI Embedding 向量维度不正确，期望 " + dimensions + "，实际 " + values.length);
            }
            result.add(normalize(values));
        }
        return List.copyOf(result);
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

    private float[] normalize(double[] values) {
        double squareSum = 0;
        for (double value : values) {
            squareSum += value * value;
        }
        double norm = Math.sqrt(squareSum);
        float[] result = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = norm < 1e-12 ? (float) values[i] : (float) (values[i] / norm);
        }
        return result;
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("TEI 请求失败，HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TEI 请求被中断", exception);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("TEI 响应格式不正确", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException state) {
                throw state;
            }
            throw new IllegalStateException("TEI Embedding 调用失败", exception);
        }
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少 TEI Embedding 地址");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record ElasticsearchDimensions(int value) {
    }
}
