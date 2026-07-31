package com.agentto.rag.ingestion;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rag_chunk")
public class RagChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "chunk_uid", nullable = false, unique = true, length = 64)
    private String chunkUid;
    @Column(name = "document_id", nullable = false)
    private Long documentId;
    @Column(name = "version_id", nullable = false)
    private Long versionId;
    @Column(name = "ordinal_no", nullable = false)
    private int ordinalNo;
    private String title;
    @Column(nullable = false)
    private String content;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Column(name = "page_no")
    private Integer pageNo;
    @Column(name = "section_path")
    private String sectionPath;
    @Column(name = "sheet_name")
    private String sheetName;
    @Column(name = "row_start")
    private Integer rowStart;
    @Column(name = "row_end")
    private Integer rowEnd;
    @Column(name = "metadata_json")
    private String metadataJson;
    @Column(name = "embedding_model")
    private String embeddingModel;
    @Column(name = "embedding_dimensions")
    private Integer embeddingDimensions;
    @Column(name = "indexed_at")
    private Instant indexedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RagChunkEntity() {
    }

    public static RagChunkEntity create(String uid, Long documentId, Long versionId, int ordinal, String title,
            String content, String contentHash, Integer page, String section, String sheet, Integer rowStart,
            Integer rowEnd, String metadataJson, int dimensions) {
        RagChunkEntity entity = new RagChunkEntity();
        entity.chunkUid = uid;
        entity.documentId = documentId;
        entity.versionId = versionId;
        entity.ordinalNo = ordinal;
        entity.title = title;
        entity.content = content;
        entity.contentHash = contentHash;
        entity.pageNo = page;
        entity.sectionPath = section;
        entity.sheetName = sheet;
        entity.rowStart = rowStart;
        entity.rowEnd = rowEnd;
        entity.metadataJson = metadataJson;
        entity.embeddingModel = "bge-large-zh-v1.5";
        entity.embeddingDimensions = dimensions;
        entity.createdAt = Instant.now();
        return entity;
    }

    public void indexed() { indexedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getChunkUid() { return chunkUid; }
    public Long getDocumentId() { return documentId; }
    public Long getVersionId() { return versionId; }
    public int getOrdinalNo() { return ordinalNo; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Integer getPageNo() { return pageNo; }
    public String getSectionPath() { return sectionPath; }
    public String getSheetName() { return sheetName; }
    public Integer getRowStart() { return rowStart; }
    public Integer getRowEnd() { return rowEnd; }
    public String getMetadataJson() { return metadataJson; }
}
