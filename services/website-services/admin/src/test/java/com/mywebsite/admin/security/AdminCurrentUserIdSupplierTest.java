package com.mywebsite.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.AuthenticatedUser;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.common.security.TokenAudience;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AdminCurrentUserIdSupplierTest {

    private final AdminCurrentUserIdSupplier supplier = new AdminCurrentUserIdSupplier();

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    void returnsAdminUserIdFromAuthContext() {
        AuthContext.set(new AuthenticatedUser(
                TokenAudience.ADMIN, SubjectCodes.formatAdminSubject(42L), "hqjiayou", null, Set.of()));

        assertThat(supplier.currentUserId()).isEqualTo(42L);
    }

    @Test
    void returnsNullWithoutAuthContext() {
        assertThat(supplier.currentUserId()).isNull();
    }

    @Test
    void returnsNullForNonAdminAudience() {
        AuthContext.set(new AuthenticatedUser(
                TokenAudience.AGENT, SubjectCodes.formatAgentUserSubject(7L), "u", 1L, Set.of()));

        assertThat(supplier.currentUserId()).isNull();
    }
}
