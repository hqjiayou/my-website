package com.mywebsite.admin.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 5C RBAC 管理 HTTP 契约（集中放置，避免过度拆分包）。 */
public final class AdminRbacDtos {

    private AdminRbacDtos() {}

    public record AdminRoleListItemDto(
            Long id, String roleCode, String roleName, String status, String description) {}

    public record AdminRoleDetailDto(
            Long id, String roleCode, String roleName, String status, String description) {}

    public record AdminRoleCreateRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]*$") @Size(max = 64) String roleCode,
            @NotBlank @Size(max = 255) String roleName,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 32) String status) {}

    public record AdminRoleUpdateRequest(
            @NotBlank @Size(max = 255) String roleName,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 32) String status) {}

    public record AdminRoleListQuery(String roleCode, String roleName, String status) {}

    public record AdminPermissionItemDto(
            Long id,
            String permissionCode,
            String permissionName,
            String resourceType,
            String resourceKey,
            String status) {}

    public record AdminPermissionListQuery(String permissionCode, String resourceType, String resourceKey) {}

    public record AdminRolePermissionsResponse(Long roleId, String roleCode, List<String> permissions) {}

    public record AdminRolePermissionsUpdateRequest(List<@NotBlank String> permissionCodes) {}

    public record AdminRoleBindingDto(
            Long roleId, String roleCode, String roleName, String status, boolean protectedRole) {}

    public record AdminUserRoleResponse(
            Long userId, String userCode, String username, List<AdminRoleBindingDto> roles) {}

    public record AdminUserRoleUpdateRequest(@NotEmpty List<@NotBlank String> roleCodes) {}
}
