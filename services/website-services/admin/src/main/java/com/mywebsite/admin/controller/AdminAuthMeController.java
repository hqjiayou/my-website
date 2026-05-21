package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.authme.AdminAuthMeResponse;
import com.mywebsite.admin.dto.authme.AdminAuthMeUser;
import com.mywebsite.admin.entity.AdminUserEntity;
import com.mywebsite.admin.mapper.AdminUserMapper;
import com.mywebsite.admin.rbac.service.AdminPermissionQueryService;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.SubjectCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "Admin · Auth Context")
public class AdminAuthMeController {

    private final AdminUserMapper adminUserMapper;

    private final AdminPermissionQueryService permissionQueryService;

    public AdminAuthMeController(AdminUserMapper adminUserMapper, AdminPermissionQueryService permissionQueryService) {
        this.adminUserMapper = adminUserMapper;
        this.permissionQueryService = permissionQueryService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "当前管理员上下文（用户 / 角色 / 权限 / 菜单）")
    public ResponseEntity<HttpMessageBody<AdminAuthMeResponse>> me() {
        String subject =
                AuthContext.currentUser().map(u -> u.subject()).orElseThrow(IllegalStateException::new);
        long userId = SubjectCodes.parseAdminUserId(subject)
                .orElseThrow(() -> new IllegalStateException("invalid admin subject in security context"));

        AdminUserEntity entity = adminUserMapper.selectById(userId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessageKey());
        }

        String userCode = SubjectCodes.formatAdminSubject(userId);
        AdminAuthMeUser user = new AdminAuthMeUser(
                userCode,
                entity.getUsername(),
                entity.getDisplayName(),
                null,
                entity.getStatus());

        var authorization = permissionQueryService
                .loadForUserCode(userCode)
                .orElseThrow(() -> new IllegalStateException("authenticated admin user missing RBAC snapshot"));

        AdminAuthMeResponse data = new AdminAuthMeResponse(
                user, authorization.roles(), authorization.permissions(), List.of());
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }
}
