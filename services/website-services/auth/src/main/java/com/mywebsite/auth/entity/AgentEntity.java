package com.mywebsite.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * auth 模块只读站点凭证视图（表 {@code agent}）。
 *
 * <p>不依赖 agent-core；仅用于 Agent 登录时按 {@code site_code} 解析站点状态。
 */
@TableName("agent")
public class AgentEntity extends AbstractAuditableEntity {

    private String agentCode;
    private String agentName;
    private String agentType;
    private String status;

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
