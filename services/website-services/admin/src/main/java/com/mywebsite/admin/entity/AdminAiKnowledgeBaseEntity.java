package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/** 知识空间（容器）：平台资源，文档归属其一；createdByAgentId 为创建者留痕（不再是所有者），agent 经订阅使用。 */
@TableName("agent_ai_knowledge_base")
public class AdminAiKnowledgeBaseEntity extends AbstractAuditableEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

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
