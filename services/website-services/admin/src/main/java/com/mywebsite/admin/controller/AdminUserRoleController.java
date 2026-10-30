package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminRbacDtos.AdminUserRoleResponse;
import com.mywebsite.admin.dto.AdminRbacDtos.AdminUserRoleUpdateRequest;
import com.mywebsite.admin.service.AdminUserRoleManagementService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin · User Roles")
@SecurityRequirement(name = "adminBearer")
public class AdminUserRoleController {

    private final AdminUserRoleManagementService userRoleManagementService;

    public AdminUserRoleController(AdminUserRoleManagementService userRoleManagementService) {
        this.userRoleManagementService = userRoleManagementService;
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:user:list')")
    @Operation(summary = "查询管理员角色绑定")
    public ResponseEntity<HttpMessageBody<AdminUserRoleResponse>> getRoles(@PathVariable long id) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, userRoleManagementService.getUserRoles(id)));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:user:list')")
    @Operation(summary = "替换管理员角色绑定")
    public ResponseEntity<HttpMessageBody<AdminUserRoleResponse>> replaceRoles(
            @PathVariable long id, @Valid @RequestBody AdminUserRoleUpdateRequest request) {
        return ResponseEntity.ok(new HttpMessageBody<>(
                ResponseMessages.SUCCESS, userRoleManagementService.replaceUserRoles(id, request)));
    }
}
