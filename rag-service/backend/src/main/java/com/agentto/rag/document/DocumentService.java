package com.agentto.rag.document;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.agentto.rag.ingestion.IngestionJob;
import com.agentto.rag.ingestion.IngestionJobRepository;
import com.agentto.rag.ingestion.parser.DocumentParserFactory;
import com.agentto.rag.knowledgebase.KnowledgeBaseAdminService;
import com.agentto.rag.storage.ObjectStorageService;
import com.agentto.rag.storage.StoredObject;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final IngestionJobRepository jobRepository;
    private final DocumentParserFactory parserFactory;
    private final ObjectStorageService storage;
    private final KnowledgeBaseAdminService knowledgeBaseAdminService;

    public DocumentService(DocumentRepository documentRepository, DocumentVersionRepository versionRepository,
            IngestionJobRepository jobRepository, DocumentParserFactory parserFactory, ObjectStorageService storage,
            KnowledgeBaseAdminService knowledgeBaseAdminService) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.jobRepository = jobRepository;
        this.parserFactory = parserFactory;
        this.storage = storage;
        this.knowledgeBaseAdminService = knowledgeBaseAdminService;
    }

    /**
     * 上传文档到指定知识库。
     * 校验知识库存在且处于 ACTIVE 状态后，保存原始文件并创建入库任务。
     *
     * @param file            上传文件
     * @param knowledgeBaseId 目标知识库 ID
     * @param operatorId      操作者 ID
     * @return 上传结果
     * @throws KnowledgeBaseNotWritableException 当知识库不存在或已被禁用时抛出
     */
    @Transactional
    public UploadResult upload(MultipartFile file, Long knowledgeBaseId, Long operatorId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        // 校验知识库存在且活跃
        knowledgeBaseAdminService.requireActive(knowledgeBaseId);
        String filename = safeFilename(file.getOriginalFilename());
        parserFactory.forFile(filename);
        byte[] bytes = bytes(file);
        String sha256 = sha256(bytes);
        RagDocumentVersion existing = versionRepository.findFirstBySha256OrderByCreatedAtDesc(sha256).orElse(null);
        if (existing != null) {
            return UploadResult.duplicate(existing);
        }
        String objectKey = objectKey(filename);
        StoredObject stored = storage.put(objectKey, bytes, file.getContentType());

        RagDocument document = documentRepository.save(RagDocument.manual(filename, null, knowledgeBaseId, operatorId));
        RagDocumentVersion version = versionRepository.save(RagDocumentVersion.first(document.getId(), filename,
                file.getContentType(), bytes.length, sha256, stored.bucket(), stored.objectKey(), operatorId));
        document.setCurrentVersion(version.getId());
        documentRepository.save(document);
        IngestionJob job = jobRepository.save(IngestionJob.queued(document.getId(), version.getId()));
        return UploadResult.created(document.getId(), version.getId(), job.getId(), stored.objectKey());
    }

    private byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取上传文件失败", exception);
        }
    }

    private String safeFilename(String filename) {
        String value = filename == null ? "" : filename.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        value = slash >= 0 ? value.substring(slash + 1) : value;
        if (value.isBlank()) {
            throw new IllegalArgumentException("上传文件名不能为空");
        }
        return value.length() <= 255 ? value : value.substring(value.length() - 255);
    }

    private String objectKey(String filename) {
        LocalDate date = LocalDate.now();
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return "manual/%d/%02d/%s.%s".formatted(date.getYear(), date.getMonthValue(), UUID.randomUUID(), extension);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

}
