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
 * ⑤ V69：自有链按模式拆分拷贝——KEY 链＝预设中 KEY 成员保序、CLI 链＝CLI 成员保序；
 * ⑥ V70：profile→预设（V60）→模板＋本地副本两跳平移后 prompt 逐字等价，无 profile
 * 的 agent 副本保持全空；另以 V69 为起点单独验证预设的 skills 部分拷入自有集、
 * 共享预设各 agent 各持一份、未引用/引用已删预设不回填。
 *
 * <p>执行：{@code mvn test -Ppersistence-verify-pg -pl admin -am -Dtest=MigrationBackfillVerifyPgTest}
 */
@Tag("verifyPg")
class MigrationBackfillVerifyPgTest {

    private static final long AGENT_ID = 900101L;
    private static final long PROFILE_AGENT_ID = 900102L;
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

        // ⑥ V70 两跳平移：PROFILE_AGENT 的旧 profile（V60 先成预设）被拷为本地
        // 副本并生成同名规则模板（「<agent名> 的配置预设」），prompt/温度逐字等价、
        // 技能集为空（旧 profile 本就没有技能部分）；AGENT_ID 无 profile、从未
        // 引用预设，副本四字段保持全空（兜底不固化）。
        String copiedPrompt = jdbc.queryForObject(
                "SELECT prompt_system_prompt FROM agent WHERE id = ?", String.class, PROFILE_AGENT_ID);
        assertThat(copiedPrompt).isEqualTo("存量提示词");
        java.math.BigDecimal copiedTemperature = jdbc.queryForObject(
                "SELECT prompt_temperature FROM agent WHERE id = ?",
                java.math.BigDecimal.class, PROFILE_AGENT_ID);
        assertThat(copiedTemperature).isEqualByComparingTo("0.35");
        Long traceTemplateId = jdbc.queryForObject(
                "SELECT prompt_template_id FROM agent WHERE id = ?", Long.class, PROFILE_AGENT_ID);
        assertThat(traceTemplateId).isNotNull();
        String templateName = jdbc.queryForObject(
                "SELECT name FROM agent_prompt_template WHERE id = ?", String.class, traceTemplateId);
        assertThat(templateName).isEqualTo("Backfill Agent 2 的配置预设");
        String templatePrompt = jdbc.queryForObject(
                "SELECT system_prompt FROM agent_prompt_template WHERE id = ?", String.class, traceTemplateId);
        assertThat(templatePrompt).isEqualTo("存量提示词");
        assertThat(jdbc.queryForObject(
                        "SELECT default_skill_ids FROM agent WHERE id = ?", String.class, PROFILE_AGENT_ID))
                .isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM agent WHERE id = ? AND prompt_system_prompt IS NULL"
                                + " AND prompt_temperature IS NULL AND prompt_template_id IS NULL"
                                + " AND default_skill_ids IS NULL",
                        Integer.class, AGENT_ID))
                .isEqualTo(1);
    }

    /**
     * V70 预设拆分（以 V69 为起点）：未删预设逐条生成同名模板；引用 agent 把
     * prompt 拷为本地副本、skills 拷入自有集、留痕指向生成模板；共享预设的
     * 多个 agent 各持一份且留痕同指一条模板；未引用与引用已删预设的 agent
     * 保持全空；已删预设不生成模板；config_preset_id 列值原样保留（停用不清空）。
     */
    @Test
    void stagedMigrationFromV69SplitsConfigPresetsIntoTemplatesAndOwnCopies() throws Exception {
        long presetShared = 900501L;
        long presetIdle = 900502L;
        long presetDeleted = 900503L;
        long agentA = 900601L;
        long agentB = 900602L;
        long agentBare = 900603L;
        long agentStale = 900604L;

        DriverManagerDataSource adminDataSource =
                new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        JdbcTemplate adminJdbc = new JdbcTemplate(adminDataSource);
        adminJdbc.execute("DROP DATABASE IF EXISTS my_website_prompt_verify");
        adminJdbc.execute("CREATE DATABASE my_website_prompt_verify");
        String promptDbUrl = PG.getJdbcUrl().replace("my_website_backfill_verify", "my_website_prompt_verify");
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(promptDbUrl, PG.getUsername(), PG.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("69")
                .load()
                .migrate();

        jdbc.update(
                "INSERT INTO agent_ai_config_preset (id, name, system_prompt, temperature, default_skill_ids)"
                        + " VALUES (?, '共享口径', '你是共享助手', 0.70, '[\"skill-b\",\"skill-a\"]')",
                presetShared);
        jdbc.update(
                "INSERT INTO agent_ai_config_preset (id, name, system_prompt, temperature, default_skill_ids)"
                        + " VALUES (?, '闲置口径', '闲置提示', NULL, '[\"skill-x\"]')",
                presetIdle);
        jdbc.update(
                "INSERT INTO agent_ai_config_preset (id, name, system_prompt, temperature, default_skill_ids, deleted)"
                        + " VALUES (?, '已删口径', '已删提示', NULL, NULL, TRUE)",
                presetDeleted);
        for (long[] row : new long[][] {
            {agentA, presetShared}, {agentB, presetShared}, {agentBare, 0L}, {agentStale, presetDeleted}
        }) {
            jdbc.update(
                    "INSERT INTO agent (id, agent_code, agent_name, agent_type, status, config_preset_id)"
                            + " VALUES (?, ?, ?, 'PERSONAL', 'ACTIVE', ?)",
                    row[0],
                    "prompt_agent_" + row[0],
                    "Prompt Agent " + row[0],
                    row[1] == 0L ? null : row[1]);
        }

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        // 模板只从未删预设生成（共享＋闲置＝2 条），名称/prompt 逐字。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_prompt_template", Integer.class))
                .isEqualTo(2);
        Long sharedTemplateId = jdbc.queryForObject(
                "SELECT id FROM agent_prompt_template WHERE name = '共享口径'", Long.class);
        assertThat(jdbc.queryForObject(
                        "SELECT system_prompt FROM agent_prompt_template WHERE id = ?",
                        String.class, sharedTemplateId))
                .isEqualTo("你是共享助手");

        // 引用共享预设的两个 agent：副本与技能集逐字、留痕同指生成模板。
        for (long agentId : new long[] {agentA, agentB}) {
            assertThat(jdbc.queryForObject(
                            "SELECT prompt_system_prompt FROM agent WHERE id = ?", String.class, agentId))
                    .isEqualTo("你是共享助手");
            assertThat(jdbc.queryForObject(
                            "SELECT prompt_temperature FROM agent WHERE id = ?",
                            java.math.BigDecimal.class, agentId))
                    .isEqualByComparingTo("0.70");
            String skillsJson = jdbc.queryForObject(
                    "SELECT default_skill_ids FROM agent WHERE id = ?", String.class, agentId);
            assertThat(new ObjectMapper().readValue(skillsJson, new TypeReference<List<String>>() {}))
                    .containsExactly("skill-b", "skill-a");
            assertThat(jdbc.queryForObject(
                            "SELECT prompt_template_id FROM agent WHERE id = ?", Long.class, agentId))
                    .isEqualTo(sharedTemplateId);
            // 列保留口径：config_preset_id 值不动（运行时停读，删除留 UoW-7）。
            assertThat(jdbc.queryForObject(
                            "SELECT config_preset_id FROM agent WHERE id = ?", Long.class, agentId))
                    .isEqualTo(presetShared);
        }

        // 未引用与引用已删预设的 agent：四字段全空，兜底集不固化。
        for (long agentId : new long[] {agentBare, agentStale}) {
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM agent WHERE id = ? AND prompt_system_prompt IS NULL"
                                    + " AND prompt_temperature IS NULL AND prompt_template_id IS NULL"
                                    + " AND default_skill_ids IS NULL",
                            Integer.class, agentId))
                    .isEqualTo(1);
        }
    }

    /** 按 V56 时点表形造存量：列名/表名以 V56 为准（base 仍用 agent_id、link 表与 document.agent_id 都在）。 */
    private void seedV56Fixtures(JdbcTemplate jdbc) {
        jdbc.update(
                "INSERT INTO agent (id, agent_code, agent_name, agent_type, status)"
                        + " VALUES (?, 'backfill_agent', 'Backfill Agent', 'PERSONAL', 'ACTIVE')",
                AGENT_ID);
        // 带旧 profile 的存量 agent：V60 平移为预设、V70 再拷为本地副本（断言 ⑥）。
        jdbc.update(
                "INSERT INTO agent (id, agent_code, agent_name, agent_type, status)"
                        + " VALUES (?, 'profile_agent', 'Backfill Agent 2', 'PERSONAL', 'ACTIVE')",
                PROFILE_AGENT_ID);
        jdbc.update(
                "INSERT INTO agent_ai_prompt_profile (agent_id, system_prompt, temperature)"
                        + " VALUES (?, '存量提示词', 0.35)",
                PROFILE_AGENT_ID);
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
