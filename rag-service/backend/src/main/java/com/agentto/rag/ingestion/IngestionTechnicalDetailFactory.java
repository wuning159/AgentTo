package com.agentto.rag.ingestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.agentto.rag.document.RagDocumentVersion;
import com.agentto.rag.index.ElasticsearchProperties;
import com.agentto.rag.ingestion.chunk.ParsedBlock;
import com.agentto.rag.ingestion.chunk.RagChunk;
import com.agentto.rag.observability.TechnicalStageDetail;

@Component
public class IngestionTechnicalDetailFactory {

    private static final int SAMPLE_LIMIT = 3;
    private static final int SAMPLE_TEXT_LIMIT = 500;

    private final ChunkingProperties chunking;
    private final ElasticsearchProperties elasticsearch;

    public IngestionTechnicalDetailFactory(ChunkingProperties chunking, ElasticsearchProperties elasticsearch) {
        this.chunking = chunking;
        this.elasticsearch = elasticsearch;
    }

    public TechnicalStageDetail store(RagDocumentVersion version) {
        return new TechnicalStageDetail("原始文件已经保存到对象存储，并记录不可变文件哈希", 1, 1,
                Map.of("storage", "MINIO", "sha256", version.getSha256()),
                Map.of("fileSizeBytes", version.getFileSize()), List.of(), Map.of());
    }

    public TechnicalStageDetail parse(String filename, List<ParsedBlock> blocks) {
        long headings = blocks.stream().filter(block -> !block.title().isBlank()).count();
        long tables = blocks.stream().filter(block -> "table".equalsIgnoreCase(block.metadata().get("type"))).count();
        return new TechnicalStageDetail("按文档原始顺序提取标题、段落和表格，保留结构来源信息", 1, blocks.size(),
                Map.of("parser", parserName(filename), "preserveDocumentOrder", true),
                Map.of("blocks", blocks.size(), "headings", headings, "tables", tables),
                blockSamples(blocks), Map.of());
    }

    public TechnicalStageDetail clean(List<ParsedBlock> before, List<ParsedBlock> after) {
        return new TechnicalStageDetail("合并连续空白、清理空段落并保留标题路径和来源元数据",
                before.size(), after.size(),
                Map.of("rules", List.of("NULL_CHARACTER", "REPEATED_WHITESPACE", "EMPTY_BLOCK")),
                Map.of("removedBlocks", Math.max(0, before.size() - after.size())),
                blockSamples(after), Map.of());
    }

    public TechnicalStageDetail chunk(List<ParsedBlock> blocks, List<RagChunk> chunks) {
        long oversized = blocks.stream().filter(block -> block.content().length() > chunking.maxChars()).count();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("strategy", "STRUCTURE_AWARE_PARAGRAPH");
        parameters.put("targetChars", chunking.targetChars());
        parameters.put("maxChars", chunking.maxChars());
        parameters.put("overlapChars", chunking.overlapChars());
        parameters.put("oversizedSplitOrder", List.of("SENTENCE", "CLAUSE", "HARD_LIMIT"));
        return new TechnicalStageDetail("先按标题和段落组合；大段落再按句子、次级标点和字符上限切分",
                blocks.size(), chunks.size(), parameters,
                Map.of("oversizedParagraphs", oversized, "generatedChunks", chunks.size()),
                chunkSamples(chunks), Map.of());
    }

    public TechnicalStageDetail embed(List<RagChunk> chunks, List<float[]> vectors, int batchSize) {
        int dimensions = vectors.isEmpty() ? elasticsearch.dimensions() : vectors.get(0).length;
        List<Map<String, Object>> samples = new ArrayList<>();
        for (int index = 0; index < Math.min(SAMPLE_LIMIT, vectors.size()); index++) {
            float[] vector = vectors.get(index);
            List<Float> preview = new ArrayList<>();
            for (int dimension = 0; dimension < Math.min(8, vector.length); dimension++) {
                preview.add(vector[dimension]);
            }
            samples.add(Map.of("chunkOrdinal", chunks.get(index).ordinal(), "dimensions", vector.length,
                    "vectorPreview", preview));
        }
        return new TechnicalStageDetail("分批调用 TEI Embedding 服务，把每个分块转换为稠密向量",
                chunks.size(), vectors.size(),
                Map.of("provider", "TEI", "model", "bge-large-zh-v1.5", "batchSize", batchSize,
                        "dimensions", dimensions),
                Map.of("generatedVectors", vectors.size()), samples, Map.of());
    }

    public TechnicalStageDetail index(List<RagChunk> chunks, String indexVersion) {
        return new TechnicalStageDetail("把正文、来源元数据和向量写入 Elasticsearch 混合检索索引",
                chunks.size(), chunks.size(),
                Map.of("engine", "ELASTICSEARCH", "index", indexVersion,
                        "dimensions", elasticsearch.dimensions()),
                Map.of("indexedChunks", chunks.size()), chunkSamples(chunks), Map.of());
    }

    public TechnicalStageDetail complete(int count) {
        return new TechnicalStageDetail("文档已经可以参与关键词召回和向量召回", count, count,
                Map.of(), Map.of("readyChunks", count), List.of(), Map.of());
    }

    public TechnicalStageDetail failure(String stage, Exception exception) {
        String message = exception.getMessage();
        return new TechnicalStageDetail("阶段执行失败", null, null,
                Map.of("stage", stage),
                Map.of("exceptionType", exception.getClass().getSimpleName(),
                        "message", message == null || message.isBlank() ? "未提供错误说明" : truncate(message)),
                List.of(), Map.of());
    }

    private List<Map<String, Object>> blockSamples(List<ParsedBlock> blocks) {
        List<Map<String, Object>> samples = new ArrayList<>();
        for (ParsedBlock block : blocks.stream().limit(SAMPLE_LIMIT).toList()) {
            samples.add(Map.of("title", truncate(block.title()), "content", truncate(block.content()),
                    "metadata", block.metadata()));
        }
        return List.copyOf(samples);
    }

    private List<Map<String, Object>> chunkSamples(List<RagChunk> chunks) {
        List<Map<String, Object>> samples = new ArrayList<>();
        for (RagChunk chunk : chunks.stream().limit(SAMPLE_LIMIT).toList()) {
            samples.add(Map.of("ordinal", chunk.ordinal(), "content", truncate(chunk.content()),
                    "metadata", chunk.metadata()));
        }
        return List.copyOf(samples);
    }

    private String parserName(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".docx")) return "APACHE_POI_DOCX";
        if (lower.endsWith(".pdf")) return "PDFBOX";
        return "APACHE_POI_EXCEL";
    }

    private String truncate(String value) {
        if (value == null) return "";
        return value.length() <= SAMPLE_TEXT_LIMIT ? value : value.substring(0, SAMPLE_TEXT_LIMIT) + "…";
    }
}
