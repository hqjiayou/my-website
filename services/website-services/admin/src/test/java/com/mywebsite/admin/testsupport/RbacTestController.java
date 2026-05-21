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
 * 仅 test scope：验证 Method Security + {@link com.mywebsite.admin.rbac.security.AdminPermissionChecker}。
 * 不进入 main 源码与生产路由。
 */
@RestController
@RequestMapping("/api/admin/test/rbac")
public class RbacTestController {

    @GetMapping("/check")
    @PreAuthorize("@adminPermissionChecker.hasPermission('admin:rbac:check')")
    public ResponseEntity<HttpMessageBody<Map<String, String>>> check() {
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, Map.of("status", "ok")));
    }
}
