package com.mywebsite.site.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mywebsite.site.SiteApplication;
import com.mywebsite.site.entity.SiteEntity;
import com.mywebsite.site.entity.SiteUserEntity;
import com.mywebsite.site.mapper.SiteMapper;
import com.mywebsite.site.mapper.SiteUserMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * 持久层真实 PostgreSQL 回归验证：校验 {@link SiteEntity}/{@link SiteMapper}、{@link SiteUserEntity}/{@link SiteUserMapper} 与外键及乐观锁等与 Flyway、库的联通性。
 *
 * <p>执行方式见 {@link com.mywebsite.admin.persistence.AdminUserPersistenceVerifyPgTest}。
 */
@Tag("verifyPg")
@SpringBootTest(classes = SiteApplication.class)
@ActiveProfiles("verify-pg")
@ImportAutoConfiguration(
        exclude = {
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
class SitePersistenceVerifyPgTest {

    @Autowired
    private SiteMapper siteMapper;

    @Autowired
    private SiteUserMapper siteUserMapper;

    @Test
    void site_insert_select_updated_at_trigger_moves_forward() throws InterruptedException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        SiteEntity site = new SiteEntity();
        site.setSiteCode("SITE_" + suffix);
        site.setSiteName("Verify Site");
        site.setSiteType("CAR");
        site.setStatus("ACTIVE");

        siteMapper.insert(site);
        assertThat(site.getId()).isNotNull();

        SiteEntity loaded = siteMapper.selectById(site.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        OffsetDateTime beforeUpdate = loaded.getUpdatedAt();

        Thread.sleep(50);

        loaded.setDescription("touch");
        siteMapper.updateById(loaded);

        SiteEntity after = siteMapper.selectById(site.getId());
        assertThat(after.getUpdatedAt()).isAfter(beforeUpdate);
    }

    @Test
    void site_user_foreign_key_and_query_by_site_id() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        SiteEntity site = new SiteEntity();
        site.setSiteCode("FK_" + suffix);
        site.setSiteName("FK Site");
        site.setSiteType("CAR");
        site.setStatus("ACTIVE");
        siteMapper.insert(site);

        SiteUserEntity user = new SiteUserEntity();
        user.setSiteId(site.getId());
        user.setUsername("user_" + suffix);
        user.setStatus("ACTIVE");
        siteUserMapper.insert(user);

        List<SiteUserEntity> rows =
                siteUserMapper.selectList(
                        Wrappers.<SiteUserEntity>lambdaQuery().eq(SiteUserEntity::getSiteId, site.getId()));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getUsername()).isEqualTo("user_" + suffix);
    }

    @Test
    void site_user_invalid_site_id_rejected() {
        SiteUserEntity bad = new SiteUserEntity();
        bad.setSiteId(9_999_999_999L);
        bad.setUsername("orphan");
        bad.setStatus("ACTIVE");

        assertThatThrownBy(() -> siteUserMapper.insert(bad)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void site_optimistic_lock_stale_version_no_rows_updated() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        SiteEntity site = new SiteEntity();
        site.setSiteCode("OL_" + suffix);
        site.setSiteName("OL Site");
        site.setSiteType("CAR");
        site.setStatus("ACTIVE");
        siteMapper.insert(site);

        SiteEntity fresh = siteMapper.selectById(site.getId());
        fresh.setDescription("v1");
        siteMapper.updateById(fresh);

        SiteEntity stale = new SiteEntity();
        stale.setId(site.getId());
        stale.setSiteCode(site.getSiteCode());
        stale.setSiteName(site.getSiteName());
        stale.setSiteType(site.getSiteType());
        stale.setStatus(site.getStatus());
        stale.setVersion(0);
        stale.setDescription("lost-update");

        assertThat(siteMapper.updateById(stale)).isZero();
        SiteEntity unchanged = siteMapper.selectById(site.getId());
        assertThat(unchanged.getDescription()).isEqualTo("v1");
        assertThat(unchanged.getVersion()).isEqualTo(1);
    }
}
