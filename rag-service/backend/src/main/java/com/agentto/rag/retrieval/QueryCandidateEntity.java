package com.agentto.rag.retrieval;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rag_query_candidate")
public class QueryCandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "trace_id", nullable = false)
    private Long traceId;
    @Column(name = "chunk_uid", nullable = false, length = 64)
    private String chunkUid;
    @Column(name = "content_hash", length = 64)
    private String contentHash;
    @Column(name = "dedupe_status", nullable = false, length = 16)
    private String dedupeStatus;
    @Column(name = "duplicate_of_chunk_uid", length = 64)
    private String duplicateOfChunkUid;
    @Column(length = 512)
    private String title;
    private String content;
    @Column(name = "document_id")
    private Long documentId;
    @Column(name = "version_id")
    private Long versionId;
    @Column(name = "metadata_json")
    private String metadataJson;
    @Column(name = "keyword_score")
    private Double keywordScore;
    @Column(name = "keyword_rank")
    private Integer keywordRank;
    @Column(name = "vector_score")
    private Double vectorScore;
    @Column(name = "vector_rank")
    private Integer vectorRank;
    @Column(name = "rrf_score")
    private Double rrfScore;
    @Column(name = "rrf_rank")
    private Integer rrfRank;
    @Column(name = "rerank_score")
    private Double rerankScore;
    @Column(name = "rerank_rank")
    private Integer rerankRank;
    @Column(name = "final_rank")
    private Integer finalRank;
    @Column(nullable = false)
    private boolean selected;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QueryCandidateEntity() {
    }

    public static QueryCandidateEntity create(Long traceId, RetrievalCandidate value) {
        QueryCandidateEntity entity = new QueryCandidateEntity();
        entity.traceId = traceId;
        entity.chunkUid = value.chunkId();
        entity.contentHash = value.contentHash();
        entity.dedupeStatus = value.dedupeStatus() == null ? DedupeStatus.KEPT.name() : value.dedupeStatus().name();
        entity.duplicateOfChunkUid = value.duplicateOfChunkId();
        entity.title = value.title();
        entity.content = value.content();
        entity.documentId = value.documentId();
        entity.versionId = value.versionId();
        entity.metadataJson = value.metadata() == null ? null : value.metadata().toString();
        entity.keywordScore = value.keywordScore();
        entity.keywordRank = value.keywordRank();
        entity.vectorScore = value.vectorScore();
        entity.vectorRank = value.vectorRank();
        entity.rrfScore = value.rrfScore();
        entity.rrfRank = value.rrfRank();
        entity.rerankScore = value.rerankScore();
        entity.rerankRank = value.rerankRank();
        entity.finalRank = value.finalRank();
        entity.selected = value.finalRank() != null;
        entity.createdAt = Instant.now();
        return entity;
    }

    public String getChunkUid() { return chunkUid; }
    public String getContentHash() { return contentHash; }
    public String getDedupeStatus() { return dedupeStatus; }
    public String getDuplicateOfChunkUid() { return duplicateOfChunkUid; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Long getDocumentId() { return documentId; }
    public Long getVersionId() { return versionId; }
    public String getMetadataJson() { return metadataJson; }
    public Double getKeywordScore() { return keywordScore; }
    public Integer getKeywordRank() { return keywordRank; }
    public Double getVectorScore() { return vectorScore; }
    public Integer getVectorRank() { return vectorRank; }
    public Double getRrfScore() { return rrfScore; }
    public Integer getRrfRank() { return rrfRank; }
    public Double getRerankScore() { return rerankScore; }
    public Integer getRerankRank() { return rerankRank; }
    public Integer getFinalRank() { return finalRank; }
    public boolean isSelected() { return selected; }
}
