package com.mywebsite.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * RBAC 种子 Flyway（V1–V30：含 Agent/User/Media/Content/AI；模板与预约权限已由 V30 下架）在真实 PostgreSQL 上的迁移与数据验证。
 *
 * <p>默认由 Testcontainers 自举一次性 PostgreSQL（库名 {@code my_website_rbac_verify}）；执行：
 * {@code mvn clean test -Ppersistence-verify-pg -pl admin -am -Dtest=AdminRbacSeedVerifyPgTest}
 */
@Tag("verifyPg")
@SpringBootTest(classes = AdminApplication.class)
@ActiveProfiles("verify-pg-rbac")
@ImportAutoConfiguration(
        exclude = {
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
class AdminRbacSeedVerifyPgTest {

    private static final Set<String> EXPECTED_ACTIVE_PERMISSION_CODES = Set.of(
            "admin:role:list",
            "admin:role:detail",
            "admin:role:create",
            "admin:role:update",
            "admin:role:delete",
            "admin:role:permission:view",
            "admin:role:permission:update",
            "admin:permission:list",
            "admin:user:list",
            "admin:user:detail",
            "admin:user:create",
            "admin:user:update",
            "admin:user:delete",
            "admin:user:status",
            "admin:user:role:view",
            "admin:user:role:update",
            "admin:agent:list",
            "admin:agent:detail",
            "admin:agent:create",
            "admin:agent:update",
            "admin:agent:delete",
            "admin:agent:status",
            "admin:media:list",
            "admin:media:upload",
            "admin:media:download",
            "admin:media:delete",
            "admin:content:list",
            "admin:content:create",
            "admin:content:update",
            "admin:content:delete",
            "admin:content:status",
            "admin:dashboard:view",
            "admin:menu:system",
            "admin:menu:system.user",
            "admin:menu:system.role",
            "admin:menu:system.permission",
            "admin:menu:system.menu",
            "admin:menu:apps",
            "admin:menu:apps.sites",
            "admin:menu:list",
            "admin:menu:detail",
            "admin:menu:create",
            "admin:menu:update",
            "admin:menu:delete",
            "admin:menu:sort",
            "admin:menu:bind",
            "admin:ai-prompt:read",
            "admin:ai-prompt:update",
            "admin:ai-kb:list",
            "admin:ai-kb:create",
            "admin:ai-kb:update",
            "admin:ai-kb:delete",
            "admin:ai-conversation:list",
            "admin:ai-model:read",
            "admin:ai-model:update");

    private static final int EXPECTED_FLYWAY_VERSION_COUNT = 36;
    private static final int EXPECTED_ACTIVE_PERMISSION_COUNT = 55;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayV1ThroughV21AppliedWithExpectedRbacSeed() {
        List<Map<String, Object>> migrations = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(migrations)
                .extracting(row -> row.get("version"))
                .contains(
                        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15",
                        "16", "17", "18", "19", "20", "21", "22", "23", "24");
        assertThat(migrations).allMatch(row -> Boolean.TRUE.equals(row.get("success")));

        List<Map<String, Object>> roles = jdbcTemplate.queryForList(
                """
                SELECT role_code, role_name, status, deleted
                FROM admin_role
                WHERE role_code IN ('super_admin', 'admin')
                ORDER BY role_code
                """);
        assertThat(roles).hasSize(2);
        assertThat(roles)
                .extracting(row -> row.get("role_code"))
                .containsExactly("admin", "super_admin");
        assertThat(roles).allMatch(row -> "ACTIVE".equals(row.get("status")));
        assertThat(roles).allMatch(row -> Boolean.FALSE.equals(row.get("deleted")));

        List<Map<String, Object>> activePermissions = jdbcTemplate.queryForList(
                """
                SELECT permission_code, resource_type, resource_key, status, deleted
                FROM admin_permission
                WHERE status = 'ACTIVE' AND deleted = FALSE
                ORDER BY permission_code
                """);
        Set<String> activeCodes = activePermissions.stream()
                .map(row -> (String) row.get("permission_code"))
                .collect(Collectors.toSet());
        assertThat(activeCodes).containsAll(EXPECTED_ACTIVE_PERMISSION_CODES);
        assertThat(activeCodes).doesNotContain("admin:rbac:check", "admin:menu:view");
        assertThat(activeCodes).hasSize(EXPECTED_ACTIVE_PERMISSION_COUNT);
        assertThat(activeCodes).allMatch(code -> code.equals(code.toLowerCase()) && code.startsWith("admin:"));

        Integer disabledMenuView = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM admin_permission
                WHERE permission_code = 'admin:menu:view' AND status = 'DISABLED' AND deleted = FALSE
                """,
                Integer.class);
        assertThat(disabledMenuView).isEqualTo(1);

        Map<String, Object> appsSitesMenu = jdbcTemplate.queryForMap(
                """
                SELECT permission_code, route_path, parent_id
                FROM admin_permission
                WHERE permission_code = 'admin:menu:apps.sites' AND deleted = FALSE
                """);
        assertThat(appsSitesMenu.get("route_path")).isEqualTo("/apps/sites");
        Long appsParentId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM admin_permission
                WHERE permission_code = 'admin:menu:apps' AND deleted = FALSE
                """,
                Long.class);
        assertThat(appsSitesMenu.get("parent_id")).isEqualTo(appsParentId);

        List<Map<String, Object>> adminBindings = jdbcTemplate.queryForList(
                """
                SELECT r.role_code, p.permission_code
                FROM admin_role_permission rp
                JOIN admin_role r ON r.id = rp.admin_role_id
                JOIN admin_permission p ON p.id = rp.admin_permission_id
                WHERE r.role_code = 'admin' AND rp.deleted = FALSE AND p.status = 'ACTIVE'
                ORDER BY p.permission_code
                """);
        Set<String> boundCodes = adminBindings.stream()
                .map(row -> (String) row.get("permission_code"))
                .collect(Collectors.toSet());
        assertThat(boundCodes).containsAll(EXPECTED_ACTIVE_PERMISSION_CODES);
        assertThat(boundCodes).doesNotContain("admin:rbac:check");
        assertThat(boundCodes).hasSize(EXPECTED_ACTIVE_PERMISSION_COUNT);

        long superAdminBindings = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM admin_role_permission rp
                JOIN admin_role r ON r.id = rp.admin_role_id
                WHERE r.role_code = 'super_admin' AND rp.deleted = FALSE
                """,
                Long.class);
        assertThat(superAdminBindings).isZero();
    }

    @Test
    void knowledgeBaseTableCarriesDeletedAtColumn() {
        // V29 漏建 deleted_at 时 H2 schema（按实体抄写）测不出来，只有真 PG 能抓：
        // 实体 AdminAiKnowledgeBaseEntity 继承 AbstractAuditableEntity，该列必须存在。
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'agent_ai_knowledge_base' AND column_name = 'deleted_at'
                """,
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void everySoftDeleteTableCarriesDeletedAtColumn() {
        // 类级守卫（泛化 knowledgeBaseTableCarriesDeletedAtColumn 的教训）：凡是带
        // deleted 软删标志的表，AbstractAuditableEntity 都要求 deleted_at 同时存在；
        // 再有迁移漏列，这里一次性全抓，不用等运行时查询爆 500。
        List<String> offenders = jdbcTemplate.queryForList(
                """
                SELECT c.table_name FROM information_schema.columns c
                WHERE c.table_schema = 'public' AND c.column_name = 'deleted'
                  AND NOT EXISTS (
                      SELECT 1 FROM information_schema.columns d
                      WHERE d.table_schema = 'public'
                        AND d.table_name = c.table_name
                        AND d.column_name = 'deleted_at')
                ORDER BY c.table_name
                """,
                String.class);
        assertThat(offenders).isEmpty();
    }

    @Test
    void flywaySchemaRemainsStableOnSecondContextRefresh() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        assertThat(count).isEqualTo(EXPECTED_FLYWAY_VERSION_COUNT);
        List<Map<String, Object>> migrations = jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(migrations).hasSize(EXPECTED_FLYWAY_VERSION_COUNT);
        assertThat(migrations).allMatch(row -> Boolean.TRUE.equals(row.get("success")));
    }
}
