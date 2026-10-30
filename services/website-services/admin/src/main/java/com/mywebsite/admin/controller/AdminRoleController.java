package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminRbacDtos.AdminRoleCreateRequest;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRoleDetailDto;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRoleListItemDto;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRoleListQuery;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRolePermissionsResponse;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRolePermissionsUpdateRequest;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminRoleUpdateRequest;
import com.mywebsite.admin.service.AdminRoleManagementService;
import com.mywebsite.admin.service.AdminRolePermissionManagementService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/roles")
@Tag(name = "Admin · Roles")
@SecurityRequirement(name = "adminBearer")
public class AdminRoleController {

    private final AdminRoleManagementService roleManagementService;

    private final AdminRolePermissionManagementService rolePermissionManagementService;

    public AdminRoleController(
            AdminRoleManagementService roleManagementService,
            AdminRolePermissionManagementService rolePermissionManagementService) {
        this.roleManagementService = roleManagementService;
        this.rolePermissionManagementService = rolePermissionManagementService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:role:list')")
    @Operation(summary = "角色列表")
    public ResponseEntity<HttpMessageBody<List<AdminRoleListItemDto>>> list(
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String status) {
        List<AdminRoleListItemDto> data =
                roleManagementService.listRoles(new AdminRoleListQuery(roleCode, roleName, status));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:role:list')")
    @Operation(summary = "角色详情")
    public ResponseEntity<HttpMessageBody<AdminRoleDetailDto>> get(@PathVariable long id) {
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, roleManagementService.getRole(id)));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:role:list')")
    @Operation(summary = "创建角色")
    public ResponseEntity<HttpMessageBody<AdminRoleDetailDto>> create(@Valid @RequestBody AdminRoleCreateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, roleManagementService.createRole(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:role:list')")
    @Operation(summary = "更新角色")
    public ResponseEntity<HttpMessageBody<AdminRoleDetailDto>> update(
            @PathVariable long id, @Valid @RequestBody AdminRoleUpdateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, roleManagementService.updateRole(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:role:list')")
    @Operation(summary = "删除角色（软删除）")
    public ResponseEntity<HttpMessageBody<Void>> delete(@PathVariable long id) {
        roleManagementService.deleteRole(id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:role:list')")
    @Operation(summary = "查询角色已绑定权限")
    public ResponseEntity<HttpMessageBody<AdminRolePermissionsResponse>> getPermissions(@PathVariable long id) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, rolePermissionManagementService.getRolePermissions(id)));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:role:list')")
    @Operation(summary = "替换角色权限绑定")
    public ResponseEntity<HttpMessageBody<AdminRolePermissionsResponse>> replacePermissions(
            @PathVariable long id, @Valid @RequestBody AdminRolePermissionsUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, rolePermissionManagementService.replaceRolePermissions(id, request)));
    }
}
