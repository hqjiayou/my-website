package com.mywebsite.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.mapper.AdminAgentMapper;
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
 * Agent Core 持久层真实 PostgreSQL 回归：AdminAgentEntity / AdminAgentMapper 双映射 {@code agent} 表。
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
class AdminAgentPersistenceVerifyPgTest {

    @Autowired
    private AdminAgentMapper adminAgentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insert_select_soft_delete_and_unique_reuse() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String agentCode = "pg_" + suffix;

        AdminAgentEntity agent = new AdminAgentEntity();
        agent.setAgentCode(agentCode);
        agent.setAgentName("PG Agent");
        agent.setAgentType("CAR_REPAIR");
        agent.setStatus("ACTIVE");
        adminAgentMapper.insert(agent);
        assertThat(agent.getId()).isNotNull();

        AdminAgentEntity loaded = adminAgentMapper.selectById(agent.getId());
        assertThat(loaded.getAgentCode()).isEqualTo(agentCode);
        assertThat(loaded.getVersion()).isZero();

        AdminAgentEntity dup = new AdminAgentEntity();
        dup.setAgentCode(agentCode);
        dup.setAgentName("Dup");
        dup.setAgentType("CAKE");
        dup.setStatus("ACTIVE");
        assertThatThrownBy(() -> adminAgentMapper.insert(dup)).satisfies(this::assertDuplicateKey);

        adminAgentMapper.deleteById(agent.getId());
        assertThat(adminAgentMapper.selectById(agent.getId())).isNull();

        Integer deletedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent WHERE agent_code = ? AND deleted = TRUE", Integer.class, agentCode);
        assertThat(deletedRows).isEqualTo(1);

        AdminAgentEntity reused = new AdminAgentEntity();
        reused.setAgentCode(agentCode);
        reused.setAgentName("Reused");
        reused.setAgentType("SALON");
        reused.setStatus("ACTIVE");
        adminAgentMapper.insert(reused);
        assertThat(reused.getId()).isNotNull().isNotEqualTo(agent.getId());
    }

    @Test
    void optimistic_lock_stale_update_returns_zero() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AdminAgentEntity agent = new AdminAgentEntity();
        agent.setAgentCode("olv_" + suffix);
        agent.setAgentName("OLV");
        agent.setAgentType("CLINIC");
        agent.setStatus("ACTIVE");
        adminAgentMapper.insert(agent);

        AdminAgentEntity fresh = adminAgentMapper.selectById(agent.getId());
        fresh.setAgentName("round1");
        adminAgentMapper.updateById(fresh);
        assertThat(adminAgentMapper.selectById(agent.getId()).getVersion()).isEqualTo(1);

        AdminAgentEntity stale = new AdminAgentEntity();
        stale.setId(agent.getId());
        stale.setAgentCode(agent.getAgentCode());
        stale.setAgentName("should-not-apply");
        stale.setAgentType("CLINIC");
        stale.setStatus("ACTIVE");
        stale.setVersion(0);
        assertThat(adminAgentMapper.updateById(stale)).isZero();
        assertThat(adminAgentMapper.selectById(agent.getId()).getAgentName()).isEqualTo("round1");
    }

    private void assertDuplicateKey(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String m = cur.getMessage();
            if (m != null
                    && (m.contains("duplicate key")
                            || m.contains("uq_agent_agent_code_active")
                            || m.contains("violates unique constraint"))) {
                return;
            }
            cur = cur.getCause();
        }
        throw new AssertionError("期望唯一约束冲突", ex);
    }
}
