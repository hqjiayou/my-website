package com.mywebsite.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mywebsite.admin.entity.AdminSiteEntity;
import com.mywebsite.admin.mapper.AdminSiteMapper;
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
 * Site Core 持久层真实 PostgreSQL 回归：AdminSiteEntity / AdminSiteMapper 双映射 {@code site} 表。
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
class AdminSitePersistenceVerifyPgTest {

    @Autowired
    private AdminSiteMapper adminSiteMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insert_select_soft_delete_and_unique_reuse() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String siteCode = "pg_" + suffix;

        AdminSiteEntity site = new AdminSiteEntity();
        site.setSiteCode(siteCode);
        site.setSiteName("PG Site");
        site.setSiteType("CAR_REPAIR");
        site.setStatus("ACTIVE");
        adminSiteMapper.insert(site);
        assertThat(site.getId()).isNotNull();

        AdminSiteEntity loaded = adminSiteMapper.selectById(site.getId());
        assertThat(loaded.getSiteCode()).isEqualTo(siteCode);
        assertThat(loaded.getVersion()).isZero();

        AdminSiteEntity dup = new AdminSiteEntity();
        dup.setSiteCode(siteCode);
        dup.setSiteName("Dup");
        dup.setSiteType("CAKE");
        dup.setStatus("ACTIVE");
        assertThatThrownBy(() -> adminSiteMapper.insert(dup)).satisfies(this::assertDuplicateKey);

        adminSiteMapper.deleteById(site.getId());
        assertThat(adminSiteMapper.selectById(site.getId())).isNull();

        Integer deletedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM site WHERE site_code = ? AND deleted = TRUE", Integer.class, siteCode);
        assertThat(deletedRows).isEqualTo(1);

        AdminSiteEntity reused = new AdminSiteEntity();
        reused.setSiteCode(siteCode);
        reused.setSiteName("Reused");
        reused.setSiteType("SALON");
        reused.setStatus("ACTIVE");
        adminSiteMapper.insert(reused);
        assertThat(reused.getId()).isNotNull().isNotEqualTo(site.getId());
    }

    @Test
    void optimistic_lock_stale_update_returns_zero() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AdminSiteEntity site = new AdminSiteEntity();
        site.setSiteCode("olv_" + suffix);
        site.setSiteName("OLV");
        site.setSiteType("CLINIC");
        site.setStatus("ACTIVE");
        adminSiteMapper.insert(site);

        AdminSiteEntity fresh = adminSiteMapper.selectById(site.getId());
        fresh.setSiteName("round1");
        adminSiteMapper.updateById(fresh);
        assertThat(adminSiteMapper.selectById(site.getId()).getVersion()).isEqualTo(1);

        AdminSiteEntity stale = new AdminSiteEntity();
        stale.setId(site.getId());
        stale.setSiteCode(site.getSiteCode());
        stale.setSiteName("should-not-apply");
        stale.setSiteType("CLINIC");
        stale.setStatus("ACTIVE");
        stale.setVersion(0);
        assertThat(adminSiteMapper.updateById(stale)).isZero();
        assertThat(adminSiteMapper.selectById(site.getId()).getSiteName()).isEqualTo("round1");
    }

    private void assertDuplicateKey(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String m = cur.getMessage();
            if (m != null
                    && (m.contains("duplicate key")
                            || m.contains("uq_site_site_code_active")
                            || m.contains("violates unique constraint"))) {
                return;
            }
            cur = cur.getCause();
        }
        throw new AssertionError("期望唯一约束冲突", ex);
    }
}
