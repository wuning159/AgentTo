package com.agentto.rag.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class V8MigrationContractTest {

    @Test
    void appliesV8OnBaselinedSchema() throws Exception {
        String url = "jdbc:h2:mem:content_asset_v8_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        create table rag_document_version (
                            id bigint primary key auto_increment,
                            document_id bigint not null,
                            version_no int not null,
                            original_filename varchar(255) not null,
                            content_type varchar(128),
                            file_size bigint not null,
                            sha256 varchar(64) not null,
                            object_bucket varchar(128) not null,
                            object_key varchar(512) not null,
                            processing_status varchar(32) not null,
                            chunk_count int not null default 0,
                            created_by bigint not null,
                            created_at timestamp(6) not null
                        )
                        """);
            }

            Flyway.configure()
                    .dataSource(url, "sa", "")
                    .locations("classpath:db/migration")
                    .table("rag_flyway_schema_history")
                    .baselineOnMigrate(true)
                    .baselineVersion("7")
                    .validateMigrationNaming(true)
                    .load()
                    .migrate();

            assertColumn(connection, "rag_content_asset", "storage_state");
            assertColumn(connection, "rag_content_asset", "unreferenced_since");
            assertColumn(connection, "rag_content_asset", "lease_owner");
            assertColumn(connection, "rag_object_cleanup_task", "object_key");
            assertColumn(connection, "rag_document_version", "content_asset_id");
        }
    }

    private void assertColumn(Connection connection, String table, String column) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "select count(*) from information_schema.columns where lower(table_name) = '"
                                + table + "' and lower(column_name) = '" + column + "'")) {
            result.next();
            assertThat(result.getInt(1)).as("%s.%s", table, column).isEqualTo(1);
        }
    }
}
