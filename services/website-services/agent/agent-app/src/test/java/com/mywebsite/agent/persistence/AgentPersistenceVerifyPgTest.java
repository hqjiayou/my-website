package com.mywebsite.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mywebsite.agent.AgentApplication;
import com.mywebsite.agent.entity.AgentEntity;
import com.mywebsite.agent.entity.AgentUserEntity;
import com.mywebsite.agent.mapper.AgentMapper;
import com.mywebsite.agent.mapper.AgentUserMapper;
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
 * 持久层真实 PostgreSQL 回归验证：校验 {@link AgentEntity}/{@link AgentMapper}、{@link AgentUserEntity}/{@link AgentUserMapper} 与外键及乐观锁等与 Flyway、库的联通性。
 *
 * <p>执行方式见 {@link com.mywebsite.admin.AdminUserPersistenceVerifyPgTest}。
 */
@Tag("verifyPg")
@SpringBootTest(classes = AgentApplication.class)
@ActiveProfiles("verify-pg")
@ImportAutoConfiguration(
        exclude = {
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
        })
class AgentPersistenceVerifyPgTest {

    @Autowired
    private AgentMapper agentMapper;

    @Autowired
    private AgentUserMapper agentUserMapper;

    @Test
    void site_insert_select_updated_at_trigger_moves_forward() throws InterruptedException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AgentEntity agent = new AgentEntity();
        agent.setAgentCode("AGENT_" + suffix);
        agent.setAgentName("Verify Agent");
        agent.setAgentType("CAR");
        agent.setStatus("ACTIVE");

        agentMapper.insert(agent);
        assertThat(agent.getId()).isNotNull();

        AgentEntity loaded = agentMapper.selectById(agent.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        OffsetDateTime beforeUpdate = loaded.getUpdatedAt();

        Thread.sleep(50);

        loaded.setDescription("touch");
        agentMapper.updateById(loaded);

        AgentEntity after = agentMapper.selectById(agent.getId());
        assertThat(after.getUpdatedAt()).isAfter(beforeUpdate);
    }

    @Test
    void agent_user_foreign_key_and_query_by_agent_id() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AgentEntity agent = new AgentEntity();
        agent.setAgentCode("FK_" + suffix);
        agent.setAgentName("FK Agent");
        agent.setAgentType("CAR");
        agent.setStatus("ACTIVE");
        agentMapper.insert(agent);

        AgentUserEntity user = new AgentUserEntity();
        user.setAgentId(agent.getId());
        user.setUsername("user_" + suffix);
        user.setStatus("ACTIVE");
        agentUserMapper.insert(user);

        List<AgentUserEntity> rows =
                agentUserMapper.selectList(
                        Wrappers.<AgentUserEntity>lambdaQuery().eq(AgentUserEntity::getAgentId, agent.getId()));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getUsername()).isEqualTo("user_" + suffix);
    }

    @Test
    void agent_user_invalid_agent_id_rejected() {
        AgentUserEntity bad = new AgentUserEntity();
        bad.setAgentId(9_999_999_999L);
        bad.setUsername("orphan");
        bad.setStatus("ACTIVE");

        assertThatThrownBy(() -> agentUserMapper.insert(bad)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void site_optimistic_lock_stale_version_no_rows_updated() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AgentEntity agent = new AgentEntity();
        agent.setAgentCode("OL_" + suffix);
        agent.setAgentName("OL Agent");
        agent.setAgentType("CAR");
        agent.setStatus("ACTIVE");
        agentMapper.insert(agent);

        AgentEntity fresh = agentMapper.selectById(agent.getId());
        fresh.setDescription("v1");
        agentMapper.updateById(fresh);

        AgentEntity stale = new AgentEntity();
        stale.setId(agent.getId());
        stale.setAgentCode(agent.getAgentCode());
        stale.setAgentName(agent.getAgentName());
        stale.setAgentType(agent.getAgentType());
        stale.setStatus(agent.getStatus());
        stale.setVersion(0);
        stale.setDescription("lost-update");

        assertThat(agentMapper.updateById(stale)).isZero();
        AgentEntity unchanged = agentMapper.selectById(agent.getId());
        assertThat(unchanged.getDescription()).isEqualTo("v1");
        assertThat(unchanged.getVersion()).isEqualTo(1);
    }
}
