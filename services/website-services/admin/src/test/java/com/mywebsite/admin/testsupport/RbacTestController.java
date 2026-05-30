package com.mywebsite.admin.testsupport;

import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅 test scope：验证 Method Security + {@link com.mywebsite.admin.security.AdminPermissionChecker}。
 * 不进入 main 源码与生产路由。
 */
@RestController
@RequestMapping("/api/admin/test/rbac")
public class RbacTestController {

    /** 仅 test H2 数据与 Method Security 探针；不进入 main 常量与生产 Flyway 种子。 */
    private static final String PERMISSION_RBAC_CHECK = "admin:rbac:check";

    @GetMapping("/check")
    @PreAuthorize("@adminPermissionChecker.hasPermission('" + PERMISSION_RBAC_CHECK + "')")
    public ResponseEntity<HttpMessageBody<Map<String, String>>> check() {
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, Map.of("status", "ok")));
    }
}
