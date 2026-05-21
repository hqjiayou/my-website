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
 * 5C.1：RBAC 种子 Flyway（V1 + V2）在真实 PostgreSQL 上的迁移与数据验证。
 *
 * <p>默认库：{@code my_website_rbac_seed_verify}；执行：
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

    private static final Set<String> EXPECTED_PERMISSION_CODES = Set.of(
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
            "admin:user:role:view",
            "admin:user:role:update",
            "admin:menu:view",
            "admin:site:list",
            "admin:site:detail",
            "admin:dashboard:view");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayV1AndV2AppliedWithExpectedRbacSeed() {
        List<Map<String, Object>> migrations = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(migrations)
                .extracting(row -> row.get("version"))
                .contains("1", "2");
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

        List<Map<String, Object>> permissions = jdbcTemplate.queryForList(
                """
                SELECT permission_code, resource_type, resource_key, status, deleted
                FROM admin_permission
                ORDER BY permission_code
                """);
        Set<String> codes =
                permissions.stream().map(row -> (String) row.get("permission_code")).collect(Collectors.toSet());
        assertThat(codes).containsAll(EXPECTED_PERMISSION_CODES);
        assertThat(codes).doesNotContain("admin:rbac:check");
        assertThat(codes).hasSize(16);
        assertThat(codes).allMatch(code -> code.equals(code.toLowerCase()) && code.startsWith("admin:"));
        assertThat(permissions).allMatch(row -> "ACTIVE".equals(row.get("status")));
        assertThat(permissions).allMatch(row -> Boolean.FALSE.equals(row.get("deleted")));

        List<Map<String, Object>> adminBindings = jdbcTemplate.queryForList(
                """
                SELECT r.role_code, p.permission_code
                FROM admin_role_permission rp
                JOIN admin_role r ON r.id = rp.admin_role_id
                JOIN admin_permission p ON p.id = rp.admin_permission_id
                WHERE r.role_code = 'admin' AND rp.deleted = FALSE
                ORDER BY p.permission_code
                """);
        Set<String> boundCodes = adminBindings.stream()
                .map(row -> (String) row.get("permission_code"))
                .collect(Collectors.toSet());
        assertThat(boundCodes).containsAll(EXPECTED_PERMISSION_CODES);
        assertThat(boundCodes).doesNotContain("admin:rbac:check");
        assertThat(boundCodes).hasSize(16);

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
    void flywaySchemaRemainsStableOnSecondContextRefresh() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        assertThat(count).isEqualTo(2);
        List<Map<String, Object>> migrations = jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank");
        assertThat(migrations).hasSize(2);
        assertThat(migrations).allMatch(row -> Boolean.TRUE.equals(row.get("success")));
    }
}
