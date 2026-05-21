package com.mywebsite.admin.rbac.support;

import com.mywebsite.admin.entity.AdminRoleEntity;
import com.mywebsite.admin.rbac.AdminRbacConstants;
import com.mywebsite.admin.rbac.AdminRbacMessageKeys;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;

public final class AdminSuperAdminRoleSupport {

    private AdminSuperAdminRoleSupport() {}

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
