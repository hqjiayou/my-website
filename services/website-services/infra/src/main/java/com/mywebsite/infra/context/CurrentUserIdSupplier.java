package com.mywebsite.infra.context;

/**
 * 可选由业务模块提供：返回当前操作用户 ID；缺省时 {@link com.mywebsite.infra.handler.AuditMetaObjectHandler} 跳过审计人填充。
 */
@FunctionalInterface
public interface CurrentUserIdSupplier {

    Long currentUserId();
}
