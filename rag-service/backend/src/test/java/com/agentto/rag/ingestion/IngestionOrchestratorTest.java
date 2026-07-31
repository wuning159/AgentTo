package com.agentto.rag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.agentto.rag.auth.AdminSessionRepository;
import com.agentto.rag.auth.AdminUser;
import com.agentto.rag.auth.AdminUserRepository;
import com.agentto.rag.document.DocumentRepository;
import com.agentto.rag.document.DocumentService;
import com.agentto.rag.document.DocumentVersionRepository;
import com.agentto.rag.document.UploadResult;
import com.agentto.rag.embedding.EmbeddingService;
import com.agentto.rag.index.ChunkIndex;
import com.agentto.rag.index.IndexSearchHit;
import com.agentto.rag.index.IndexedChunk;
import com.agentto.rag.observability.TechnicalStageDetail;
import com.agentto.rag.storage.ObjectStorageService;
import com.agentto.rag.storage.StoredObject;
import com.fasterxml.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@SpringBootTest
@Import(IngestionOrchestratorTest.FakeAdapterConfiguration.class)
class IngestionOrchestratorTest {

    @Autowired private DocumentService documentService;
    @Autowired private IngestionOrchestrator orchestrator;
    @Autowired private IngestionJobRepository jobRepository;
    @Autowired private IngestionStageRepository stageRepository;
    @Autowired private RagChunkRepository chunkRepository;
    @Autowired private DocumentVersionRepository versionRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private AdminUserRepository userRepository;
    @Autowired private AdminSessionRepository sessionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private FakeChunkIndex fakeIndex;
    @Autowired private BlockingEmbedding blockingEmbedding;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private IngestionQueryService ingestionQueryService;

    private Long adminId;

    @BeforeEach
    void setUp() {
        chunkRepository.deleteAll();
        stageRepository.deleteAll();
        jobRepository.deleteAll();
        versionRepository.deleteAll();
        documentRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        fakeIndex.clear();
        blockingEmbedding.reset();
        adminId = userRepository.save(AdminUser.create("admin", "管理员", passwordEncoder.encode("password"))).getId();
    }

    @Test
    void processesUploadedDocxThroughEveryVisibleStage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "预算制度.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx());
        UploadResult upload = documentService.upload(file, "公司制度", adminId);

        orchestrator.process(upload.jobId());

        IngestionJob job = jobRepository.findById(upload.jobId()).orElseThrow();
        assertThat(job.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(stageRepository.findByJobIdOrderById(upload.jobId()))
                .extracting(IngestionStage::getStageCode)
                .containsExactly("STORE", "PARSE", "CLEAN", "CHUNK", "EMBED", "INDEX", "COMPLETE");
        assertThat(chunkRepository.findByVersionIdOrderByOrdinalNo(upload.versionId())).isNotEmpty()
                .allMatch(chunk -> chunk.getContent().contains("预算"));
        assertThat(fakeIndex.chunks).isNotEmpty();
        assertThat(versionRepository.findById(upload.versionId()).orElseThrow().getProcessingStatus()).isEqualTo("READY");
        assertThat(documentRepository.findById(upload.documentId()).orElseThrow().getStatus()).isEqualTo("READY");

        IngestionStage chunkStage = stageRepository.findByJobIdOrderById(upload.jobId()).stream()
                .filter(stage -> stage.getStageCode().equals("CHUNK"))
                .findFirst().orElseThrow();
        TechnicalStageDetail chunkDetail = objectMapper.readValue(
                chunkStage.getTechnicalDetailJson(), TechnicalStageDetail.class);
        assertThat(chunkDetail.summary()).contains("标题").contains("段落");
        assertThat(chunkDetail.parameters())
                .containsEntry("targetChars", 500)
                .containsEntry("maxChars", 800)
                .containsEntry("overlapChars", 80);
        assertThat(chunkDetail.inputCount()).isPositive();
        assertThat(chunkDetail.outputCount()).isPositive();

        IngestionStageView chunkView = ingestionQueryService.job(upload.jobId()).stages().stream()
                .filter(stage -> stage.stage().equals("CHUNK"))
                .findFirst().orElseThrow();
        assertThat(chunkView.technicalDetail()).isEqualTo(chunkDetail);
        assertThat(ingestionQueryService.latestForVersion(upload.versionId()).id())
                .isEqualTo(upload.jobId());
    }

