package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.time.OffsetDateTime;

/**
 * 对外 API 网关访问凭证只读映射（表 {@code gateway_access_key}）。签发/吊销在 admin
 * {@code AdminGatewayAccessKeyEntity}；这里只供网关 {@code /v1/**} 鉴权过滤器按哈希查询。
 * 列须与 admin 一侧逐列一致，见 {@code EntityTableColumnConsistencyTest}。
 */
@TableName("gateway_access_key")
public class AgentGatewayAccessKeyEntity extends AbstractAuditableEntity {

    private String name;
    private String keyHash;
    private String keyHint;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime expiresAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime revokedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getKeyHint() {
        return keyHint;
    }

    public void setKeyHint(String keyHint) {
        this.keyHint = keyHint;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }
}
