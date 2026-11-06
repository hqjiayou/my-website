package com.mywebsite.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 解耦迁移回填的真 PG 分阶段验证（#209/#210 深审补强）：现有 VerifyPg 测试都由
 * Spring 启动时把 Flyway 一次迁到最新，断言不了「存量数据被回填成什么」。
 * 本类不用 Spring：先把空库迁到 V56（解耦前夜表形），按 V56 表形造存量数据，
 * 再迁到最新，逐项断言回填结果——
 * ① V57：每个未删空间都有一行创建者订阅；
 * ② V61：嵌入登记按空间扇出，类集（entry_id 集合）与原 agent-scope 行一致；
 * ③ V64：agent_ai_knowledge_document.agent_id 列已删；
 * ④ V63：链预设按「专属在前、平台在后（段内默认在前、id 升序）」生成并回填引用；
 * ⑤ V69：自有链按模式拆分拷贝——KEY 链＝预设中 KEY 成员保序、CLI 链＝CLI 成员保序。
 *
 * <p>执行：{@code mvn test -Ppersistence-verify-pg -pl admin -am -Dtest=MigrationBackfillVerifyPgTest}
 */
@Tag("verifyPg")
class MigrationBackfillVerifyPgTest {

    private static final long AGENT_ID = 900101L;
    private static final long OWN_DEFAULT_ENTRY_ID = 900201L;
    private static final long PLATFORM_ENTRY_ID = 900202L;
    private static final long OWN_ALT_ENTRY_ID = 900203L;
    private static final long OWN_CLI_ENTRY_ID = 900204L;
    private static final long BASE_ID = 900301L;
    private static final long DOCUMENT_ID = 900401L;

    private static final PostgreSQLContainer<?> PG =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("my_website_backfill_verify")
                    .withUsername("test")
                    .withPassword("test");

    @BeforeAll
    static void startPg() {
        PG.start();
    }

    @AfterAll
    static void stopPg() {
        PG.stop();
    }

