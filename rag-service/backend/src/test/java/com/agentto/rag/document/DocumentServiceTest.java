package com.agentto.rag.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import com.agentto.rag.asset.CanonicalObjectKey;
import com.agentto.rag.asset.ContentAssetRepository;
import com.agentto.rag.auth.AdminSessionRepository;
import com.agentto.rag.auth.AdminUser;
import com.agentto.rag.auth.AdminUserRepository;
import com.agentto.rag.ingestion.IngestionJobRepository;
import com.agentto.rag.knowledgebase.KnowledgeBase;
import com.agentto.rag.knowledgebase.KnowledgeBaseNotWritableException;
import com.agentto.rag.knowledgebase.KnowledgeBaseRepository;
import com.agentto.rag.storage.InMemoryObjectStorage;

@ActiveProfiles("test")
@SpringBootTest
@Import(DocumentServiceTest.StorageConfiguration.class)
class DocumentServiceTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository versionRepository;

    @Autowired
    private IngestionJobRepository jobRepository;

    @Autowired
    private ContentAssetRepository contentAssetRepository;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private AdminUserRepository userRepository;

    @Autowired
    private AdminSessionRepository sessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InMemoryObjectStorage storage;

    private Long adminId;
    private Long knowledgeBaseId;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        versionRepository.deleteAll();
        contentAssetRepository.deleteAll();
        documentRepository.deleteAll();
        knowledgeBaseRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        storage.clear();
        adminId = userRepository.save(AdminUser.create("admin", "管理员", passwordEncoder.encode("password")))
                .getId();
        KnowledgeBase kb = knowledgeBaseRepository.save(
                new KnowledgeBase("kb-test-001", "测试知识库", "PRIVATE", null));
        knowledgeBaseId = kb.getId();
    }

    @Test
    void uploadStoresImmutableOriginalAndCreatesQueuedIngestionJob() {
        byte[] bytes = "fake-docx-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "制度.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);

        UploadResult result = documentService.upload(file, knowledgeBaseId, adminId);

        assertThat(result.documentId()).isNotNull();
        assertThat(result.versionId()).isNotNull();
        assertThat(result.jobId()).isNotNull();
        assertThat(result.objectKey()).isEqualTo(
                CanonicalObjectKey.of("e76bda917de8995693adb36b33262160e895318a635ca11ac8272b3a630c37b1"));
        assertThat(storage.bytes(result.objectKey())).containsExactly(bytes);
        RagDocumentVersion version = versionRepository.findById(result.versionId()).orElseThrow();
        assertThat(version.getSha256())
                .isEqualTo("e76bda917de8995693adb36b33262160e895318a635ca11ac8272b3a630c37b1");
        assertThat(version.getContentAssetId()).isNotNull();
        assertThat(jobRepository.findById(result.jobId()).orElseThrow().getStatus()).isEqualTo("QUEUED");
        assertThat(documentRepository.findById(result.documentId()).orElseThrow().getKnowledgeBaseId())
                .isEqualTo(knowledgeBaseId);
    }

    @Test
    void exactDuplicateReturnsExistingVersionWithoutWritingStorageOrCreatingJob() {
        byte[] bytes = "same-docx-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile first = new MockMultipartFile("file", "制度.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);
        MockMultipartFile second = new MockMultipartFile("file", "制度副本.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);

        UploadResult created = documentService.upload(first, knowledgeBaseId, adminId);
        UploadResult duplicate = documentService.upload(second, knowledgeBaseId, adminId);

        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.documentId()).isEqualTo(created.documentId());
        assertThat(duplicate.versionId()).isEqualTo(created.versionId());
        assertThat(duplicate.jobId()).isNull();
        assertThat(storage.size()).isOne();
        assertThat(documentRepository.count()).isOne();
        assertThat(versionRepository.count()).isOne();
        assertThat(jobRepository.count()).isOne();
        assertThat(contentAssetRepository.count()).isOne();
    }

    @Test
    void uploadRejectsUnsupportedExtensionBeforeWritingStorage() {
        MockMultipartFile file = new MockMultipartFile("file", "archive.zip", "application/zip", new byte[] { 1 });

        assertThatThrownBy(() -> documentService.upload(file, knowledgeBaseId, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持");
        assertThat(storage.size()).isZero();
    }

    /**
     * 上传到不存在或已被禁用的知识库时应拒绝。
     */
    @Test
    void uploadRejectsUnknownOrDisabledKnowledgeBase() {
        MockMultipartFile file = new MockMultipartFile("file", "制度.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "content".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> documentService.upload(file, 999L, adminId))
                .isInstanceOf(KnowledgeBaseNotWritableException.class)
                .hasMessageContaining("不存在");
        assertThat(storage.size()).isZero();

        KnowledgeBase disabled = knowledgeBaseRepository.save(
                new KnowledgeBase("kb-disabled", "禁用知识库", "PRIVATE", null));
        disabled.setStatus("DISABLED");
        knowledgeBaseRepository.save(disabled);
        assertThatThrownBy(() -> documentService.upload(file, disabled.getId(), adminId))
                .isInstanceOf(KnowledgeBaseNotWritableException.class)
                .hasMessageContaining("禁用");
        assertThat(storage.size()).isZero();
    }

    @TestConfiguration
    static class StorageConfiguration {

        @Bean
        @Primary
        InMemoryObjectStorage memoryStorage() {
            return new InMemoryObjectStorage();
        }
    }
}
