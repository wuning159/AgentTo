package com.agentto.rag.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

/**
 * MySQL 真实迁移集成测试。
 *
 * <p>使用与生产一致的 mysql:8.4 容器执行 Flyway 全量迁移（V1-V7），
 * 验证迁移脚本在真实 MySQL 方言下可执行，并生成完整业务表结构。
 * 不加载 Spring 上下文，仅验证迁移层本身。
 */
class MySqlMigrationIntegrationTest {

    @Test
    void migratesV1ThroughV6OnRealMySql() throws Exception {
        try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("agentto_rag_it")
                .withUsername("rag_it")
                .withPassword("rag_it_password")) {
            mysql.start();

            // 复刻 application.yml 中的 Flyway 配置
            MigrateResult result = Flyway.configure()
                    .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .table("rag_flyway_schema_history")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .validateMigrationNaming(true)
                    .load()
                    .migrate();

            assertThat(result.migrationsExecuted).as("V1-V7 全部执行").isEqualTo(7);

            try (Connection connection = DriverManager.getConnection(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                assertTableExists(connection, "rag_admin_user");
                assertTableExists(connection, "rag_admin_session");
                assertTableExists(connection, "rag_document");
                assertTableExists(connection, "rag_document_version");
                assertTableExists(connection, "rag_ingestion_job");
                assertTableExists(connection, "rag_ingestion_stage");
                assertTableExists(connection, "rag_chunk");
                assertTableExists(connection, "rag_query_trace");
                assertTableExists(connection, "rag_query_candidate");
                assertTableExists(connection, "rag_knowledge_base");
                assertTableExists(connection, "rag_client_application");
                assertTableExists(connection, "rag_knowledge_base_grant");
                assertTableExists(connection, "rag_client_api_key");
                assertTableExists(connection, "rag_query_flow_trace");

                assertColumnExists(connection, "rag_document", "knowledge_base_id");
                assertColumnExists(connection, "rag_chunk", "knowledge_base_id");
                assertColumnExists(connection, "rag_ingestion_stage", "technical_detail_json");
                assertColumnExists(connection, "rag_query_trace", "execution_report_json");
                assertColumnExists(connection, "rag_query_candidate", "dedupe_status");
                assertColumnExists(connection, "rag_query_flow_trace", "profile_shortlist_json");

                assertNoForeignKey(connection, "rag_query_trace", "fk_rag_query_trace_creator");
            }
        }
    }

    /** 验证 V7 已解除 created_by 外键：公共 API 调用方（client application）也可写入检索 Trace */
    private void assertNoForeignKey(Connection connection, String table, String constraint) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "select count(*) from information_schema.table_constraints "
                                + "where table_schema = database() and lower(table_name) = '"
                                + table + "' and lower(constraint_name) = '" + constraint + "'")) {
            result.next();
            assertThat(result.getInt(1)).as("外键 %s 应当已被 V7 解除", constraint).isZero();
        }
    }

    private void assertTableExists(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "select count(*) from information_schema.tables "
                                + "where table_schema = database() and lower(table_name) = '"
                                + table + "'")) {
            result.next();
            assertThat(result.getInt(1)).as("表 %s 应当存在", table).isEqualTo(1);
        }
    }

    private void assertColumnExists(Connection connection, String table, String column) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "select count(*) from information_schema.columns "
                                + "where table_schema = database() and lower(table_name) = '"
                                + table + "' and lower(column_name) = '" + column + "'")) {
            result.next();
            assertThat(result.getInt(1)).as("列 %s.%s 应当存在", table, column).isEqualTo(1);
        }
    }
}
