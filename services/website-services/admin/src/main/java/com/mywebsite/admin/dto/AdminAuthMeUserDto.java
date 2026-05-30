package com.mywebsite.admin.dto;

public record AdminAuthMeUserDto(
        String userCode, String username, String displayName, String avatarUrl, String status) {}
