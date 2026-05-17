package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.constant.AdminAuthConstants;
import com.mywebsite.admin.dto.AdminLoginCookieDto;
import com.mywebsite.admin.dto.AdminLoginResponseDto;
import com.mywebsite.admin.dto.AdminLoginUserDto;
import com.mywebsite.admin.dto.AdminRefreshCookieDto;
import com.mywebsite.admin.dto.AdminRefreshResponseDto;
import com.mywebsite.admin.dto.RotateRefreshDto;
import com.mywebsite.admin.entity.AdminUserEntity;
import com.mywebsite.admin.mapper.AdminUserMapper;
import com.mywebsite.admin.properties.AdminAuthProperties;
import com.mywebsite.admin.service.AdminAuthService;
import com.mywebsite.admin.service.AdminRefreshSessionService;
import com.mywebsite.auth.config.JwtProperties;
import com.mywebsite.auth.token.IssuedAccessToken;
import com.mywebsite.auth.token.RefreshTokenGenerator;
import com.mywebsite.auth.token.TokenAudience;
import com.mywebsite.auth.token.TokenService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final String KEY_LOGIN_FAILED = "admin.auth.login.failed";

    private static final String KEY_REFRESH_INVALID = "admin.auth.refresh.invalid";

    private static final String KEY_ACCOUNT_DISABLED = "admin.auth.account.disabled";

    private static final String KEY_PASSWORD_TOO_LONG = "admin.auth.password.too-long";

    private final AdminUserMapper adminUserMapper;

    private final PasswordEncoder passwordEncoder;

    private final TokenService tokenService;

    private final AdminRefreshSessionService refreshSessionService;

    private final JwtProperties jwtProperties;

    private final AdminAuthProperties adminAuthProperties;

    public AdminAuthServiceImpl(
            AdminUserMapper adminUserMapper,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            AdminRefreshSessionService refreshSessionService,
            JwtProperties jwtProperties,
            AdminAuthProperties adminAuthProperties) {
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshSessionService = refreshSessionService;
        this.jwtProperties = jwtProperties;
        this.adminAuthProperties = adminAuthProperties;
    }

    @Override
    public AdminLoginCookieDto login(String username, String rawPassword) {
        validatePasswordPolicy(rawPassword);

        AdminUserEntity user =
                adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
                        .eq(AdminUserEntity::getUsername, username.trim()));

        if (user == null || user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            denyBadCredential();
        }

        String status = user.getStatus() == null ? "" : user.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!AdminAuthConstants.ACTIVE_ADMIN_ACCOUNT_STATUS.equals(status)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), KEY_ACCOUNT_DISABLED);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            denyBadCredential();
        }

        String sessionId = UUID.randomUUID().toString();
        String refreshPlain = RefreshTokenGenerator.generateOpaque();
        Instant expireAt = Instant.now().plus(adminAuthProperties.getRefreshTokenTtl());

        refreshSessionService.bindNewSession(user.getId(), user.getUsername(), sessionId, refreshPlain, expireAt);

        String accessToken = tokenService.createAccessToken(new IssuedAccessToken(
                TokenAudience.ADMIN,
                String.valueOf(user.getId()),
                user.getUsername(),
                sessionId,
                Set.of()));

        long expiresInSec = jwtProperties.getAccessTokenTtl().toSeconds();

        AdminLoginUserDto userDto =
                new AdminLoginUserDto(user.getId(), user.getUsername(), user.getDisplayName(), user.getStatus());

        AdminLoginResponseDto body = new AdminLoginResponseDto(accessToken, expiresInSec, userDto);

        return new AdminLoginCookieDto(body, refreshPlain, expireAt);
    }

    @Override
    public AdminRefreshCookieDto refresh(Optional<String> refreshTokenPlainOptional) {
        String refreshPlain =
                refreshTokenPlainOptional.filter(s -> !s.isBlank()).orElseThrow(this::unauthorizedRefresh);

        RotateRefreshDto rotated = refreshSessionService.rotate(refreshPlain);
        return switch (rotated) {
            case RotateRefreshDto.Invalid ignored -> throw unauthorizedRefresh();
            case RotateRefreshDto.ReplayRevoked ignored -> throw unauthorizedRefresh();
            case RotateRefreshDto.Rotated ok -> {
                String accessToken = tokenService.createAccessToken(new IssuedAccessToken(
                        TokenAudience.ADMIN,
                        String.valueOf(ok.adminUserId()),
                        ok.username(),
                        ok.sessionId(),
                        Set.of()));
                long expiresInSec = jwtProperties.getAccessTokenTtl().toSeconds();
                AdminRefreshResponseDto body = new AdminRefreshResponseDto(accessToken, expiresInSec);
                yield new AdminRefreshCookieDto(body, ok.newRefreshPlain(), ok.expireAt());
            }
        };
    }

    @Override
    public void logout(Optional<String> refreshTokenPlainOptional) {
        refreshSessionService.revokeByRefreshCookie(refreshTokenPlainOptional);
    }

    private BusinessException unauthorizedRefresh() {
        return new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), KEY_REFRESH_INVALID);
    }

    private void denyBadCredential() {
        throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), KEY_LOGIN_FAILED);
    }

    private void validatePasswordPolicy(String rawPassword) {
        int maxLen = adminAuthProperties.getAdminLoginMaxPasswordLength();
        if (rawPassword != null && rawPassword.length() > maxLen) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), KEY_PASSWORD_TOO_LONG);
        }
    }
}
