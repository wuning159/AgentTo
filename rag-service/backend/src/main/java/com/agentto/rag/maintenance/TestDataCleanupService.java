package com.agentto.rag.maintenance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentto.rag.index.ChunkIndex;
import com.agentto.rag.storage.ObjectStorageService;

@Service
public class TestDataCleanupService {

    private final JdbcTemplate jdbcTemplate;
    private final ChunkIndex chunkIndex;
    private final ObjectStorageService storage;

    public TestDataCleanupService(JdbcTemplate jdbcTemplate, ChunkIndex chunkIndex, ObjectStorageService storage) {
        this.jdbcTemplate = jdbcTemplate;
        this.chunkIndex = chunkIndex;
        this.storage = storage;
    }

    @Transactional
    public CleanupResult cleanup() {
        int documents = count("rag_document");
        int versions = count("rag_document_version");
        int chunks = count("rag_chunk");
        int jobs = count("rag_ingestion_job");
        int traces = count("rag_query_trace");

        chunkIndex.clearAll();
        storage.clearAll();

        jdbcTemplate.update("delete from rag_query_candidate");
        jdbcTemplate.update("delete from rag_query_trace");
        jdbcTemplate.update("delete from rag_ingestion_stage");
        jdbcTemplate.update("delete from rag_ingestion_job");
        jdbcTemplate.update("delete from rag_chunk");
        jdbcTemplate.update("delete from rag_document_version");
        jdbcTemplate.update("delete from rag_document");
        return new CleanupResult(documents, versions, chunks, jobs, traces, true, true);
    }

    private int count(String table) {
        Integer value = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
        return value == null ? 0 : value;
    }
}
