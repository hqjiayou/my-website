package com.mywebsite.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.admin.entity.AdminContentEntity;
import com.mywebsite.admin.entity.AdminMediaEntity;
import com.mywebsite.admin.entity.AdminSiteEntity;
import com.mywebsite.admin.mapper.AdminContentMapper;
import com.mywebsite.admin.mapper.AdminMediaMapper;
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
 * Content 持久层真实 PostgreSQL：site_content 表、FK、软删。
 *
 * <p>{@code mvn test -Ppersistence-verify-pg -pl admin -am -Dtest=AdminContentPersistenceVerifyPgTest}
 */
@Tag("verifyPg")
@SpringBootTest(classes = AdminApplication.class)
@ActiveProfiles("verify-pg")
@ImportAutoConfiguration(
        exclude = {
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
class AdminContentPersistenceVerifyPgTest {

    @Autowired
    private AdminSiteMapper adminSiteMapper;

    @Autowired
    private AdminMediaMapper adminMediaMapper;

    @Autowired
    private AdminContentMapper adminContentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void siteContentRoundTripSoftDeleteAndCoverFk() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AdminSiteEntity site = new AdminSiteEntity();
        site.setSiteCode("pg_ct_" + suffix);
        site.setSiteName("PG Content");
        site.setSiteType("CAKE");
        site.setStatus("ACTIVE");
        adminSiteMapper.insert(site);
        assertThat(site.getId()).isNotNull();

        AdminMediaEntity media = new AdminMediaEntity();
        media.setSiteId(site.getId());
        media.setBucket("uploads");
        media.setObjectKey("sites/" + site.getId() + "/2026/08/" + UUID.randomUUID() + ".jpg");
        media.setOriginalFilename("cover.jpg");
        media.setContentType("image/jpeg");
        media.setExtension("jpg");
        media.setFileSize(4L);
        media.setSha256("abcd");
        media.setVisibility("PRIVATE");
        media.setStatus("READY");
        adminMediaMapper.insert(media);

        AdminContentEntity content = new AdminContentEntity();
        content.setSiteId(site.getId());
        content.setType("hero");
        content.setTitle("Hero");
        content.setBody("body");
        content.setStatus("DRAFT");
        content.setSortOrder(1);
        content.setCoverMediaId(media.getId());
        adminContentMapper.insert(content);
        assertThat(content.getId()).isNotNull();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT site_id, type, status, cover_media_id, deleted
                FROM site_content WHERE id = ?
                """,
                content.getId());
        assertThat(row.get("site_id")).isEqualTo(site.getId());
        assertThat(row.get("type")).isEqualTo("hero");
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat(row.get("cover_media_id")).isEqualTo(media.getId());
        assertThat(row.get("deleted")).isEqualTo(false);

        adminContentMapper.deleteById(content.getId());
        Integer deletedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM site_content WHERE id = ? AND deleted = TRUE
                """,
                Integer.class,
                content.getId());
        assertThat(deletedCount).isEqualTo(1);
        assertThat(adminContentMapper.selectById(content.getId())).isNull();
    }
}
