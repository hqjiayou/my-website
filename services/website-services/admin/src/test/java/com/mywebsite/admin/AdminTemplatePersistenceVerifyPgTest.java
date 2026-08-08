package com.mywebsite.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mywebsite.admin.entity.AdminTemplateEntity;
import com.mywebsite.admin.mapper.AdminTemplateMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 * Template 持久层真实 PostgreSQL 回归：{@code site_template} 种子、唯一约束、软删与乐观锁。
 *
 * <p>{@code mvn test -Ppersistence-verify-pg -pl admin -am}
 */
@Tag("verifyPg")
@SpringBootTest(classes = AdminApplication.class)
@ActiveProfiles("verify-pg")
@ImportAutoConfiguration(
        exclude = {
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
class AdminTemplatePersistenceVerifyPgTest {

    private static final Set<String> EXPECTED_SEED_CODES = Set.of(
            "japanese-car-repair-v1",
            "used-car-shop-v1",
            "cake-shop-v1",
            "salon-v1",
            "clinic-v1",
            "gyoseishoshi-v1");

    private static final Set<String> ALLOWED_SITE_TYPES = Set.of(
            "CAR_REPAIR", "USED_CAR", "CAKE", "SALON", "CLINIC", "GYOSEISHOSHI");

    @Autowired
    private AdminTemplateMapper adminTemplateMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywaySeedHasSixTemplatesWithValidSiteTypes() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT code, site_type, status, deleted
                FROM site_template
                WHERE deleted = FALSE
                ORDER BY sort_order, id
                """);
        assertThat(rows).hasSizeGreaterThanOrEqualTo(6);
        Set<String> codes = rows.stream().map(r -> (String) r.get("code")).collect(Collectors.toSet());
        assertThat(codes).containsAll(EXPECTED_SEED_CODES);
        assertThat(rows).allMatch(r -> ALLOWED_SITE_TYPES.contains((String) r.get("site_type")));
        assertThat(rows).allMatch(r -> Boolean.FALSE.equals(r.get("deleted")));
    }

    @Test
    void insert_select_soft_delete_and_unique_reuse() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String code = "pg_tpl_" + suffix;

        AdminTemplateEntity template = new AdminTemplateEntity();
        template.setCode(code);
        template.setName("PG Template");
        template.setSiteType("CAR_REPAIR");
        template.setStatus("DRAFT");
        template.setSortOrder(100);
        adminTemplateMapper.insert(template);
        assertThat(template.getId()).isNotNull();

        AdminTemplateEntity loaded = adminTemplateMapper.selectById(template.getId());
        assertThat(loaded.getCode()).isEqualTo(code);
        assertThat(loaded.getVersion()).isZero();

        AdminTemplateEntity dup = new AdminTemplateEntity();
        dup.setCode(code);
        dup.setName("Dup");
        dup.setSiteType("CAKE");
        dup.setStatus("ENABLED");
        dup.setSortOrder(101);
        assertThatThrownBy(() -> adminTemplateMapper.insert(dup)).satisfies(this::assertDuplicateKey);

        adminTemplateMapper.deleteById(template.getId());
        assertThat(adminTemplateMapper.selectById(template.getId())).isNull();

        Integer deletedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM site_template WHERE code = ? AND deleted = TRUE", Integer.class, code);
        assertThat(deletedRows).isEqualTo(1);

        AdminTemplateEntity reused = new AdminTemplateEntity();
        reused.setCode(code);
        reused.setName("Reused");
        reused.setSiteType("SALON");
        reused.setStatus("ENABLED");
        reused.setSortOrder(102);
        adminTemplateMapper.insert(reused);
        assertThat(reused.getId()).isNotNull().isNotEqualTo(template.getId());
    }

    @Test
    void optimistic_lock_stale_update_returns_zero() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AdminTemplateEntity template = new AdminTemplateEntity();
        template.setCode("olv_tpl_" + suffix);
        template.setName("OLV");
        template.setSiteType("CLINIC");
        template.setStatus("DRAFT");
        template.setSortOrder(200);
        adminTemplateMapper.insert(template);

        AdminTemplateEntity fresh = adminTemplateMapper.selectById(template.getId());
        fresh.setStatus("ENABLED");
        adminTemplateMapper.updateById(fresh);
        assertThat(adminTemplateMapper.selectById(template.getId()).getVersion()).isEqualTo(1);

        AdminTemplateEntity stale = new AdminTemplateEntity();
        stale.setId(template.getId());
        stale.setCode(template.getCode());
        stale.setName(template.getName());
        stale.setSiteType("CLINIC");
        stale.setStatus("DISABLED");
        stale.setSortOrder(200);
        stale.setVersion(0);
        assertThat(adminTemplateMapper.updateById(stale)).isZero();
        assertThat(adminTemplateMapper.selectById(template.getId()).getStatus()).isEqualTo("ENABLED");
    }

    private void assertDuplicateKey(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String m = cur.getMessage();
            if (m != null
                    && (m.contains("duplicate key")
                            || m.contains("uq_site_template_code_active")
                            || m.contains("violates unique constraint"))) {
                return;
            }
            cur = cur.getCause();
        }
        throw new AssertionError("期望唯一约束冲突", ex);
    }
}
