package com.mywebsite.common.security;

import java.util.Optional;

public final class AuthContext {

    private static final ThreadLocal<AuthenticatedUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {}

    public static void set(AuthenticatedUser user) {
        HOLDER.set(user);
    }

    public static Optional<AuthenticatedUser> currentUser() {
        return Optional.ofNullable(HOLDER.get());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
