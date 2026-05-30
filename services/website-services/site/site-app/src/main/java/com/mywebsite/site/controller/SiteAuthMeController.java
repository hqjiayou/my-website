package com.mywebsite.site.controller;

import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.api.ResponseMessages;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.site.dto.SiteAuthMeResponseDto;
import com.mywebsite.site.dto.SiteAuthMeUserDto;
import com.mywebsite.site.dto.SiteMenuNodeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/site/auth")
@Tag(name = "Site · Auth Context")
public class SiteAuthMeController {

    @GetMapping("/me")
    @Operation(summary = "当前前台用户上下文（占位）")
    public ResponseEntity<HttpMessageBody<SiteAuthMeResponseDto>> me() {
        String subject =
                AuthContext.currentUser().map(u -> u.subject()).orElseThrow(IllegalStateException::new);
        long userId = SubjectCodes.parseSiteUserId(subject)
                .orElseThrow(() -> new IllegalStateException("invalid site subject in security context"));

        String userCode = SubjectCodes.formatSiteUserSubject(userId);
        SiteAuthMeUserDto user = new SiteAuthMeUserDto(userCode, null, null, null, "ACTIVE");

        List<String> roles = List.of("SITE_USER");
        List<String> permissions = List.of();
        List<SiteMenuNodeDto> menus = List.of();

        SiteAuthMeResponseDto data = new SiteAuthMeResponseDto(user, roles, permissions, menus);
        return ResponseEntity.ok(new HttpMessageBody<>(ResponseMessages.SUCCESS, data));
    }
}
