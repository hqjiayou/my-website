package com.mywebsite.admin.rbac.service;

import com.mywebsite.admin.rbac.dto.AdminCurrentAuthorizationDto;
import java.util.Optional;

/** 按管理员用户加载 RBAC 快照；后续可在此实现 Redis 缓存。 */
public interface AdminPermissionQueryService {

    /** 从 JWT {@code sub} / {@code userCode}（如 {@code ADMIN_000001}）解析并加载。 */
    Optional<AdminCurrentAuthorizationDto> loadForUserCode(String userCode);

    Optional<AdminCurrentAuthorizationDto> loadForUserId(long userId);

    /** 从 {@link com.mywebsite.common.security.AuthContext} 解析当前 Admin 用户并加载。 */
    Optional<AdminCurrentAuthorizationDto> loadForCurrentUser();

    boolean isSuperAdmin(long userId);
}
