package com.agentto.rag.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.agentto.rag.retrieval.RerankScore;
import com.agentto.rag.retrieval.TeiRerankClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class TeiClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void embeddingClientNormalizesBatchAndChecksDimensions() throws Exception {
        int port = serve("/embed", "[[3.0,4.0,0.0],[0.0,0.0,2.0]]");
        TeiEmbeddingClient client = new TeiEmbeddingClient("http://127.0.0.1:" + port, 3,
                java.time.Duration.ofSeconds(2), new ObjectMapper());

        List<float[]> vectors = client.embed(List.of("甲", "乙"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.6f, 0.8f, 0.0f);
        assertThat(vectors.get(1)).containsExactly(0.0f, 0.0f, 1.0f);
    }

    @Test
    void embeddingClientRejectsWrongVectorDimension() throws Exception {
        int port = serve("/embed", "[[1.0,2.0]]");
        TeiEmbeddingClient client = new TeiEmbeddingClient("http://127.0.0.1:" + port, 3,
                java.time.Duration.ofSeconds(2), new ObjectMapper());

        assertThatThrownBy(() -> client.embed(List.of("甲")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("维度");
    }

    @Test
    void rerankClientSortsScoresAndKeepsOriginalIndex() throws Exception {
        int port = serve("/rerank", "[{\"index\":0,\"score\":0.12},{\"index\":1,\"score\":0.91}]");
        TeiRerankClient client = new TeiRerankClient("http://127.0.0.1:" + port,
                java.time.Duration.ofSeconds(2), new ObjectMapper());

        List<RerankScore> result = client.rerank("合同怎么审查", List.of("第一段", "第二段"));

        assertThat(result).extracting(RerankScore::originalIndex).containsExactly(1, 0);
        assertThat(result).extracting(RerankScore::rank).containsExactly(1, 2);
        assertThat(result.getFirst().score()).isEqualTo(0.91);
    }

    private int serve(String path, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server.getAddress().getPort();
    }
}
