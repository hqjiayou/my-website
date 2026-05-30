package com.mywebsite.admin.service;

import com.mywebsite.admin.constant.AdminRbacConstants;
import com.mywebsite.admin.constant.AdminRbacMessageKeys;
import com.mywebsite.admin.entity.AdminRoleEntity;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;

/** super_admin 角色保护规则（RBAC 业务约束，非 Security 链路组件）。 */
public final class AdminRoleGuard {

    private AdminRoleGuard() {}

    public static boolean isSuperAdminRole(AdminRoleEntity role) {
        return role != null && AdminRbacConstants.ROLE_SUPER_ADMIN.equals(role.getRoleCode());
    }

    public static boolean isSuperAdminRoleCode(String roleCode) {
        return AdminRbacConstants.ROLE_SUPER_ADMIN.equals(roleCode);
    }

    public static void rejectIfSuperAdmin(AdminRoleEntity role) {
        if (isSuperAdminRole(role)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.ROLE_SUPER_ADMIN_IMMUTABLE);
        }
    }

    public static void rejectSuperAdminRoleCode(String roleCode) {
        if (isSuperAdminRoleCode(roleCode)) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(), AdminRbacMessageKeys.ROLE_CODE_RESERVED);
        }
    }
}
