package com.mywebsite.admin.service.impl;

import com.mywebsite.admin.constant.AdminRbacConstants;
import com.mywebsite.admin.entity.AdminUserEntity;
import com.mywebsite.admin.mapper.AdminRbacQueryMapper;
import com.mywebsite.admin.mapper.AdminUserMapper;
import com.mywebsite.admin.security.AdminCurrentAuthorizationSnapshot;
import com.mywebsite.admin.service.AdminPermissionQueryService;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.common.security.TokenAudience;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AdminPermissionQueryServiceImpl implements AdminPermissionQueryService {

    private final AdminRbacQueryMapper rbacQueryMapper;

    private final AdminUserMapper adminUserMapper;

    public AdminPermissionQueryServiceImpl(AdminRbacQueryMapper rbacQueryMapper, AdminUserMapper adminUserMapper) {
        this.rbacQueryMapper = rbacQueryMapper;
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public Optional<AdminCurrentAuthorizationSnapshot> loadForCurrentUser() {
        return AuthContext.currentUser()
                .filter(u -> u.audience() == TokenAudience.ADMIN)
                .map(u -> u.subject())
                .flatMap(this::loadForUserCode);
    }

    @Override
    public Optional<AdminCurrentAuthorizationSnapshot> loadForUserCode(String userCode) {
        return SubjectCodes.parseAdminUserId(userCode).flatMap(this::loadForUserId);
    }

    @Override
    public Optional<AdminCurrentAuthorizationSnapshot> loadForUserId(long userId) {
        AdminUserEntity user = adminUserMapper.selectById(userId);
        if (user == null || !AdminRbacConstants.STATUS_ACTIVE.equals(user.getStatus())) {
            return Optional.empty();
        }

        List<String> roles = rbacQueryMapper.selectRoleCodesByUserId(userId);
        boolean superAdmin = roles.contains(AdminRbacConstants.ROLE_SUPER_ADMIN);
        List<String> permissions = superAdmin
                ? rbacQueryMapper.selectAllActivePermissionCodes()
                : rbacQueryMapper.selectPermissionCodesByUserId(userId);

        return Optional.of(new AdminCurrentAuthorizationSnapshot(
                userId, List.copyOf(roles), List.copyOf(permissions), superAdmin));
    }

    @Override
    public boolean isSuperAdmin(long userId) {
        return rbacQueryMapper.selectRoleCodesByUserId(userId).contains(AdminRbacConstants.ROLE_SUPER_ADMIN);
    }
}
