package com.mywebsite.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminRoleManagementMapper {

    @Select(
            """
            SELECT COUNT(*)
            FROM admin_user_role
            WHERE admin_role_id = #{roleId}
              AND deleted = FALSE
            """)
    long countActiveUsersByRoleId(@Param("roleId") long roleId);
}
