package com.mywebsite.common.security;

/** Access JWT {@code subject_type} claim；用于区分 Admin / Agent，防止跨端误用令牌。 */
public enum SubjectType {
    ADMIN,
    AGENT
}
