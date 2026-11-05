package com.mywebsite.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.agent.entity.AgentAiKnowledgeBaseEntity;
import com.mywebsite.agent.entity.AgentAiKnowledgeBaseSubscriptionEntity;
import com.mywebsite.agent.mapper.AgentAiKnowledgeBaseMapper;
import com.mywebsite.agent.mapper.AgentAiKnowledgeBaseSubscriptionMapper;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * agent 可达知识空间集的统一展开（Issue #209 UoW-2 落地于对话路径、UoW-4 抽为
 * 两处共用）：该 agent 创建的空间 ∪ 订阅的空间（订阅表含创建者行，按 base id
 * 去重），顺序为创建在前、订阅在后。向量分区键与文档可见性都以这份展开为准：
 * 检索下发空间集、文档枚举与字面回退过滤都吃同一口径，避免各消费方各查一遍
 * 漂移。
 *
 * <p>失败隔离同经验条目/MCP 选择：查库失败只记日志、按无空间继续（返回空集），
 * 绝不拖垮调用方主流程。
 */
@Component
public class KnowledgeSpaceResolver {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSpaceResolver.class);

    private final AgentAiKnowledgeBaseMapper knowledgeBaseMapper;
    private final AgentAiKnowledgeBaseSubscriptionMapper knowledgeSubscriptionMapper;

    public KnowledgeSpaceResolver(
            AgentAiKnowledgeBaseMapper knowledgeBaseMapper,
            AgentAiKnowledgeBaseSubscriptionMapper knowledgeSubscriptionMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeSubscriptionMapper = knowledgeSubscriptionMapper;
    }

    /** 该 agent 可达的知识空间 id 集（创建∪订阅、去重保序）；失败或无空间时为空集。 */
    public List<Long> reachableBaseIds(long agentId) {
        try {
            LinkedHashSet<Long> baseIds = new LinkedHashSet<>();
            knowledgeBaseMapper
                    .selectList(new LambdaQueryWrapper<AgentAiKnowledgeBaseEntity>()
                            .eq(AgentAiKnowledgeBaseEntity::getCreatedByAgentId, agentId))
                    .forEach(base -> baseIds.add(base.getId()));
            knowledgeSubscriptionMapper
                    .selectList(new LambdaQueryWrapper<AgentAiKnowledgeBaseSubscriptionEntity>()
                            .eq(AgentAiKnowledgeBaseSubscriptionEntity::getAgentId, agentId))
                    .forEach(subscription -> baseIds.add(subscription.getBaseId()));
            return List.copyOf(baseIds);
        } catch (RuntimeException ex) {
            log.warn("knowledge base expansion failed; continuing without knowledge bases", ex);
            return List.of();
        }
    }
}
