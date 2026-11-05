package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * 知识空间（表 {@code agent_ai_knowledge_base}）。站点侧只读最小映射（写入在 admin）：
 * 仅声明聊天请求展开可检索空间集所需的创建者列，与 admin
 * {@code AdminAiKnowledgeBaseEntity} 同表双映射（Issue #209 UoW-2）。
 */
@TableName("agent_ai_knowledge_base")
public class AgentAiKnowledgeBaseEntity extends AbstractAuditableEntity {

    /** 创建该空间的 agent id（创建者留痕，空间经订阅被其他 agent 使用）。 */
    private Long createdByAgentId;

    public Long getCreatedByAgentId() {
        return createdByAgentId;
    }

    public void setCreatedByAgentId(Long createdByAgentId) {
        this.createdByAgentId = createdByAgentId;
    }
}
