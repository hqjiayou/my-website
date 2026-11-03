package com.mywebsite.admin.security;

import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.common.security.TokenAudience;
import com.mywebsite.infra.context.CurrentUserIdSupplier;
import org.springframework.stereotype.Component;

/**
 * 把当前 Admin JWT 的用户 ID 交给 {@link com.mywebsite.infra.handler.AuditMetaObjectHandler}，
 * 填充 {@code created_by}/{@code updated_by}。无登录上下文时返回 null，由库默认值或可空列承接。
 */
@Component
public class AdminCurrentUserIdSupplier implements CurrentUserIdSupplier {

    @Override
    public Long currentUserId() {
        return AuthContext.currentUser()
                .filter(user -> user.audience() == TokenAudience.ADMIN)
                .flatMap(user -> SubjectCodes.parseAdminUserId(user.subject()))
                .orElse(null);
    }
}
