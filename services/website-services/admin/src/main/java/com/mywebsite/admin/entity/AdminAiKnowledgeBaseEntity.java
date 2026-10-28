package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/** 知识库（容器）：归属一个所有者站点，文档归属其一；可链接给其他站点共享。 */
@TableName("agent_ai_knowledge_base")
public class AdminAiKnowledgeBaseEntity extends AbstractAuditableEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    private Long agentId;
    private String name;

    @TableField("is_default")
    private Boolean isDefault;

    private String status;

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
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
