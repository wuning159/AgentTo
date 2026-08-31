package com.agentto.rag.document;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.agentto.rag.asset.ContentAsset;
import com.agentto.rag.asset.ContentAssetService;
import com.agentto.rag.asset.UploadedDocument;
import com.agentto.rag.ingestion.IngestionJob;
import com.agentto.rag.ingestion.IngestionJobRepository;
import com.agentto.rag.ingestion.parser.DocumentParserFactory;
import com.agentto.rag.knowledgebase.KnowledgeBaseAdminService;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final IngestionJobRepository jobRepository;
    private final DocumentParserFactory parserFactory;
    private final ContentAssetService contentAssetService;
    private final KnowledgeBaseAdminService knowledgeBaseAdminService;
    private final TransactionTemplate transactionTemplate;

    public DocumentService(DocumentRepository documentRepository, DocumentVersionRepository versionRepository,
            IngestionJobRepository jobRepository, DocumentParserFactory parserFactory,
            ContentAssetService contentAssetService, KnowledgeBaseAdminService knowledgeBaseAdminService,
            TransactionTemplate transactionTemplate) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.jobRepository = jobRepository;
        this.parserFactory = parserFactory;
        this.contentAssetService = contentAssetService;
        this.knowledgeBaseAdminService = knowledgeBaseAdminService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 上传文档到指定知识库。
     * 校验知识库存在且处于 ACTIVE 状态后，按内容寻址保存原始文件并创建入库任务。
     *
     * @param file            上传文件
     * @param knowledgeBaseId 目标知识库 ID
     * @param operatorId      操作者 ID
     * @return 上传结果
     * @throws KnowledgeBaseNotWritableException 当知识库不存在或已被禁用时抛出
     */
    public UploadResult upload(MultipartFile file, Long knowledgeBaseId, Long operatorId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        knowledgeBaseAdminService.requireActive(knowledgeBaseId);
        String filename = safeFilename(file.getOriginalFilename());
        parserFactory.forFile(filename);
        ContentAsset asset = contentAssetService.storeOrReuse(UploadedDocument.from(file));
        contentAssetService.requireReadyForReference(asset.getId());
        return persistUpload(filename, file.getContentType(), knowledgeBaseId, operatorId, asset);
    }

    private UploadResult persistUpload(String filename, String contentType, Long knowledgeBaseId, Long operatorId,
            ContentAsset asset) {
        return transactionTemplate.execute(status -> {
            RagDocumentVersion existing = versionRepository.findFirstBySha256OrderByCreatedAtDesc(asset.getSha256())
                    .orElse(null);
            if (existing != null) {
                return UploadResult.duplicate(existing);
            }
            RagDocument document = documentRepository.save(RagDocument.manual(filename, null, knowledgeBaseId, operatorId));
            RagDocumentVersion version = versionRepository.save(RagDocumentVersion.first(document.getId(), filename,
                    contentType, asset.getContentLength(), asset.getSha256(), asset.getBucket(), asset.getObjectKey(),
                    asset.getId(), operatorId));
            document.setCurrentVersion(version.getId());
            documentRepository.save(document);
            IngestionJob job = jobRepository.save(IngestionJob.queued(document.getId(), version.getId()));
            return UploadResult.created(document.getId(), version.getId(), job.getId(), asset.getObjectKey());
        });
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
}
