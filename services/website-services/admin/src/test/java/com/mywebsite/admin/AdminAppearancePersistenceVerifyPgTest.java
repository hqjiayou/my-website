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
 * Appearance 持久层真实 PostgreSQL 回归：site_appearance JSONB、软删同步。
 * （原 template_id FK 用例随行业模板下架移除，V30 已软删种子模板。）
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
    void appearanceJsonb_roundTrip_andSoftDelete() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AdminSiteEntity site = new AdminSiteEntity();
        site.setSiteCode("pg_ap_" + suffix);
        site.setSiteName("PG Appearance");
        site.setSiteType("PERSONAL");
        site.setStatus("ACTIVE");
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

}
