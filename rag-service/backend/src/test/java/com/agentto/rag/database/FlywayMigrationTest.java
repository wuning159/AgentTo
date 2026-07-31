package com.agentto.rag.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.agentto.rag.RagServiceApplication;

@ActiveProfiles("test")
@SpringBootTest(classes = RagServiceApplication.class)
class FlywayMigrationTest {

    private static final List<String> REQUIRED_TABLES = List.of(
            "rag_admin_user",
            "rag_admin_session",
            "rag_document",
            "rag_document_version",
            "rag_ingestion_job",
            "rag_ingestion_stage",
            "rag_chunk",
            "rag_query_trace",
            "rag_query_candidate");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsAllRagTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "select lower(table_name) from information_schema.tables where table_schema = 'public'",
                String.class);

        assertThat(tables).containsAll(REQUIRED_TABLES);
    }

    @Test
    void createsDuplicateGovernanceAndObservabilityColumns() {
        assertColumnExists("RAG_INGESTION_STAGE", "TECHNICAL_DETAIL_JSON");
        assertColumnExists("RAG_QUERY_TRACE", "RANK_CONSTANT");
        assertColumnExists("RAG_QUERY_TRACE", "DEDUPLICATED_COUNT");
        assertColumnExists("RAG_QUERY_TRACE", "EXECUTION_REPORT_JSON");
        assertColumnExists("RAG_QUERY_CANDIDATE", "CONTENT_HASH");
        assertColumnExists("RAG_QUERY_CANDIDATE", "DEDUPE_STATUS");
        assertColumnExists("RAG_QUERY_CANDIDATE", "DUPLICATE_OF_CHUNK_UID");
    }

    private void assertColumnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns "
                        + "where upper(table_name) = ? and upper(column_name) = ?",
                Integer.class, table, column);
        assertThat(count).isEqualTo(1);
    }
}
