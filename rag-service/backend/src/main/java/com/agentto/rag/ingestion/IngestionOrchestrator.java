package com.agentto.rag.ingestion;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.agentto.rag.document.DocumentRepository;
import com.agentto.rag.document.DocumentVersionRepository;
import com.agentto.rag.document.RagDocument;
import com.agentto.rag.document.RagDocumentVersion;
import com.agentto.rag.embedding.EmbeddingService;
import com.agentto.rag.index.ChunkIndex;
import com.agentto.rag.index.IndexedChunk;
import com.agentto.rag.ingestion.chunk.ParsedBlock;
import com.agentto.rag.ingestion.chunk.RagChunk;
import com.agentto.rag.ingestion.chunk.StructureAwareChunker;
import com.agentto.rag.ingestion.parser.DocumentParserFactory;
import com.agentto.rag.observability.TechnicalStageDetail;
import com.agentto.rag.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class IngestionOrchestrator {

    private final IngestionJobRepository jobRepository;
    private final IngestionStageRepository stageRepository;
    private final RagChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final ObjectStorageService storage;
    private final DocumentParserFactory parserFactory;
    private final StructureAwareChunker chunker;
    private final EmbeddingService embeddingService;
    private final ChunkIndex chunkIndex;
    private final ObjectMapper objectMapper;
    private final IngestionTechnicalDetailFactory technicalDetails;

    public IngestionOrchestrator(IngestionJobRepository jobRepository, IngestionStageRepository stageRepository,
            RagChunkRepository chunkRepository, DocumentRepository documentRepository,
            DocumentVersionRepository versionRepository, ObjectStorageService storage,
            DocumentParserFactory parserFactory, StructureAwareChunker chunker, EmbeddingService embeddingService,
            ChunkIndex chunkIndex, ObjectMapper objectMapper, IngestionTechnicalDetailFactory technicalDetails) {
        this.jobRepository = jobRepository;
        this.stageRepository = stageRepository;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.storage = storage;
        this.parserFactory = parserFactory;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.chunkIndex = chunkIndex;
        this.objectMapper = objectMapper;
        this.technicalDetails = technicalDetails;
    }

    public void process(Long jobId) {
        IngestionJob job = jobRepository.findById(jobId).orElseThrow();
        RagDocument document = documentRepository.findById(job.getDocumentId()).orElseThrow();
        RagDocumentVersion version = versionRepository.findById(job.getVersionId()).orElseThrow();
        String currentStage = "STORE";
        Instant stageStarted = Instant.now();
        try {
            job.start(currentStage);
            version.markProcessing();
            jobRepository.save(job);
            versionRepository.save(version);
            stageRepository.save(IngestionStage.success(jobId, currentStage, "原文件已保存", 1,
                    technicalJson(technicalDetails.store(version)), stageStarted));

            currentStage = "PARSE";
            job.stage(currentStage);
            jobRepository.save(job);
            stageStarted = Instant.now();
            List<ParsedBlock> blocks;
            try (InputStream input = storage.get(version.getObjectBucket(), version.getObjectKey())) {
                blocks = parserFactory.forFile(version.getOriginalFilename()).parse(input, version.getOriginalFilename());
            }
            stageRepository.save(IngestionStage.success(jobId, currentStage, "文档解析完成", blocks.size(),
                    technicalJson(technicalDetails.parse(version.getOriginalFilename(), blocks)), stageStarted));

            currentStage = "CLEAN";
            job.stage(currentStage);
            jobRepository.save(job);
            stageStarted = Instant.now();
            List<ParsedBlock> beforeClean = blocks;
            blocks = beforeClean.stream()
                    .map(block -> new ParsedBlock(block.title(), cleanup(block.content()), block.metadata()))
                    .filter(block -> !block.content().isBlank())
                    .toList();
            stageRepository.save(IngestionStage.success(jobId, currentStage, "文本清洗完成", blocks.size(),
                    technicalJson(technicalDetails.clean(beforeClean, blocks)), stageStarted));

            currentStage = "CHUNK";
            job.stage(currentStage);
            jobRepository.save(job);
            stageStarted = Instant.now();
            List<RagChunk> chunks = chunker.chunk(blocks);
            if (chunks.isEmpty()) throw new IllegalStateException("文档没有可建立索引的文本内容");
            stageRepository.save(IngestionStage.success(jobId, currentStage, "结构化分块完成", chunks.size(),
                    technicalJson(technicalDetails.chunk(blocks, chunks)), stageStarted));

            currentStage = "EMBED";
            job.stage(currentStage);
            jobRepository.save(job);
            stageStarted = Instant.now();
            List<float[]> vectors = embedBatches(chunks);
            stageRepository.save(IngestionStage.success(jobId, currentStage, "向量生成完成", vectors.size(),
                    technicalJson(technicalDetails.embed(chunks, vectors, 16)), stageStarted));

            List<RagChunkEntity> entities = createEntities(document.getId(), version.getId(), document.getKnowledgeBaseId(), chunks, vectors);
            chunkRepository.deleteByVersionId(version.getId());
            chunkRepository.saveAll(entities);

            currentStage = "INDEX";
            job.stage(currentStage);
            jobRepository.save(job);
            stageStarted = Instant.now();
            chunkIndex.ensureIndex();
            List<IndexedChunk> indexed = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                RagChunk chunk = chunks.get(i);
                RagChunkEntity entity = entities.get(i);
                indexed.add(new IndexedChunk(entity.getChunkUid(), document.getId(), version.getId(),
                        document.getKnowledgeBaseId(), chunk.ordinal(),
                        chunk.metadata().getOrDefault("section", ""), chunk.content(), chunk.metadata(), vectors.get(i)));
                entity.indexed();
            }
            chunkIndex.replaceVersionChunks(version.getId(), indexed);
            chunkRepository.saveAll(entities);
            stageRepository.save(IngestionStage.success(jobId, currentStage, "Elasticsearch 索引完成", indexed.size(),
                    technicalJson(technicalDetails.index(chunks, chunkIndex.indexVersion())), stageStarted));

            currentStage = "COMPLETE";
            job.stage(currentStage);
            jobRepository.save(job);
            stageStarted = Instant.now();
            version.markReady(chunks.size(), chunkIndex.indexVersion());
            document.markReady();
            job.succeed();
            stageRepository.save(IngestionStage.success(jobId, currentStage, "入库完成", chunks.size(),
                    technicalJson(technicalDetails.complete(chunks.size())), stageStarted));
        } catch (Exception exception) {
            job.fail(currentStage, exception.getMessage());
            version.markFailed();
            document.markFailed();
            stageRepository.save(IngestionStage.failed(jobId, currentStage, exception.getMessage(),
                    technicalJson(technicalDetails.failure(currentStage, exception)), stageStarted));
        }
        jobRepository.save(job);
        versionRepository.save(version);
        documentRepository.save(document);
    }

    private List<float[]> embedBatches(List<RagChunk> chunks) {
        List<float[]> result = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += 16) {
            int end = Math.min(start + 16, chunks.size());
            result.addAll(embeddingService.embed(chunks.subList(start, end).stream().map(RagChunk::content).toList()));
        }
        if (result.size() != chunks.size()) throw new IllegalStateException("向量数量与切片数量不一致");
        return result;
    }

    private List<RagChunkEntity> createEntities(Long documentId, Long versionId, Long knowledgeBaseId, List<RagChunk> chunks,
            List<float[]> vectors) {
        List<RagChunkEntity> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            RagChunk chunk = chunks.get(i);
            Map<String, String> metadata = chunk.metadata();
            String hash = sha256(chunk.content());
            String uid = versionId + "-" + chunk.ordinal() + "-" + hash.substring(0, 12);
            result.add(RagChunkEntity.create(uid, documentId, versionId, knowledgeBaseId, chunk.ordinal(),
                    metadata.getOrDefault("section", ""), chunk.content(), hash,
                    integer(metadata.get("page")), metadata.get("section"), metadata.get("sheet"),
                    integer(metadata.get("rowStart")), integer(metadata.get("rowEnd")), json(metadata),
                    vectors.get(i).length));
        }
        return result;
    }

    private String cleanup(String value) {
        return value.replace('\u0000', ' ').replaceAll("[\\t ]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private Integer integer(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value); } catch (NumberFormatException ignored) { return null; }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("切片元数据序列化失败", exception); }
    }

    private String technicalJson(TechnicalStageDetail detail) {
        return json(detail);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 不可用", exception); }
    }
}
