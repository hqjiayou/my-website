package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.AdminUserDtos.AdminUserCreateRequest;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserDetailDto;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserListItemDto;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserListQuery;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminUserDtos.AdminUserUpdateRequest;
import com.mywebsite.admin.service.AdminUserManagementService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin · Users")
@SecurityRequirement(name = "adminBearer")
public class AdminUserController {

    private final AdminUserManagementService userManagementService;

    public AdminUserController(AdminUserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:user:list')")
    @Operation(summary = "管理员用户列表")
    public ResponseEntity<HttpMessageBody<List<AdminUserListItemDto>>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String displayName) {
        List<AdminUserListItemDto> data =
                userManagementService.listUsers(new AdminUserListQuery(username, status, displayName));
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:user:list')")
    @Operation(summary = "管理员用户详情")
    public ResponseEntity<HttpMessageBody<AdminUserDetailDto>> get(@PathVariable long id) {
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, userManagementService.getUser(id)));
    }

    @PostMapping
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:user:create')")
    @Operation(summary = "创建管理员用户")
    public ResponseEntity<HttpMessageBody<AdminUserDetailDto>> create(
            @Valid @RequestBody AdminUserCreateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, userManagementService.createUser(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:user:update')")
    @Operation(summary = "更新管理员用户资料")
    public ResponseEntity<HttpMessageBody<AdminUserDetailDto>> update(
            @PathVariable long id, @Valid @RequestBody AdminUserUpdateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, userManagementService.updateUser(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:user:status')")
    @Operation(summary = "变更管理员用户状态")
    public ResponseEntity<HttpMessageBody<AdminUserDetailDto>> updateStatus(
            @PathVariable long id, @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        return ResponseEntity.ok(
                new HttpMessageBody<>(ResponseMessages.SUCCESS, userManagementService.updateStatus(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:user:delete')")
    @Operation(summary = "删除管理员用户（软删除）")
    public ResponseEntity<HttpMessageBody<Void>> delete(@PathVariable long id) {
        userManagementService.deleteUser(id);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, null));
    }
}
