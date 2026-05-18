package com.mywebsite.auth.controller;

import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.infra.service.LocalizedMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/site")
@Tag(name = "Auth · Site Login（预留）")
public class AuthSiteLoginController {

    private static final String MSG_NOT_IMPLEMENTED = "auth.site.login.not-implemented";

    private final LocalizedMessageService localizedMessageService;

    public AuthSiteLoginController(LocalizedMessageService localizedMessageService) {
        this.localizedMessageService = localizedMessageService;
    }

    @PostMapping("/login")
    @Operation(summary = "站点登录（预留，当前未实现）")
    public ResponseEntity<HttpMessageBody<Void>> login() {
        String message = localizedMessageService.getMessage(MSG_NOT_IMPLEMENTED);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(new HttpMessageBody<>(message, null));
    }
}
