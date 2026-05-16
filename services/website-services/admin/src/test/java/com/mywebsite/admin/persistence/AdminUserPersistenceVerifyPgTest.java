package com.mywebsite.admin.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mywebsite.admin.AdminApplication;
import com.mywebsite.admin.entity.AdminUserEntity;
import com.mywebsite.admin.mapper.AdminUserMapper;
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
 * 持久层真实 PostgreSQL 回归验证：校验 {@link AdminUserEntity} / {@link AdminUserMapper} 与 Flyway、PostgreSQL 的基础联通性（插入查询、约束、乐观锁等）。
 *
 * <p>默认 {@code mvn test} 不执行（Surefire 排除 {@code verifyPg}）；显式验证：
 * {@code mvn clean test -Ppersistence-verify-pg -pl admin,site/site-app -am}
 *
 * <p>类名使用 {@code *Test} 后缀以便由 Surefire 发现（{@code *IT} 默认划归 Failsafe）。
 */
@Tag("verifyPg")
@SpringBootTest(classes = AdminApplication.class)
@ActiveProfiles("verify-pg")
@ImportAutoConfiguration(
        exclude = {
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
class AdminUserPersistenceVerifyPgTest {

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insert_select_identity_timestamps_populated() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AdminUserEntity u = new AdminUserEntity();
        u.setUsername("ins_" + suffix);
        u.setStatus("ACTIVE");

        adminUserMapper.insert(u);
        assertThat(u.getId()).isNotNull();

        AdminUserEntity loaded = adminUserMapper.selectById(u.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getUsername()).isEqualTo("ins_" + suffix);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void duplicate_active_username_rejected() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "dup_" + suffix;

        AdminUserEntity u1 = new AdminUserEntity();
        u1.setUsername(username);
        u1.setStatus("ACTIVE");
        adminUserMapper.insert(u1);

        AdminUserEntity u2 = new AdminUserEntity();
        u2.setUsername(username);
        u2.setStatus("ACTIVE");

        assertThatThrownBy(() -> adminUserMapper.insert(u2)).satisfies(ex -> assertDuplicateKey(ex));
    }

    @Test
    void logical_delete_then_reinsert_same_username_allowed_partial_unique() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "reuse_" + suffix;

        AdminUserEntity first = new AdminUserEntity();
        first.setUsername(username);
        first.setStatus("ACTIVE");
        adminUserMapper.insert(first);
        Long id = first.getId();

        adminUserMapper.deleteById(id);
        assertThat(adminUserMapper.selectById(id)).isNull();

        Integer deletedRows =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM admin_user WHERE username = ? AND deleted = TRUE",
                        Integer.class,
                        username);
        assertThat(deletedRows).isEqualTo(1);

        AdminUserEntity second = new AdminUserEntity();
        second.setUsername(username);
        second.setStatus("ACTIVE");
        adminUserMapper.insert(second);
        assertThat(second.getId()).isNotNull().isNotEqualTo(id);

        Integer activeRows =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM admin_user WHERE username = ? AND deleted = FALSE",
                        Integer.class,
                        username);
        assertThat(activeRows).isEqualTo(1);

        Integer totalPhys =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM admin_user WHERE username = ?", Integer.class, username);
        assertThat(totalPhys).isEqualTo(2);
    }

    @Test
    void optimistic_lock_version_increment_and_stale_update_fails() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AdminUserEntity u = new AdminUserEntity();
        u.setUsername("olv_" + suffix);
        u.setStatus("ACTIVE");
        adminUserMapper.insert(u);

        AdminUserEntity fresh = adminUserMapper.selectById(u.getId());
        assertThat(fresh.getVersion()).isZero();

        fresh.setDisplayName("round1");
        adminUserMapper.updateById(fresh);

        AdminUserEntity after = adminUserMapper.selectById(u.getId());
        assertThat(after.getVersion()).isEqualTo(1);

        AdminUserEntity stale = new AdminUserEntity();
        stale.setId(u.getId());
        stale.setUsername(u.getUsername());
        stale.setStatus("ACTIVE");
        stale.setVersion(0);
        stale.setDisplayName("should-not-apply");

        assertThat(adminUserMapper.updateById(stale)).isZero();

        AdminUserEntity unchanged = adminUserMapper.selectById(u.getId());
        assertThat(unchanged.getDisplayName()).isEqualTo("round1");
        assertThat(unchanged.getVersion()).isEqualTo(1);
    }

    private static void assertDuplicateKey(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String m = cur.getMessage();
            if (m != null
                    && (m.contains("duplicate key")
                            || m.contains("uq_admin_user_username_active")
                            || m.contains("violates unique constraint"))) {
                return;
            }
            cur = cur.getCause();
        }
        throw new AssertionError("期望唯一约束冲突", ex);
    }
}