    @Test
    void exposesCurrentStageWhileTheJobIsStillRunning() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "预算制度.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx());
        UploadResult upload = documentService.upload(file, "公司制度", adminId);
        blockingEmbedding.pause();

        CompletableFuture<Void> processing = CompletableFuture.runAsync(() -> orchestrator.process(upload.jobId()));
        try {
            assertThat(blockingEmbedding.awaitEntered()).isTrue();

            IngestionJob visible = jobRepository.findById(upload.jobId()).orElseThrow();
            assertThat(visible.getStatus()).isEqualTo("RUNNING");
            assertThat(visible.getCurrentStage()).isEqualTo("EMBED");
            assertThat(stageRepository.findByJobIdOrderById(upload.jobId()))
                    .extracting(IngestionStage::getStageCode)
                    .containsExactly("STORE", "PARSE", "CLEAN", "CHUNK");
        } finally {
            blockingEmbedding.release();
            processing.get(10, TimeUnit.SECONDS);
        }
    }

    private byte[] docx() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("预算编制完成后，必须由财务部门进行审查。预算调整需要保留审批记录。");
            document.write(output);
            return output.toByteArray();
        }
    }

    @TestConfiguration
    static class FakeAdapterConfiguration {
        @Bean @Primary FakeStorage fakeStorage() { return new FakeStorage(); }
        @Bean @Primary BlockingEmbedding fakeEmbedding() { return new BlockingEmbedding(); }
        @Bean @Primary FakeChunkIndex fakeChunkIndex() { return new FakeChunkIndex(); }
    }

    static final class BlockingEmbedding implements EmbeddingService {
        private volatile CountDownLatch entered = new CountDownLatch(0);
        private volatile CountDownLatch released = new CountDownLatch(0);

        void pause() {
            entered = new CountDownLatch(1);
            released = new CountDownLatch(1);
        }

        void reset() {
            entered = new CountDownLatch(0);
            released = new CountDownLatch(0);
        }

        boolean awaitEntered() throws InterruptedException { return entered.await(5, TimeUnit.SECONDS); }
        void release() { released.countDown(); }

        @Override
        public List<float[]> embed(List<String> texts) {
            entered.countDown();
            try {
                if (!released.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("测试等待超时");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("测试线程被中断", exception);
            }
            return texts.stream().map(text -> new float[] { 1, 0, 0 }).toList();
        }

        @Override public boolean healthy() { return true; }
    }

    static final class FakeStorage implements ObjectStorageService {
        private final Map<String, byte[]> values = new ConcurrentHashMap<>();
        @Override public StoredObject put(String objectKey, byte[] content, String contentType) {
            values.put(objectKey, content.clone()); return new StoredObject("test", objectKey);
        }
        @Override public InputStream get(String bucket, String objectKey) {
            return new ByteArrayInputStream(values.get(objectKey));
        }
        @Override public boolean healthy() { return true; }
    }

    static final class FakeChunkIndex implements ChunkIndex {
        private final List<IndexedChunk> chunks = new ArrayList<>();
        @Override public void ensureIndex() { }
        @Override public void replaceVersionChunks(Long versionId, List<IndexedChunk> values) {
            chunks.clear(); chunks.addAll(values);
        }
        @Override public List<IndexSearchHit> keywordSearch(String query, int limit) { return List.of(); }
        @Override public List<IndexSearchHit> vectorSearch(float[] queryVector, int limit) { return List.of(); }
        @Override public boolean healthy() { return true; }
        @Override public String indexVersion() { return "test-index"; }
        void clear() { chunks.clear(); }
    }
}
