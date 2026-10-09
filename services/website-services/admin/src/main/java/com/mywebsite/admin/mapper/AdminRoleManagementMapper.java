package com.mywebsite.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminRoleManagementMapper {

    @Select(
            """
            SELECT COUNT(*)
            FROM admin_user_role ur
            JOIN admin_user u ON u.id = ur.admin_user_id
            WHERE ur.admin_role_id = #{roleId}
              AND ur.deleted = FALSE
              AND u.deleted = FALSE
            """)
    long countActiveUsersByRoleId(@Param("roleId") long roleId);
}
