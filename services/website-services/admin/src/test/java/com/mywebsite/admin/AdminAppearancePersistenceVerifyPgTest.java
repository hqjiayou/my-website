package com.mywebsite.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.admin.entity.AdminAppearanceEntity;
import com.mywebsite.admin.entity.AdminSiteEntity;
import com.mywebsite.admin.mapper.AdminAppearanceMapper;
import com.mywebsite.admin.mapper.AdminSiteMapper;
import java.util.Map;
import java.util.UUID;
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
 * Appearance 持久层真实 PostgreSQL 回归：template_id FK、site_appearance JSONB、软删同步。
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
class AdminAppearancePersistenceVerifyPgTest {

    @Autowired
    private AdminSiteMapper adminSiteMapper;

    @Autowired
    private AdminAppearanceMapper adminAppearanceMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void siteTemplateFkAndAppearanceJsonb_roundTrip_andSoftDelete() {
        Long templateId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM site_template
                WHERE code = 'japanese-car-repair-v1' AND deleted = FALSE
                """,
                Long.class);
        assertThat(templateId).isNotNull();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AdminSiteEntity site = new AdminSiteEntity();
        site.setSiteCode("pg_ap_" + suffix);
        site.setSiteName("PG Appearance");
        site.setSiteType("CAR_REPAIR");
        site.setStatus("ACTIVE");
        site.setTemplateId(templateId);
        adminSiteMapper.insert(site);
        assertThat(site.getId()).isNotNull();

        AdminAppearanceEntity appearance = new AdminAppearanceEntity();
        appearance.setSiteId(site.getId());
        appearance.setSchemaVersion(1);
        appearance.setConfigJson(
                "{\"primaryColor\":\"#B91C1C\",\"fontStyle\":\"modern\","
                        + "\"buttonStyle\":\"rounded\",\"heroLayout\":\"full-bleed\"}");
        adminAppearanceMapper.insert(appearance);
        assertThat(appearance.getId()).isNotNull();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT schema_version, config::text AS config_text, deleted
                FROM site_appearance WHERE id = ?
                """,
                appearance.getId());
        assertThat(row.get("schema_version")).isEqualTo(1);
        assertThat((String) row.get("config_text")).contains("#B91C1C");
        assertThat(row.get("deleted")).isEqualTo(false);

        AdminSiteEntity loaded = adminSiteMapper.selectById(site.getId());
        assertThat(loaded.getTemplateId()).isEqualTo(templateId);

        adminAppearanceMapper.deleteById(appearance.getId());
        adminSiteMapper.deleteById(site.getId());

        Integer deletedAppearance = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM site_appearance WHERE id = ? AND deleted = TRUE",
                Integer.class,
                appearance.getId());
        assertThat(deletedAppearance).isEqualTo(1);

        Integer deletedSite = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM site WHERE id = ? AND deleted = TRUE", Integer.class, site.getId());
        assertThat(deletedSite).isEqualTo(1);
    }

    @Test
    void templateFkRestrictsHardDeleteOfReferencedTemplate() {
        Long templateId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM site_template
                WHERE code = 'cake-shop-v1' AND deleted = FALSE
                """,
                Long.class);

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AdminSiteEntity site = new AdminSiteEntity();
        site.setSiteCode("pg_fk_" + suffix);
        site.setSiteName("FK Site");
        site.setSiteType("CAKE");
        site.setStatus("ACTIVE");
        site.setTemplateId(templateId);
        adminSiteMapper.insert(site);

        // ON DELETE RESTRICT：物理删除被引用模板应失败
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbcTemplate.update(
                        "DELETE FROM site_template WHERE id = ?", templateId))
                .hasMessageContaining("violates foreign key constraint");
    }
}
