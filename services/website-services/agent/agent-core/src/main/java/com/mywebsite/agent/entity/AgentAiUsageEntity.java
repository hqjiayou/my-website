package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * AI 用量记账（表 {@code agent_ai_usage}，Flyway V45，Issue #140）。每条用户请求一行，
 * 由 agent-app（PORTAL／GATEWAY_V1）与 admin（ADMIN_PREVIEW）经
 * {@code AgentUsageService} 写入，admin 只读聚合。tokens 为 null 表示拿不到，不记 0。
 */
@TableName("agent_ai_usage")
public class AgentAiUsageEntity extends AbstractAuditableEntity {

    private Long agentId;
    private Long entryId;
    private String alias;
    private String mode;
    private String source;
    private Long promptTokens;
    private Long completionTokens;
    private Long latencyMs;
    private String status;
    private Long gatewayAccessKeyId;

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getEntryId() {
        return entryId;
    }

    public void setEntryId(Long entryId) {
        this.entryId = entryId;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Long promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Long getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Long completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getGatewayAccessKeyId() {
        return gatewayAccessKeyId;
    }

    public void setGatewayAccessKeyId(Long gatewayAccessKeyId) {
        this.gatewayAccessKeyId = gatewayAccessKeyId;
    }
}
