package com.agentto.rag.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.agentto.rag.client.ClientApplication;
import com.agentto.rag.client.ClientApplicationAdminService;
import com.agentto.rag.client.CreatedClientApiKey;
import com.agentto.rag.index.ChunkIndex;
import com.agentto.rag.index.IndexedChunk;
import com.agentto.rag.knowledgebase.JpaKnowledgeBaseAdminService;
import com.agentto.rag.knowledgebase.KnowledgeBase;
import com.agentto.rag.routing.ElasticsearchKnowledgeBaseProfileIndex;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 公共查询 API 全链路集成测试（真实 HTTP + 真实 MySQL/Elasticsearch）。
 *
 * <p>以调用方视角验证 {@code POST /api/v1/rag/query}：
 * 认证失败 401、参数非法 400、业务拒答与成功回答 200，
 * 任何场景都不得返回 500（尤其检索 Trace 持久化不得因外键或空操作人失败）。
 */
@Import(IntegrationTestStubs.class)
class RagPublicApiIntegrationTest extends IntegrationTestSupport {

    @LocalServerPort
    private int port;

    @Autowired private ClientApplicationAdminService clientService;
    @Autowired private JpaKnowledgeBaseAdminService knowledgeBaseService;
    @Autowired private ChunkIndex chunkIndex;
    @Autowired private ElasticsearchKnowledgeBaseProfileIndex profileIndex;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ClientApplication appA;
    private ClientApplication appB;
    private CreatedClientApiKey keyA;
    private Long versionId = 200L;

    @DynamicPropertySource
    static void uniqueIndex(DynamicPropertyRegistry registry) {
        // 使用独立索引名，避免与其它测试类的数据串扰
        registry.add("rag.elasticsearch.index", () -> "agentto-rag-public-api-it-chunks-v1");
    }

    @BeforeEach
    void setUp() {
        appA = clientService.createClient("public-api-app-a", "公共调用方 A");
        appB = clientService.createClient("public-api-app-b", "公共调用方 B");
        keyA = clientService.createApiKey("public-api-app-a");

        // 写入真实分片：调用方 A 的私有知识库（两个相似分片，保证内容验证分数充分）
        KnowledgeBase kbA = knowledgeBaseService.createKnowledgeBase("预算知识库", "预算审查与预算编制知识库",
                "PRIVATE", appA.getId());
        chunkIndex.ensureIndex();
        chunkIndex.replaceVersionChunks(versionId, List.of(
                new IndexedChunk(
                        "public-chunk-1", 1L, versionId, kbA.getId(), 0, "预算制度",
                        "预算必须经过财务审查，超预算支出需要重新审批，预算调整需报财务部门备案。",
                        Map.of("page", "1"), IntegrationTestStubs.embed("预算必须经过财务审查")),
                new IndexedChunk(
                        "public-chunk-2", 1L, versionId, kbA.getId(), 1, "预算编制",
                        "预算编制需依据年度经营目标，收入预算与支出预算应保持平衡，编制结果报财务审查。",
                        Map.of("page", "2"), IntegrationTestStubs.embed("预算编制需依据年度经营目标"))));

        // 写入知识库画像：描述与查询“预算审查”共享字符，KNN 可召回
        profileIndex.ensureIndex();
        writeProfile(kbA.getId(), "预算知识库", "预算审查与预算编制知识库");
    }

    /** 未认证请求返回 401，而非 500 */
    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        HttpResponse<String> response = post("/api/v1/rag/query", """
                {"query":"预算审查"}
                """, null);

        assertThat(response.statusCode()).isEqualTo(401);
        JsonNode body = read(response.body());
        assertThat(body.path("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    /** 无效 Token 返回 401，而非 500 */
    @Test
    void invalidTokenReturnsUnauthorized() throws Exception {
        HttpResponse<String> response = post("/api/v1/rag/query", """
                {"query":"预算审查"}
                """, "rag_live_ffffffffffffffffffffffffffffffffffffffff");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(read(response.body()).path("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    /** 空查询返回 400 参数错误，而非 500 */
    @Test
    void blankQueryReturnsBadRequest() throws Exception {
        HttpResponse<String> response = post("/api/v1/rag/query", """
                {"query":"  "}
                """, keyA.rawKey());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(read(response.body()).path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    /** 未授权调用方 B 查询 A 的私有知识库：业务拒答 200，不泄露知识库存在性 */
    @Test
    void unauthorizedCallerGetsRefusalWithoutError() throws Exception {
        CreatedClientApiKey keyB = clientService.createApiKey("public-api-app-b");
        HttpResponse<String> response = post("/api/v1/rag/query", """
                {"query":"预算审查"}
                """, keyB.rawKey());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode data = read(response.body()).path("data");
        assertThat(data.path("decision").asText()).isEqualTo("NO_RELEVANT_KNOWLEDGE_BASE");
        assertThat(data.path("answer").isNull()).isTrue();
        assertThat(data.path("traceUid").asText()).isNotBlank();
    }

    /** 所有者 A 查询自己的私有知识库：路由 → 检索 → 证据门 → 生成 → 引用校验，全链路 200 */
    @Test
    void ownerQuerySucceedsEndToEnd() throws Exception {
        HttpResponse<String> response = post("/api/v1/rag/query", """
                {"query":"预算审查","finalLimit":5}
                """, keyA.rawKey());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode data = read(response.body()).path("data");
        assertThat(data.path("decision").asText()).isEqualTo("ANSWERED");
        assertThat(data.path("answer").asText()).contains("预算");
        assertThat(data.path("citations")).isNotEmpty();
        assertThat(data.path("citations").get(0).path("chunkId").asText())
                .isIn("public-chunk-1", "public-chunk-2");
        assertThat(data.path("traceUid").asText()).isNotBlank();
    }

    /** 与知识库无关的查询：路由拒答或弱命中都返回合法业务决策，绝不 500 */
    @Test
    void unrelatedQueryReturnsRefusalWithoutError() throws Exception {
        HttpResponse<String> response = post("/api/v1/rag/query", """
                {"query":"食堂菜单推荐"}
                """, keyA.rawKey());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode data = read(response.body()).path("data");
        assertThat(data.path("decision").asText())
                .isIn("ANSWERED", "NO_RELEVANT_KNOWLEDGE_BASE", "INSUFFICIENT_EVIDENCE");
    }

    /** 非法 finalLimit 返回 400，而非 500 */
    @Test
    void invalidFinalLimitReturnsBadRequest() throws Exception {
        HttpResponse<String> response = post("/api/v1/rag/query", """
                {"query":"预算审查","finalLimit":0}
                """, keyA.rawKey());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(read(response.body()).path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    private HttpResponse<String> post(String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void writeProfile(Long knowledgeBaseId, String name, String description) {
        try {
            String body = """
                    {"knowledge_base_id":%d,"name":"%s","description":"%s","embedding":%s}
                    """.formatted(knowledgeBaseId, name, description,
                    vectorJson(IntegrationTestStubs.embed(description)));
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://" + ES.getHttpHostAddress() + "/" + profileIndex.indexVersion()
                            + "/_doc/" + knowledgeBaseId + "?refresh=true"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isLessThan(400);
        } catch (Exception exception) {
            throw new IllegalStateException("写入知识库画像失败", exception);
        }
    }

    private String vectorJson(float[] vector) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(vector[index]);
        }
        return json.append(']').toString();
    }

    private JsonNode read(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
