package com.agentto.rag.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.agentto.rag.client.ClientApplication;
import com.agentto.rag.client.ClientApplicationAdminService;
import com.agentto.rag.client.CreatedClientApiKey;
import com.agentto.rag.index.ChunkIndex;
import com.agentto.rag.index.IndexedChunk;
import com.agentto.rag.index.SearchScope;
import com.agentto.rag.knowledgebase.JpaKnowledgeBaseAdminService;
import com.agentto.rag.knowledgebase.KnowledgeBase;
import com.agentto.rag.retrieval.HybridRetrievalService;
import com.agentto.rag.retrieval.RetrievalCandidate;
import com.agentto.rag.retrieval.RetrievalRequest;
import com.agentto.rag.retrieval.RetrievalResponse;
import com.agentto.rag.routing.ElasticsearchKnowledgeBaseProfileIndex;
import com.agentto.rag.routing.KnowledgeBaseProfileCandidate;
import com.agentto.rag.routing.KnowledgeBaseRoute;
import com.agentto.rag.routing.KnowledgeBaseRouter;
import com.agentto.rag.routing.RoutingDecision;

/**
 * 知识库 ACL 作用域集成测试（真实 Elasticsearch）。
 *
 * <p>验证计划要求的核心隔离场景：
 * 私有知识库只对所有者可见；路由与检索均按调用方可访问知识库集合过滤，
 * 检索结果必须映射到真实写入的分片。
 */
@Import(IntegrationTestStubs.class)
class ElasticsearchScopeIntegrationTest extends IntegrationTestSupport {

    @Autowired private ClientApplicationAdminService clientService;
    @Autowired private JpaKnowledgeBaseAdminService knowledgeBaseService;
    @Autowired private KnowledgeBaseRouter router;
    @Autowired private HybridRetrievalService retrievalService;
    @Autowired private ChunkIndex chunkIndex;
    @Autowired private ElasticsearchKnowledgeBaseProfileIndex profileIndex;

    private ClientApplication appA;
    private ClientApplication appB;
    private CreatedClientApiKey keyA;
    private KnowledgeBase kbA;
    private Long versionId = 100L;

    @DynamicPropertySource
    static void uniqueIndex(DynamicPropertyRegistry registry) {
        // 使用独立索引名，避免与其它测试类的数据串扰
        registry.add("rag.elasticsearch.index", () -> "agentto-rag-scope-it-chunks-v1");
    }

    @BeforeEach
    void setUp() {
        appA = clientService.createClient("scope-app-a", "调用方 A");
        appB = clientService.createClient("scope-app-b", "调用方 B");
        keyA = clientService.createApiKey("scope-app-a");
        kbA = knowledgeBaseService.createKnowledgeBase("预算知识库", "预算审查与预算编制知识库",
                "PRIVATE", appA.getId());

        // 写入真实分片：私有知识库 A 下的预算切片（两个相似分片，保证内容验证分数充分）
        chunkIndex.ensureIndex();
        chunkIndex.replaceVersionChunks(versionId, List.of(
                new IndexedChunk(
                        "scope-chunk-1", 1L, versionId, kbA.getId(), 0, "预算制度",
                        "预算必须经过财务审查，超预算支出需要重新审批，预算调整需报财务部门备案。",
                        Map.of("page", "1"), IntegrationTestStubs.embed("预算必须经过财务审查")),
                new IndexedChunk(
                        "scope-chunk-2", 1L, versionId, kbA.getId(), 1, "预算编制",
                        "预算编制需依据年度经营目标，收入预算与支出预算应保持平衡，编制结果报财务审查。",
                        Map.of("page", "2"), IntegrationTestStubs.embed("预算编制需依据年度经营目标"))));

        // 写入知识库画像：描述与查询“预算审查”共享字符，KNN 可召回
        profileIndex.ensureIndex();
        writeProfile(kbA.getId(), "预算知识库", "预算审查与预算编制知识库");
    }

    /** 未授权调用方 B 查询私有知识库：路由直接拒答，不泄露知识库存在性 */
    @Test
    void unauthorizedCallerCannotRouteToPrivateKnowledgeBase() {
        KnowledgeBaseRoute route = router.route(appB.getId(), "预算审查");

        assertThat(route.decision()).isEqualTo(RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE);
        assertThat(route.selectedKnowledgeBaseIds()).isEmpty();
    }

    /** 所有者 A 路由到自己的私有知识库：画像召回 → 内容验证全链路命中 */
    @Test
    void ownerRoutesToOwnPrivateKnowledgeBase() {
        KnowledgeBaseRoute route = router.route(appA.getId(), "预算审查");

        assertThat(route.decision()).isEqualTo(RoutingDecision.ROUTED);
        assertThat(route.selectedKnowledgeBaseIds()).containsExactly(kbA.getId());
        assertThat(route.profileShortlist()).contains(kbA.getId());
        assertThat(route.verificationScores().get(kbA.getId())).isGreaterThan(0.0);
    }

    /** 画像召回返回真实知识库画像（knowledge_base_id 与名称可解析） */
    @Test
    void profileSearchReturnsRealKnowledgeBaseProfiles() {
        List<KnowledgeBaseProfileCandidate> candidates = profileIndex.search(
                IntegrationTestStubs.embed("预算审查"), Set.of(kbA.getId()), 10);

        assertThat(candidates).isNotEmpty();
        assertThat(candidates.get(0).knowledgeBaseId()).isEqualTo(kbA.getId());
        assertThat(candidates.get(0).name()).isEqualTo("预算知识库");
    }

    /** 检索结果映射到真实写入的分片：chunkId 与内容来自 ES 索引 */
    @Test
    void retrievalMapsRealChunksWithinSelectedScope() {
        RetrievalResponse response = retrievalService.search(new RetrievalRequest(
                "预算审查", 12, 12, 10, 10, 8)
                .withScope(new SearchScope(Set.of(kbA.getId())))
                .withRequesterId(appA.getId()));

        assertThat(response.candidates()).isNotEmpty();
        RetrievalCandidate top = response.candidates().get(0);
        assertThat(top.chunkId()).isIn("scope-chunk-1", "scope-chunk-2");
        assertThat(top.content()).contains("预算");
        assertThat(response.traceUid()).isNotBlank();
    }

    /** 画像索引缺失时路由不抛异常：惰性创建后正常返回（无 500 路径） */
    @Test
    void routingDoesNotFailWhenProfileIndexWasMissing() {
        // 清理画像索引后，路由应惰性重建并正常返回（画像为空 → 拒答，而非异常）
        clearProfileIndex();
        org.assertj.core.api.Assertions.assertThatCode(
                () -> router.route(appA.getId(), "预算审查"))
                .doesNotThrowAnyException();
    }

    private void writeProfile(Long knowledgeBaseId, String name, String description) {
        try {
            String body = """
                    {"knowledge_base_id":%d,"name":"%s","description":"%s","embedding":%s}
                    """.formatted(knowledgeBaseId, name, description,
                    vectorJson(IntegrationTestStubs.embed(description)));
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://" + ES.getHttpHostAddress() + "/" + profileIndex.indexVersion()
                            + "/_doc/" + knowledgeBaseId + "?refresh=true"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isLessThan(400);
        } catch (Exception exception) {
            throw new IllegalStateException("写入知识库画像失败", exception);
        }
    }

    private void clearProfileIndex() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://" + ES.getHttpHostAddress() + "/" + profileIndex.indexVersion() + "?refresh=true"))
                    .DELETE()
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception exception) {
            throw new IllegalStateException("清理画像索引失败", exception);
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
}
