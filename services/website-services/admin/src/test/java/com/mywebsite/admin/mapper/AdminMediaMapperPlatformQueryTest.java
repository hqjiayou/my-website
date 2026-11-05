package com.mywebsite.admin.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.mapper.AdminMediaMapper.AdminMediaListRow;
import com.mywebsite.admin.testsupport.AbstractAdminSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

/**
 * 媒体平台化（#212）列表 SQL 集成验证：library/platform 两视图的成员、referenced 标记、
 * 内容类型过滤与分页 total 都由单条 SQL 给出（H2 夹具，无 HTTP/JWT 参与）。
 */
@Sql(
        scripts = {"/rbac/rbac-h2-schema.sql", "/rbac/rbac-h2-data.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AdminMediaMapperPlatformQueryTest extends AbstractAdminSpringBootTest {

    private static final long AGENT_A = 101L;
    private static final long AGENT_B = 102L;

    @Autowired
    private AdminMediaMapper adminMediaMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long insertMedia(long siteId, String scope, String contentType) {
        jdbcTemplate.update(
                """
                INSERT INTO media_file (site_id, bucket, object_key, original_filename, content_type,
                                        extension, file_size, sha256, visibility, scope, status)
                VALUES (?, 'test', ?, 'f.jpg', ?, 'jpg', 4, NULL, 'PRIVATE', ?, 'READY')
                """,
                siteId,
                "sites/" + siteId + "/" + System.nanoTime() + ".jpg",
                contentType,
                scope);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM media_file", Long.class);
    }

    private void insertReference(long mediaId, long agentId) {
        jdbcTemplate.update("INSERT INTO media_reference (media_id, agent_id) VALUES (?, ?)", mediaId, agentId);
    }

    @Test
    void libraryContainsOwnAndReferencedOnly() {
        long own = insertMedia(AGENT_B, "AGENT", "image/jpeg");
        long referenced = insertMedia(AGENT_A, "PLATFORM", "image/png");
        insertMedia(AGENT_A, "AGENT", "image/jpeg"); // 他站私有、未引用：不可见
        insertReference(referenced, AGENT_B);

        Page<AdminMediaListRow> page =
                adminMediaMapper.selectLibraryPage(new Page<>(1, 20), AGENT_B, null);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).extracting(AdminMediaListRow::id).containsExactly(referenced, own);
        assertThat(page.getRecords()).extracting(AdminMediaListRow::referenced).containsExactly(true, false);
        assertThat(page.getRecords().get(0).agentId()).isEqualTo(AGENT_A);
        assertThat(page.getRecords().get(0).scope()).isEqualTo("PLATFORM");
    }

    @Test
    void libraryContentTypePrefixFilters() {
        insertMedia(AGENT_B, "AGENT", "image/jpeg");
        insertMedia(AGENT_B, "AGENT", "application/pdf");

        Page<AdminMediaListRow> images =
                adminMediaMapper.selectLibraryPage(new Page<>(1, 20), AGENT_B, "image/");
        Page<AdminMediaListRow> all =
                adminMediaMapper.selectLibraryPage(new Page<>(1, 20), AGENT_B, null);

        assertThat(images.getTotal()).isEqualTo(1);
        assertThat(all.getTotal()).isEqualTo(2);
    }

    @Test
    void libraryPaginationTotalCountsAllVisibleRows() {
        insertMedia(AGENT_B, "AGENT", "image/jpeg");
        insertMedia(AGENT_B, "AGENT", "image/jpeg");
        insertMedia(AGENT_B, "AGENT", "image/jpeg");

        Page<AdminMediaListRow> first =
                adminMediaMapper.selectLibraryPage(new Page<>(1, 2), AGENT_B, null);
        Page<AdminMediaListRow> second =
                adminMediaMapper.selectLibraryPage(new Page<>(2, 2), AGENT_B, null);

        assertThat(first.getTotal()).isEqualTo(3);
        assertThat(first.getRecords()).hasSize(2);
        assertThat(second.getRecords()).hasSize(1);
    }

    @Test
    void platformContainsAllPlatformFilesWithReferencedFlags() {
        long platformOfA = insertMedia(AGENT_A, "PLATFORM", "image/jpeg");
        long platformOfB = insertMedia(AGENT_B, "PLATFORM", "image/jpeg");
        insertMedia(AGENT_A, "AGENT", "image/jpeg"); // AGENT 文件不进平台库
        insertReference(platformOfA, AGENT_B);

        Page<AdminMediaListRow> page =
                adminMediaMapper.selectPlatformPage(new Page<>(1, 20), AGENT_B, null);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).extracting(AdminMediaListRow::id)
                .containsExactly(platformOfB, platformOfA);
        assertThat(page.getRecords()).extracting(AdminMediaListRow::referenced)
                .containsExactly(false, true);
    }

    @Test
    void softDeletedFilesExcludedFromBothViews() {
        long deleted = insertMedia(AGENT_B, "PLATFORM", "image/jpeg");
        jdbcTemplate.update("UPDATE media_file SET deleted = TRUE WHERE id = ?", deleted);

        assertThat(adminMediaMapper.selectLibraryPage(new Page<>(1, 20), AGENT_B, null).getTotal())
                .isZero();
        assertThat(adminMediaMapper.selectPlatformPage(new Page<>(1, 20), AGENT_B, null).getTotal())
                .isZero();
    }
}
