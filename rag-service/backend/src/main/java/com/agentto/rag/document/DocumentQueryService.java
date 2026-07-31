package com.agentto.rag.document;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentto.rag.common.api.BusinessException;

@Service
public class DocumentQueryService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;

    public DocumentQueryService(DocumentRepository documentRepository, DocumentVersionRepository versionRepository) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
    }

    @Transactional(readOnly = true)
    public Page<DocumentSummary> list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return documentRepository.findAllByOrderByUpdatedAtDesc(PageRequest.of(safePage, safeSize)).map(this::summary);
    }

    @Transactional(readOnly = true)
    public DocumentDetail detail(Long documentId) {
        RagDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException("DOCUMENT_NOT_FOUND", "文档不存在", HttpStatus.NOT_FOUND));
        List<DocumentVersionView> versions = versionRepository.findByDocumentIdOrderByVersionNoDesc(documentId)
                .stream().map(this::version).toList();
        return new DocumentDetail(summary(document), versions);
    }

    private DocumentSummary summary(RagDocument value) {
        return new DocumentSummary(value.getId(), value.getName(), value.getCategory(), value.getSourceType(),
                value.getStatus(), value.getCurrentVersionId(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private DocumentVersionView version(RagDocumentVersion value) {
        return new DocumentVersionView(value.getId(), value.getVersionNo(), value.getOriginalFilename(),
                value.getContentType(), value.getFileSize(), value.getSha256(), value.getProcessingStatus(),
                value.getChunkCount(), value.getIndexVersion(), value.getCreatedAt());
    }
}
