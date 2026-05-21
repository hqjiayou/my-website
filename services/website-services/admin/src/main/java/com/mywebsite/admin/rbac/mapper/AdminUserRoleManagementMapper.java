package com.mywebsite.admin.rbac.mapper;

import com.mywebsite.admin.rbac.mapper.AdminUserRoleManagementMapper.AdminUserRoleBindingRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserRoleManagementMapper {

    record AdminUserRoleBindingRow(Long roleId, String roleCode, String roleName, String status) {}

    @Select(
            """
            SELECT r.id AS role_id, r.role_code AS role_code, r.role_name AS role_name, r.status AS status
            FROM admin_user_role ur
            INNER JOIN admin_role r ON r.id = ur.admin_role_id
            WHERE ur.admin_user_id = #{userId}
              AND ur.deleted = FALSE
              AND r.deleted = FALSE
            ORDER BY r.role_code
            """)
    List<AdminUserRoleBindingRow> selectRoleBindingsByUserId(@Param("userId") long userId);
}
