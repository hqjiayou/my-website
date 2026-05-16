package com.mywebsite.infra.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.mywebsite.infra.context.CurrentUserIdSupplier;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 仅填充 {@code created_by} / {@code updated_by}；时间与逻辑删除时刻由库或业务显式处理。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private final ObjectProvider<CurrentUserIdSupplier> currentUserIdSupplier;

    public AuditMetaObjectHandler(ObjectProvider<CurrentUserIdSupplier> currentUserIdSupplier) {
        this.currentUserIdSupplier = currentUserIdSupplier;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        Long uid = resolveUserId();
        if (uid != null) {
            strictInsertFill(metaObject, "createdBy", Long.class, uid);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Long uid = resolveUserId();
        if (uid != null) {
            strictUpdateFill(metaObject, "updatedBy", Long.class, uid);
        }
    }

    private Long resolveUserId() {
        CurrentUserIdSupplier supplier = currentUserIdSupplier.getIfAvailable();
        return supplier == null ? null : supplier.currentUserId();
    }
}
