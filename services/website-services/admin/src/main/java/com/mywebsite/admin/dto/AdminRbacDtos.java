package com.mywebsite.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** RBAC HTTP 契约 DTO 聚合（请求/响应 record，不含内部授权快照）。 */
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

    /** 菜单编码固定落在 {@code admin:menu:} 命名空间，避免和 ACTION 权限码混用。 */
    public record AdminMenuCreateRequest(
            @NotBlank
                    @Size(max = 128)
                    @Pattern(regexp = "^admin:menu:[a-z][a-z0-9]*([.:][a-z0-9]+)*$")
                    String menuCode,
            @NotBlank @Size(max = 255) String menuName,
            Long parentId,
            @Size(max = 255) String path,
            @Size(max = 128) String icon,
            @NotNull @Min(0) @Max(9999) Integer sort,
            @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status) {}

    public record AdminMenuUpdateRequest(
            @NotBlank @Size(max = 255) String menuName,
            Long parentId,
            @Size(max = 255) String path,
            @Size(max = 128) String icon,
            @NotNull @Min(0) @Max(9999) Integer sort,
            @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status) {}
}
