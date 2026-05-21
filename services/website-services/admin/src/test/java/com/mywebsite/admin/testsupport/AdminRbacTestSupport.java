package com.mywebsite.admin.testsupport;

import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.AuthenticatedUser;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.common.security.TokenAudience;
import java.util.Set;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** RBAC 测试：绑定请求作用域与 {@link AuthContext}。 */
public final class AdminRbacTestSupport {

    private AdminRbacTestSupport() {}

    public static void bindAdminRequestContext(long adminUserId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        AuthContext.set(new AuthenticatedUser(
                TokenAudience.ADMIN, SubjectCodes.formatAdminSubject(adminUserId), null, Set.of()));
    }

    public static void clearRequestContext() {
        AuthContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }
}
