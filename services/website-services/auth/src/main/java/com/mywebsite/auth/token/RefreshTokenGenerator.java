package com.mywebsite.auth.token;

import java.security.SecureRandom;
import java.util.Base64;

public final class RefreshTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokenGenerator() {}

    /** Opaque refresh token (URL-safe Base64, ~43 chars for 32 bytes). */
    public static String generateOpaque() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
