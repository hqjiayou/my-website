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
import com.mywebsite.auth.service.AdminAuthRateLimitService;
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

    private static final String KEY_RATE_LIMITED = "admin.auth.rate-limited";

    private final AdminUserMapper adminUserMapper;

    private final PasswordEncoder passwordEncoder;

    private final TokenService tokenService;

    private final AdminRefreshSessionService refreshSessionService;

    private final TokenProperties tokenProperties;

    private final AdminAuthProperties adminAuthProperties;

    private final AdminAuthRateLimitService rateLimitService;

    /**
     * 启动时用注入的 encoder 生成一次；未知用户名路径也走 matches，抹平枚举时间侧信道。
     * 不用硬编码 hash，避免将来调整 BCrypt strength 后重新打开侧信道。
     */
    private final String dummyPasswordHash;

    public AdminAuthServiceImpl(
            AdminUserMapper adminUserMapper,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            AdminRefreshSessionService refreshSessionService,
            TokenProperties tokenProperties,
            AdminAuthProperties adminAuthProperties,
            AdminAuthRateLimitService rateLimitService) {
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshSessionService = refreshSessionService;
        this.tokenProperties = tokenProperties;
        this.adminAuthProperties = adminAuthProperties;
        this.rateLimitService = rateLimitService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public AdminLoginCookieDto login(String username, String rawPassword, String clientIp) {
        validatePasswordPolicy(rawPassword);

        String trimmedUsername = username.trim();
        String usernameNorm = normalizeUsernameForRateLimit(trimmedUsername);

        if (adminAuthProperties.isAdminRateLimitEnabled()
                && !rateLimitService.loginAllowed(clientIp, usernameNorm)) {
            throw rateLimited();
        }

        AdminUserEntity user =
                adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
                        .eq(AdminUserEntity::getUsername, trimmedUsername));

        if (user == null || user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            // 结果丢弃：仅消耗与真实比对相近的 CPU 时间
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            recordLoginFailureIfEnabled(clientIp, usernameNorm);
            denyBadCredential(trimmedUsername);
        }

        String status = user.getStatus() == null ? "" : user.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!AdminAuthConstants.ACTIVE_ADMIN_ACCOUNT_STATUS.equals(status)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), KEY_ACCOUNT_DISABLED);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            recordLoginFailureIfEnabled(clientIp, usernameNorm);
            denyBadCredential(user.getUsername());
        }

        if (adminAuthProperties.isAdminRateLimitEnabled()) {
            rateLimitService.resetLoginFailures(clientIp, usernameNorm);
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
                null,
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
    public AdminRefreshCookieDto refresh(Optional<String> refreshTokenPlainOptional, String clientIp) {
        if (adminAuthProperties.isAdminRateLimitEnabled() && !rateLimitService.refreshAllowed(clientIp)) {
            throw rateLimited();
        }

        String refreshPlain =
                refreshTokenPlainOptional.filter(s -> !s.isBlank()).orElseThrow(() -> unauthorizedRefresh(clientIp));

        RotateRefreshDto rotated = refreshSessionService.rotate(refreshPlain);
        return switch (rotated) {
            case RotateRefreshDto.Invalid ignored -> throw unauthorizedRefresh(clientIp);
            case RotateRefreshDto.ReplayRevoked ignored -> {
                log.warn(
                        "auth security refresh_replay_detected requestId={} realm=admin sessionRevoked=true",
                        MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID));
                throw unauthorizedRefresh(clientIp);
            }
            case RotateRefreshDto.Rotated ok -> {
                String accessToken = tokenService.createAccessToken(new IssuedAccessTokenDto(
                        TokenAudience.ADMIN,
                        String.valueOf(ok.adminUserId()),
                        ok.username(),
                        ok.sessionId(),
                        null,
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
    public AdminRegisterResponseDto register(AdminRegisterRequestDto request, String clientIp) {
        // 成功失败都计：放在通道判定之前，关闭通道的探测请求也计入 IP 桶
        if (adminAuthProperties.isAdminRateLimitEnabled() && !rateLimitService.registerAllowed(clientIp)) {
            throw rateLimited();
        }

        boolean usedOpenRegistration = ensureRegistrationChannelOpen();

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

        if (usedOpenRegistration) {
            log.warn(
                    "auth security open_registration_used requestId={} realm=admin userId={}",
                    MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID),
                    entity.getId());
        }

        return new AdminRegisterResponseDto(entity.getId(), trimmedUsername);
    }

    private void recordLoginFailureIfEnabled(String clientIp, String usernameNorm) {
        if (adminAuthProperties.isAdminRateLimitEnabled()) {
            rateLimitService.recordLoginFailure(clientIp, usernameNorm);
        }
    }

    private BusinessException unauthorizedRefresh(String clientIp) {
        if (adminAuthProperties.isAdminRateLimitEnabled()) {
            rateLimitService.recordRefreshFailure(clientIp);
        }
        return new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), KEY_REFRESH_INVALID);
    }

    private BusinessException rateLimited() {
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS.getCode(), KEY_RATE_LIMITED);
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

    /**
     * @return {@code true} 表示走的是非 bootstrap 的开放注册路径（用于使用告警）
     */
    private boolean ensureRegistrationChannelOpen() {
        long activeCount = adminUserMapper.selectCount(new LambdaQueryWrapper<>());
        boolean bootstrap = adminAuthProperties.isAdminBootstrapRegistrationEnabled() && activeCount == 0;
        boolean open = adminAuthProperties.isAdminOpenRegistrationEnabled();
        if (!bootstrap && !open) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), KEY_REGISTER_DISABLED);
        }
        return open && !bootstrap;
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

    private static String normalizeUsernameForRateLimit(String trimmedUsername) {
        return trimmedUsername.toLowerCase(Locale.ROOT);
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
