package com.mywebsite.admin.dto.authme;

public record AdminAuthMeUser(
        String userCode, String username, String displayName, String avatarUrl, String status) {}
