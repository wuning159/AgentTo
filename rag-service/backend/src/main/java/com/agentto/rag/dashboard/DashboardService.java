package com.agentto.rag.dashboard;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentto.rag.document.DocumentRepository;
import com.agentto.rag.embedding.EmbeddingService;
import com.agentto.rag.index.ChunkIndex;
import com.agentto.rag.ingestion.IngestionJobRepository;
import com.agentto.rag.ingestion.RagChunkRepository;
import com.agentto.rag.retrieval.QueryTraceRepository;
import com.agentto.rag.retrieval.RerankService;
import com.agentto.rag.storage.ObjectStorageService;

@Service
public class DashboardService {

    private final DocumentRepository documents;
    private final RagChunkRepository chunks;
    private final QueryTraceRepository traces;
    private final IngestionJobRepository jobs;
    private final ObjectStorageService storage;
    private final ChunkIndex index;
    private final EmbeddingService embedding;
    private final RerankService rerank;

    public DashboardService(DocumentRepository documents, RagChunkRepository chunks, QueryTraceRepository traces,
            IngestionJobRepository jobs, ObjectStorageService storage, ChunkIndex index,
            EmbeddingService embedding, RerankService rerank) {
        this.documents = documents;
        this.chunks = chunks;
        this.traces = traces;
        this.jobs = jobs;
        this.storage = storage;
        this.index = index;
        this.embedding = embedding;
        this.rerank = rerank;
    }

    @Transactional(readOnly = true)
    public DashboardOverview overview() {
        List<DependencyState> states = List.of(
                state("MySQL", () -> documents.count() >= 0, "业务与 Trace 数据"),
                state("MinIO", storage::healthy, "原始文件"),
                state("Elasticsearch", index::healthy, index.indexVersion()),
                state("TEI Embedding", embedding::healthy, "向量生成"),
                state("TEI Rerank", rerank::healthy, "结果精排"));
        return new DashboardOverview(documents.count(), documents.countByStatus("READY"),
                documents.countByStatus("PROCESSING"), documents.countByStatus("FAILED"), chunks.count(),
                traces.count(), jobs.countByStatus("RUNNING"), jobs.countByStatus("FAILED"), states);
    }

    private DependencyState state(String name, BooleanSupplier check, String detail) {
        try {
            return new DependencyState(name, check.getAsBoolean(), detail);
        } catch (RuntimeException exception) {
            return new DependencyState(name, false, exception.getMessage());
        }
    }
}
