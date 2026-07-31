package com.agentto.rag.ingestion;

public record ChunkView(
        String chunkUid,
        int ordinal,
        String title,
        String content,
        Integer page,
        String sectionPath,
        String sheetName,
        Integer rowStart,
        Integer rowEnd,
        String metadataJson) {
    static ChunkView from(RagChunkEntity value) {
        return new ChunkView(value.getChunkUid(), value.getOrdinalNo(), value.getTitle(), value.getContent(),
                value.getPageNo(), value.getSectionPath(), value.getSheetName(), value.getRowStart(),
                value.getRowEnd(), value.getMetadataJson());
    }
}
