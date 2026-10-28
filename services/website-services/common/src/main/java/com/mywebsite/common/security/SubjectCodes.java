package com.mywebsite.common.security;

import java.util.Optional;

/** Access JWT {@code sub}（subject）与用户主键之间的稳定编码（不落库字段亦可解码）。 */
public final class SubjectCodes {

    private static final String ADMIN_PREFIX = "ADMIN_";
    private static final String SITE_USER_PREFIX = "SITE_USER_";

    private SubjectCodes() {}

    public static String formatAdminSubject(long adminUserId) {
        return ADMIN_PREFIX + String.format("%06d", adminUserId);
    }

    public static Optional<Long> parseAdminUserId(String subject) {
        return parseSuffix(subject, ADMIN_PREFIX);
    }

    public static String formatAgentUserSubject(long agentUserId) {
        return SITE_USER_PREFIX + String.format("%06d", agentUserId);
    }

    public static Optional<Long> parseAgentUserId(String subject) {
        return parseSuffix(subject, SITE_USER_PREFIX);
    }

    private static Optional<Long> parseSuffix(String subject, String prefix) {
        if (subject == null || !subject.startsWith(prefix)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(subject.substring(prefix.length())));
        } catch (@SuppressWarnings("unused") NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
