package com.mywebsite.auth.service.impl;

import com.mywebsite.auth.dto.AdminLoginCookieDto;
import com.mywebsite.auth.dto.AdminLoginResponseDto;
import com.mywebsite.auth.dto.AdminRefreshCookieDto;
import com.mywebsite.auth.dto.AdminRefreshResponseDto;
import com.mywebsite.auth.dto.AdminRegisterRequestDto;
import com.mywebsite.auth.dto.AdminRegisterResponseDto;
import com.mywebsite.auth.dto.IssuedAccessTokenDto;
import com.mywebsite.auth.dto.RotateRefreshDto;
import com.mywebsite.auth.entity.AdminUserEntity;
import com.mywebsite.auth.mapper.AdminUserMapper;
import com.mywebsite.auth.constant.AdminAuthConstants;
import com.mywebsite.auth.properties.AdminAuthProperties;
import com.mywebsite.auth.service.AdminAuthService;
import com.mywebsite.auth.service.AdminRefreshSessionService;
import com.mywebsite.auth.service.TokenService;
import com.mywebsite.auth.utils.RefreshTokenUtils;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.TokenAudience;
import com.mywebsite.common.security.config.TokenProperties;
import com.mywebsite.common.web.HttpRequestCorrelation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthServiceImpl.class);

    private static final String KEY_LOGIN_FAILED = "admin.auth.login.failed";

    private static final String KEY_REFRESH_INVALID = "admin.auth.refresh.invalid";

    private static final String KEY_ACCOUNT_DISABLED = "admin.auth.account.disabled";

    private static final String KEY_PASSWORD_TOO_LONG = "admin.auth.password.too-long";

    private static final String KEY_REGISTER_DISABLED = "admin.auth.register.disabled";

    private static final String KEY_REGISTER_TAKEN = "admin.auth.register.taken";

    private static final String KEY_PASSWORD_TOO_SHORT = "admin.auth.register.password-too-short";

    private final AdminUserMapper adminUserMapper;

    private final PasswordEncoder passwordEncoder;

    private final TokenService tokenService;

    private final AdminRefreshSessionService refreshSessionService;

    private final TokenProperties tokenProperties;

    private final AdminAuthProperties adminAuthProperties;

    public AdminAuthServiceImpl(
            AdminUserMapper adminUserMapper,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            AdminRefreshSessionService refreshSessionService,
            TokenProperties tokenProperties,
            AdminAuthProperties adminAuthProperties) {
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshSessionService = refreshSessionService;
        this.tokenProperties = tokenProperties;
        this.adminAuthProperties = adminAuthProperties;
    }

    @Override
    public AdminLoginCookieDto login(String username, String rawPassword) {
        validatePasswordPolicy(rawPassword);

        String trimmedUsername = username.trim();

        AdminUserEntity user =
                adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
                        .eq(AdminUserEntity::getUsername, trimmedUsername));

        if (user == null || user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            denyBadCredential(trimmedUsername);
        }

        String status = user.getStatus() == null ? "" : user.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!AdminAuthConstants.ACTIVE_ADMIN_ACCOUNT_STATUS.equals(status)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), KEY_ACCOUNT_DISABLED);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            denyBadCredential(user.getUsername());
        }

        user.setLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
        adminUserMapper.updateById(user);

        String sessionId = UUID.randomUUID().toString();
        String refreshPlain = RefreshTokenUtils.generateOpaque();
        Instant expireAt = Instant.now().plus(tokenProperties.refreshTokenTtl());

        refreshSessionService.bindNewSession(user.getId(), user.getUsername(), sessionId, refreshPlain, expireAt);

        String accessToken = tokenService.createAccessToken(new IssuedAccessTokenDto(
                TokenAudience.ADMIN,
                String.valueOf(user.getId()),
                user.getUsername(),
                sessionId,
                Set.of()));

        long expiresInSec = tokenProperties.accessTokenTtl().toSeconds();

        AdminLoginResponseDto body = new AdminLoginResponseDto(accessToken, expiresInSec);

        log.info(
                "auth security login_success requestId={} realm=admin userId={}",
                MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID),
                user.getId());

        return new AdminLoginCookieDto(body, refreshPlain, expireAt);
    }

    @Override
    public AdminRefreshCookieDto refresh(Optional<String> refreshTokenPlainOptional) {
        String refreshPlain =
                refreshTokenPlainOptional.filter(s -> !s.isBlank()).orElseThrow(this::unauthorizedRefresh);

        RotateRefreshDto rotated = refreshSessionService.rotate(refreshPlain);
        return switch (rotated) {
            case RotateRefreshDto.Invalid ignored -> throw unauthorizedRefresh();
            case RotateRefreshDto.ReplayRevoked ignored -> {
                log.warn(
                        "auth security refresh_replay_detected requestId={} realm=admin sessionRevoked=true",
                        MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID));
                throw unauthorizedRefresh();
            }
            case RotateRefreshDto.Rotated ok -> {
                String accessToken = tokenService.createAccessToken(new IssuedAccessTokenDto(
                        TokenAudience.ADMIN,
                        String.valueOf(ok.adminUserId()),
                        ok.username(),
                        ok.sessionId(),
                        Set.of()));
                long expiresInSec = tokenProperties.accessTokenTtl().toSeconds();
                AdminRefreshResponseDto body = new AdminRefreshResponseDto(accessToken, expiresInSec);
                log.info(
                        "auth security refresh_success requestId={} realm=admin userId={}",
                        MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID),
                        ok.adminUserId());
                yield new AdminRefreshCookieDto(body, ok.newRefreshPlain(), ok.expireAt());
            }
        };
    }

    @Override
    public void logout(Optional<String> refreshTokenPlainOptional) {
        refreshSessionService.revokeByRefreshCookie(refreshTokenPlainOptional);
    }

    @Override
    public AdminRegisterResponseDto register(AdminRegisterRequestDto request) {
        ensureRegistrationChannelOpen();

        validatePasswordPolicy(request.password());
        validateRegisterMinimumPassword(request.password());

        String trimmedUsername = request.username().trim();
        ensureUsernameFree(trimmedUsername);

        String emailNorm = normalizeEmail(request.email());
        if (emailNorm != null) {
            ensureEmailFree(emailNorm);
        }

        String displayNorm = normalizeDisplayName(request.displayName());

        AdminUserEntity entity = new AdminUserEntity();
        entity.setUsername(trimmedUsername);
        entity.setPasswordHash(passwordEncoder.encode(request.password()));
        entity.setEmail(emailNorm);
        entity.setDisplayName(displayNorm);
        entity.setStatus(AdminAuthConstants.ACTIVE_ADMIN_ACCOUNT_STATUS);

        try {
            adminUserMapper.insert(entity);
        } catch (DataIntegrityViolationException ignored) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), KEY_REGISTER_TAKEN);
        }

        return new AdminRegisterResponseDto(entity.getId(), trimmedUsername);
    }

    private BusinessException unauthorizedRefresh() {
        return new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), KEY_REFRESH_INVALID);
    }

    private void denyBadCredential(String attemptedUsername) {
        log.warn(
                "auth security login_failed requestId={} realm=admin username={}",
                MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID),
                attemptedUsername);
        throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), KEY_LOGIN_FAILED);
    }

    private void validatePasswordPolicy(String rawPassword) {
        int maxLen = adminAuthProperties.getAdminLoginMaxPasswordLength();
        if (rawPassword != null && rawPassword.length() > maxLen) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), KEY_PASSWORD_TOO_LONG);
        }
    }

    private void validateRegisterMinimumPassword(String rawPassword) {
        int minLen = Math.max(1, adminAuthProperties.getAdminRegisterMinPasswordLength());
        int maxLen = adminAuthProperties.getAdminLoginMaxPasswordLength();
        if (minLen > maxLen) {
            minLen = maxLen;
        }
        if (rawPassword.length() < minLen) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), KEY_PASSWORD_TOO_SHORT, minLen);
        }
    }

    private void ensureRegistrationChannelOpen() {
        long activeCount = adminUserMapper.selectCount(new LambdaQueryWrapper<>());
        boolean bootstrap = adminAuthProperties.isAdminBootstrapRegistrationEnabled() && activeCount == 0;
        boolean open = adminAuthProperties.isAdminOpenRegistrationEnabled();
        if (!bootstrap && !open) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), KEY_REGISTER_DISABLED);
        }
    }

    private void ensureUsernameFree(String username) {
        AdminUserEntity dup =
                adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
                        .eq(AdminUserEntity::getUsername, username));
        if (dup != null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), KEY_REGISTER_TAKEN);
        }
    }

    private void ensureEmailFree(String normalizedEmail) {
        AdminUserEntity dup =
                adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
                        .eq(AdminUserEntity::getEmail, normalizedEmail));
        if (dup != null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), KEY_REGISTER_TAKEN);
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        return displayName.trim();
    }
}
