package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.time.OffsetDateTime;

/** 对外 API 网关访问凭证（表 {@code gateway_access_key}）。{@code keyHash} 不得写入接口响应，明文只在签发时一次性返回。 */
@TableName("gateway_access_key")
public class AdminGatewayAccessKeyEntity extends AbstractAuditableEntity {

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
