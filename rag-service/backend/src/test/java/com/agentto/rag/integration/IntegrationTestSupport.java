package com.agentto.rag.integration;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import com.agentto.rag.client.ClientApiKeyRepository;
import com.agentto.rag.client.ClientApplicationRepository;
import com.agentto.rag.knowledgebase.KnowledgeBaseGrantRepository;
import com.agentto.rag.knowledgebase.KnowledgeBaseRepository;

/**
 * 集成测试基类：共享 MySQL 与 Elasticsearch 容器。
 *
 * <p>容器在 Spring 上下文刷新前通过 {@link #registerProperties} 启动
 * （{@code start()} 幂等），并把连接信息动态注入属性源。
 * 镜像均来自本地（deploy/docker-compose.yml 同款），无需联网拉取。
 */
@ActiveProfiles("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestSupport {

    /** 真实 MySQL 8.4，与生产部署版本一致 */
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("agentto_rag_it")
            .withUsername("rag_it")
            .withPassword("rag_it_password")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")
            .withStartupTimeout(Duration.ofMinutes(3));

    /** 真实 Elasticsearch 9.4.2（含 IK 分词插件），与生产部署镜像一致 */
    static final ElasticsearchContainer ES = new ElasticsearchContainer(
            DockerImageName.parse("agentto-elasticsearch:9.4.2-ik")
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withStartupTimeout(Duration.ofMinutes(3))
            .waitingFor(Wait.forHttp("/_cluster/health")
                    .forPort(9200)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        MYSQL.start();
        ES.start();
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("rag.elasticsearch.url", () -> "http://" + ES.getHttpHostAddress());
        registry.add("rag.elasticsearch.username", () -> "");
        registry.add("rag.elasticsearch.password", () -> "");
    }

    @Autowired private ClientApplicationRepository clientAppRepository;
    @Autowired private ClientApiKeyRepository clientApiKeyRepository;
    @Autowired private KnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired private KnowledgeBaseGrantRepository grantRepository;

    /** 每个用例前清理业务数据，保证真实 MySQL 下从干净状态开始 */
    @BeforeEach
    void cleanBusinessData() {
        grantRepository.deleteAll();
        clientApiKeyRepository.deleteAll();
        knowledgeBaseRepository.deleteAll();
        clientAppRepository.deleteAll();
    }
}
