package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.OffsetDateTime;

/** 知识空间订阅（纯关系行，删除即真删，不走软删）：一行表示一个 agent 订阅了一个知识空间。 */
@TableName("agent_ai_knowledge_base_subscription")
public class AdminAiKnowledgeBaseSubscriptionEntity implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long baseId;
    private Long agentId;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBaseId() {
        return baseId;
    }

    public void setBaseId(Long baseId) {
        this.baseId = baseId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