    @Test
    void stagedMigrationFromV56BackfillsDecouplingShapes() throws Exception {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("56")
                .load()
                .migrate();

        seedV56Fixtures(jdbc);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        // ① V57 回填：指定空间有创建者订阅行，且全库无「未删空间缺创建者订阅」。
        Integer creatorSubscription = jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_ai_knowledge_base_subscription"
                        + " WHERE base_id = ? AND agent_id = ?",
                Integer.class, BASE_ID, AGENT_ID);
        assertThat(creatorSubscription).isEqualTo(1);
        Integer basesMissingCreatorSubscription = jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_ai_knowledge_base b"
                        + " WHERE b.deleted = FALSE AND NOT EXISTS ("
                        + " SELECT 1 FROM agent_ai_knowledge_base_subscription s"
                        + " WHERE s.base_id = b.id AND s.agent_id = b.created_by_agent_id)",
                Integer.class);
        assertThat(basesMissingCreatorSubscription).isZero();

        // ② V61 扇出：空间 scope 的登记行 entry 集与原 agent-scope 类集一致。
        List<Long> fannedOutEntryIds = jdbc.queryForList(
                "SELECT entry_id FROM agent_embedding_class WHERE base_id = ? AND deleted = FALSE",
                Long.class, BASE_ID);
        assertThat(fannedOutEntryIds)
                .containsExactlyInAnyOrder(OWN_DEFAULT_ENTRY_ID, OWN_ALT_ENTRY_ID);

        // ③ V64：document.agent_id 列已不存在。
        Integer documentAgentIdColumns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns"
                        + " WHERE table_name = 'agent_ai_knowledge_document' AND column_name = 'agent_id'",
                Integer.class);
        assertThat(documentAgentIdColumns).isZero();

        // ④ V63 生成＋回填：引用非空，成员序＝专属（默认在前、id 升序）→平台。
        Long chainPresetId = jdbc.queryForObject(
                "SELECT chain_preset_id FROM agent WHERE id = ?", Long.class, AGENT_ID);
        assertThat(chainPresetId).isNotNull();
        String entryIdsJson = jdbc.queryForObject(
                "SELECT entry_ids FROM agent_ai_chain_preset WHERE id = ?", String.class, chainPresetId);
        List<Long> presetEntryIds =
                new ObjectMapper().readValue(entryIdsJson, new TypeReference<List<Long>>() {});
        assertThat(presetEntryIds)
                .containsExactly(
                        OWN_DEFAULT_ENTRY_ID, OWN_CLI_ENTRY_ID, OWN_ALT_ENTRY_ID, PLATFORM_ENTRY_ID);

        // ⑤ V69 拆分拷贝：KEY 链只含预设里的 KEY 成员（保预设序），CLI 链只含 CLI 成员。
        String chainKeyJson = jdbc.queryForObject(
                "SELECT chain_key_entry_ids FROM agent WHERE id = ?", String.class, AGENT_ID);
        List<Long> chainKeyIds =
                new ObjectMapper().readValue(chainKeyJson, new TypeReference<List<Long>>() {});
        assertThat(chainKeyIds)
                .containsExactly(OWN_DEFAULT_ENTRY_ID, OWN_ALT_ENTRY_ID, PLATFORM_ENTRY_ID);
        String chainCliJson = jdbc.queryForObject(
                "SELECT chain_cli_entry_ids FROM agent WHERE id = ?", String.class, AGENT_ID);
        List<Long> chainCliIds =
                new ObjectMapper().readValue(chainCliJson, new TypeReference<List<Long>>() {});
        assertThat(chainCliIds).containsExactly(OWN_CLI_ENTRY_ID);
    }

    /** 按 V56 时点表形造存量：列名/表名以 V56 为准（base 仍用 agent_id、link 表与 document.agent_id 都在）。 */
    private void seedV56Fixtures(JdbcTemplate jdbc) {
        jdbc.update(
                "INSERT INTO agent (id, agent_code, agent_name, agent_type, status)"
                        + " VALUES (?, 'backfill_agent', 'Backfill Agent', 'PERSONAL', 'ACTIVE')",
                AGENT_ID);
        jdbc.update(
                "INSERT INTO agent_model_entry"
                        + " (id, display_name, model_spec, api_key, agent_id, enabled, is_default, mode, embedding_spec)"
                        + " VALUES (?, 'Own Key', 'google:own', 'k-own', ?, TRUE, TRUE, 'KEY', 'google:embed-own')",
                OWN_DEFAULT_ENTRY_ID, AGENT_ID);
        jdbc.update(
                "INSERT INTO agent_model_entry"
                        + " (id, display_name, model_spec, api_key, agent_id, enabled, is_default, mode, embedding_spec)"
                        + " VALUES (?, 'Own Alt', 'openai:own-alt', 'k-alt', ?, TRUE, FALSE, 'KEY', 'openai:embed-alt')",
                OWN_ALT_ENTRY_ID, AGENT_ID);
        jdbc.update(
                "INSERT INTO agent_model_entry"
                        + " (id, display_name, model_spec, api_key, agent_id, enabled, is_default, mode, embedding_spec)"
                        + " VALUES (?, 'Own CLI', 'openai:own-cli', 'k-cli', ?, TRUE, TRUE, 'CLI', NULL)",
                OWN_CLI_ENTRY_ID, AGENT_ID);
        jdbc.update(
                "INSERT INTO agent_model_entry"
                        + " (id, display_name, model_spec, api_key, agent_id, enabled, is_default, mode, embedding_spec)"
                        + " VALUES (?, 'Platform Key', 'google:platform', 'k-platform', NULL, TRUE, TRUE, 'KEY',"
                        + " 'google:embed-platform')",
                PLATFORM_ENTRY_ID);
        jdbc.update(
                "INSERT INTO agent_ai_knowledge_base (id, agent_id, name, is_default, status)"
                        + " VALUES (?, ?, '空间甲', TRUE, 'ACTIVE')",
                BASE_ID, AGENT_ID);
        jdbc.update(
                "INSERT INTO agent_ai_knowledge_document (id, agent_id, base_id, title, content, status)"
                        + " VALUES (?, ?, ?, '文档甲', '内容甲', 'ACTIVE')",
                DOCUMENT_ID, AGENT_ID, BASE_ID);
        jdbc.update(
                "INSERT INTO agent_embedding_class (agent_id, entry_id, enabled, sort_order)"
                        + " VALUES (?, ?, TRUE, 0)",
                AGENT_ID, OWN_DEFAULT_ENTRY_ID);
        jdbc.update(
                "INSERT INTO agent_embedding_class (agent_id, entry_id, enabled, sort_order)"
                        + " VALUES (?, ?, TRUE, 1)",
                AGENT_ID, OWN_ALT_ENTRY_ID);
    }
}
