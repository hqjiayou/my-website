package com.mywebsite.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** Admin User 管理 HTTP 契约 DTO 聚合（不含 passwordHash）。 */
public final class AdminUserDtos {

    private AdminUserDtos() {}

    /** {@code userCode}/{@code protectedUser} 为计算字段，不落库。 */
    public record AdminUserListItemDto(
            Long id,
            String userCode,
            String username,
            String displayName,
            String email,
            String phone,
            String status,
            boolean protectedUser) {}

    /** {@code userCode}/{@code protectedUser} 为计算字段，不落库。 */
    public record AdminUserDetailDto(
            Long id,
            String userCode,
            String username,
            String displayName,
            String email,
            String phone,
            String status,
            boolean protectedUser,
            OffsetDateTime lastLoginAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record AdminUserCreateRequest(
            @NotBlank @Size(max = 128) String username,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 255) String displayName,
            @Email @Size(max = 255) String email,
            @Size(max = 32) String phone) {}

    public record AdminUserUpdateRequest(
            @Size(max = 255) String displayName,
            @Email @Size(max = 255) String email,
            @Size(max = 32) String phone) {}

    public record AdminUserStatusUpdateRequest(@NotBlank @Size(max = 32) String status) {}

    public record AdminUserListQuery(String username, String status, String displayName) {}
}
