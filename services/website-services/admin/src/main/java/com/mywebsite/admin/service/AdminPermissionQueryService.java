package com.mywebsite.admin.service;

import com.mywebsite.admin.security.AdminCurrentAuthorizationSnapshot;
import java.util.Optional;

public interface AdminPermissionQueryService {

    Optional<AdminCurrentAuthorizationSnapshot> loadForUserCode(String userCode);

    Optional<AdminCurrentAuthorizationSnapshot> loadForUserId(long userId);

    /** 从 {@link com.mywebsite.common.security.AuthContext} 解析当前管理员并加载快照。 */
    Optional<AdminCurrentAuthorizationSnapshot> loadForCurrentUser();

    boolean isSuperAdmin(long userId);
}
