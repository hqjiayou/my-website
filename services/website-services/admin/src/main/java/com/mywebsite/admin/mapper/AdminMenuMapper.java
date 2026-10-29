package com.mywebsite.admin.mapper;

import com.mywebsite.admin.constant.AdminRbacConstants;
import com.mywebsite.admin.entity.AdminPermissionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 查询 {@code admin_permission} 中 {@code resource_type = MENU} 的行（无业务判断）。 */
@Mapper
public interface AdminMenuMapper {

    @Select(
            """
            SELECT id, permission_code, permission_name, resource_type, resource_key, description, status,
                   parent_id, sort_order, route_path, icon,
                   created_at, created_by, updated_at, updated_by, deleted, deleted_at, version
            FROM admin_permission
            WHERE deleted = FALSE
              AND status = '"""
                    + AdminRbacConstants.STATUS_ACTIVE
                    + "'"
                    + """
              AND resource_type = '"""
                    + AdminRbacConstants.RESOURCE_TYPE_MENU
                    + "'"
                    + """
            ORDER BY sort_order ASC, permission_code ASC
            """)
    List<AdminPermissionEntity> selectAllActiveMenus();

    /** 管理列表：含停用，仍排除已软删。侧栏不要用这个结果。 */
    @Select(
            """
            SELECT id, permission_code, permission_name, resource_type, resource_key, description, status,
                   parent_id, sort_order, route_path, icon,
                   created_at, created_by, updated_at, updated_by, deleted, deleted_at, version
            FROM admin_permission
            WHERE deleted = FALSE
              AND resource_type = '"""
                    + AdminRbacConstants.RESOURCE_TYPE_MENU
                    + "'"
                    + """
            ORDER BY sort_order ASC, permission_code ASC
            """)
    List<AdminPermissionEntity> selectAllMenus();
}
