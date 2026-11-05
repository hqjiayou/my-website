package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * 知识空间（表 {@code agent_ai_knowledge_base}）。站点侧只读映射（写入在 admin）：
 * 与 admin {@code AdminAiKnowledgeBaseEntity} 同表双映射，声明列须与彼一致
 * （EntityTableColumnConsistencyTest 守此约）；站点侧实际只读创建者列做空间集展开
 * （Issue #209 UoW-2）。
 */
@TableName("agent_ai_knowledge_base")
public class AgentAiKnowledgeBaseEntity extends AbstractAuditableEntity {

    /** 创建该空间的 agent id（创建者留痕，空间经订阅被其他 agent 使用）。 */
    private Long createdByAgentId;

    private String name;

    @TableField("is_default")
    private Boolean isDefault;

    private String status;

    public Long getCreatedByAgentId() {
        return createdByAgentId;
    }

    public void setCreatedByAgentId(Long createdByAgentId) {
        this.createdByAgentId = createdByAgentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
