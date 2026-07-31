package com.agentto.rag.document;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rag_document_version")
public class RagDocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "document_id", nullable = false)
    private Long documentId;
    @Column(name = "version_no", nullable = false)
    private int versionNo;
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;
    @Column(name = "content_type")
    private String contentType;
    @Column(name = "file_size", nullable = false)
    private long fileSize;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "object_bucket", nullable = false)
    private String objectBucket;
    @Column(name = "object_key", nullable = false)
    private String objectKey;
    @Column(name = "processing_status", nullable = false)
    private String processingStatus;
    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;
    @Column(name = "index_version")
    private String indexVersion;
    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RagDocumentVersion() {
    }

    private RagDocumentVersion(Long documentId, String filename, String contentType, long fileSize, String sha256,
            StoredLocation location, Long createdBy) {
        this.documentId = documentId;
        this.versionNo = 1;
        this.originalFilename = filename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.objectBucket = location.bucket();
        this.objectKey = location.objectKey();
        this.processingStatus = "QUEUED";
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public static RagDocumentVersion first(Long documentId, String filename, String contentType, long fileSize,
            String sha256, String bucket, String objectKey, Long createdBy) {
        return new RagDocumentVersion(documentId, filename, contentType, fileSize, sha256,
                new StoredLocation(bucket, objectKey), createdBy);
    }

    public void markProcessing() { processingStatus = "PROCESSING"; }
    public void markReady(int count, String version) { processingStatus = "READY"; chunkCount = count; indexVersion = version; }
    public void markFailed() { processingStatus = "FAILED"; }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public int getVersionNo() { return versionNo; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public String getSha256() { return sha256; }
    public String getObjectBucket() { return objectBucket; }
    public String getObjectKey() { return objectKey; }
    public String getProcessingStatus() { return processingStatus; }
    public int getChunkCount() { return chunkCount; }
    public String getIndexVersion() { return indexVersion; }
    public Instant getCreatedAt() { return createdAt; }

    private record StoredLocation(String bucket, String objectKey) {
    }
}
