package com.mywebsite.admin.rbac.mapper;

import com.mywebsite.admin.rbac.AdminRbacConstants;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminRbacQueryMapper {

    @Select(
            """
            SELECT DISTINCT r.role_code
            FROM admin_user_role ur
            INNER JOIN admin_role r ON r.id = ur.admin_role_id
            WHERE ur.admin_user_id = #{userId}
              AND ur.deleted = FALSE
              AND r.deleted = FALSE
              AND r.status = '"""
                    + AdminRbacConstants.STATUS_ACTIVE
                    + "'"
                    + """
            ORDER BY r.role_code
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") long userId);

    @Select(
            """
            SELECT DISTINCT p.permission_code
            FROM admin_user_role ur
            INNER JOIN admin_role r ON r.id = ur.admin_role_id
            INNER JOIN admin_role_permission rp ON rp.admin_role_id = r.id
            INNER JOIN admin_permission p ON p.id = rp.admin_permission_id
            WHERE ur.admin_user_id = #{userId}
              AND ur.deleted = FALSE
              AND r.deleted = FALSE
              AND r.status = '"""
                    + AdminRbacConstants.STATUS_ACTIVE
                    + "'"
                    + """
              AND rp.deleted = FALSE
              AND p.deleted = FALSE
              AND p.status = '"""
                    + AdminRbacConstants.STATUS_ACTIVE
                    + "'"
                    + """
            ORDER BY p.permission_code
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") long userId);

    @Select(
            """
            SELECT permission_code
            FROM admin_permission
            WHERE deleted = FALSE
              AND status = '"""
                    + AdminRbacConstants.STATUS_ACTIVE
                    + "'"
                    + """
            ORDER BY permission_code
            """)
    List<String> selectAllActivePermissionCodes();

    @Select(
            """
            SELECT DISTINCT p.permission_code
            FROM admin_role_permission rp
            INNER JOIN admin_permission p ON p.id = rp.admin_permission_id
            WHERE rp.admin_role_id = #{roleId}
              AND rp.deleted = FALSE
              AND p.deleted = FALSE
              AND p.status = '"""
                    + AdminRbacConstants.STATUS_ACTIVE
                    + "'"
                    + """
            ORDER BY p.permission_code
            """)
    List<String> selectPermissionCodesByRoleId(@Param("roleId") long roleId);
}
