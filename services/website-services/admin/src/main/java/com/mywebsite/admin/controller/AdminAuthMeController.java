package com.mywebsite.admin.controller;

import com.mywebsite.admin.dto.authme.AdminAuthMeResponse;
import com.mywebsite.admin.dto.authme.AdminAuthMeUser;
import com.mywebsite.admin.dto.authme.AdminMenuNode;
import com.mywebsite.admin.entity.AdminUserEntity;
import com.mywebsite.admin.mapper.AdminUserMapper;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "Admin · Auth Context")
public class AdminAuthMeController {

    private final AdminUserMapper adminUserMapper;

    public AdminAuthMeController(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    @GetMapping("/me")
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

        List<String> roles = List.of("SUPER_ADMIN");
        List<String> permissions =
                List.of("admin:user:list", "admin:user:create", "admin:role:list", "admin:menu:list");

        List<AdminMenuNode> menus = List.of(new AdminMenuNode(
                "system",
                "系统管理",
                "/system",
                "SettingOutlined",
                10,
                List.of(new AdminMenuNode(
                        "system-user", "用户管理", "/system/user", null, 10, List.of()))));

        AdminAuthMeResponse data = new AdminAuthMeResponse(user, roles, permissions, menus);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }
}
