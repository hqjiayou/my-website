package com.mywebsite.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.admin.entity.AdminMediaEntity;
import com.mywebsite.admin.entity.AdminSiteEntity;
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
 * Media 持久层真实 PostgreSQL：media_file 表、索引、软删。
 *
 * <p>{@code mvn test -Ppersistence-verify-pg -pl admin -am -Dtest=AdminMediaPersistenceVerifyPgTest}
 */
@Tag("verifyPg")
@SpringBootTest(classes = AdminApplication.class)
@ActiveProfiles("verify-pg")
@ImportAutoConfiguration(
        exclude = {
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
class AdminMediaPersistenceVerifyPgTest {

    @Autowired
    private AdminSiteMapper adminSiteMapper;

    @Autowired
    private AdminMediaMapper adminMediaMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mediaFileRoundTripAndSoftDelete() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AdminSiteEntity site = new AdminSiteEntity();
        site.setSiteCode("pg_md_" + suffix);
        site.setSiteName("PG Media");
        site.setSiteType("CAKE");
        site.setStatus("ACTIVE");
        adminSiteMapper.insert(site);
        assertThat(site.getId()).isNotNull();

        String objectKey = "sites/" + site.getId() + "/2026/08/" + UUID.randomUUID() + ".jpg";
        AdminMediaEntity media = new AdminMediaEntity();
        media.setSiteId(site.getId());
        media.setBucket("uploads");
        media.setObjectKey(objectKey);
        media.setOriginalFilename("photo.jpg");
        media.setContentType("image/jpeg");
        media.setExtension("jpg");
        media.setFileSize(4L);
        media.setSha256("abcd");
        media.setVisibility("PRIVATE");
        media.setStatus("READY");
        adminMediaMapper.insert(media);
        assertThat(media.getId()).isNotNull();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT site_id, object_key, content_type, status, deleted
                FROM media_file WHERE id = ?
                """,
                media.getId());
        assertThat(row.get("site_id")).isEqualTo(site.getId());
        assertThat(row.get("object_key")).isEqualTo(objectKey);
        assertThat(row.get("content_type")).isEqualTo("image/jpeg");
        assertThat(row.get("status")).isEqualTo("READY");
        assertThat(row.get("deleted")).isEqualTo(false);

        adminMediaMapper.deleteById(media.getId());
        Integer deletedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM media_file WHERE id = ? AND deleted = TRUE
                """,
                Integer.class,
                media.getId());
        assertThat(deletedCount).isEqualTo(1);

        assertThat(adminMediaMapper.selectById(media.getId())).isNull();
    }
}
