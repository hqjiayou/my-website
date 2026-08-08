package com.mywebsite.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.auth.constant.SiteAuthConstants;
import com.mywebsite.auth.dto.IssuedAccessTokenDto;
import com.mywebsite.auth.dto.SiteLoginCookieDto;
import com.mywebsite.auth.dto.SiteLoginResponseDto;
import com.mywebsite.auth.dto.SiteRefreshCookieDto;
import com.mywebsite.auth.dto.SiteRefreshResponseDto;
import com.mywebsite.auth.dto.SiteRotateRefreshDto;
import com.mywebsite.auth.entity.SiteEntity;
import com.mywebsite.auth.entity.SiteUserEntity;
import com.mywebsite.auth.mapper.SiteMapper;
import com.mywebsite.auth.mapper.SiteUserMapper;
import com.mywebsite.auth.properties.SiteAuthProperties;
import com.mywebsite.auth.service.SiteAuthRateLimitService;
import com.mywebsite.auth.service.SiteAuthService;
import com.mywebsite.auth.service.SiteRefreshSessionService;
import com.mywebsite.auth.service.TokenService;
import com.mywebsite.auth.utils.RefreshTokenUtils;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.TokenAudience;
import com.mywebsite.common.security.config.TokenProperties;
import com.mywebsite.common.web.HttpRequestCorrelation;
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
import org.springframework.stereotype.Service;

@Service
public class SiteAuthServiceImpl implements SiteAuthService {

    private static final Logger log = LoggerFactory.getLogger(SiteAuthServiceImpl.class);

    private static final String KEY_LOGIN_FAILED = "site.auth.login.failed";

    private static final String KEY_REFRESH_INVALID = "site.auth.refresh.invalid";

    private static final String KEY_ACCOUNT_DISABLED = "site.auth.account.disabled";

    private static final String KEY_PASSWORD_TOO_LONG = "site.auth.password.too-long";

    private static final String KEY_RATE_LIMITED = "site.auth.rate-limited";

    private final SiteMapper siteMapper;

    private final SiteUserMapper siteUserMapper;

    private final PasswordEncoder passwordEncoder;

    private final TokenService tokenService;

    private final SiteRefreshSessionService refreshSessionService;

    private final TokenProperties tokenProperties;

    private final SiteAuthProperties siteAuthProperties;

    private final SiteAuthRateLimitService rateLimitService;

    /**
     * 启动时用注入的 encoder 生成一次；未知用户名 / 未知站点路径也走 matches，抹平枚举时间侧信道。
     */
    private final String dummyPasswordHash;

    public SiteAuthServiceImpl(
            SiteMapper siteMapper,
            SiteUserMapper siteUserMapper,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            SiteRefreshSessionService refreshSessionService,
            TokenProperties tokenProperties,
            SiteAuthProperties siteAuthProperties,
            SiteAuthRateLimitService rateLimitService) {
        this.siteMapper = siteMapper;
        this.siteUserMapper = siteUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshSessionService = refreshSessionService;
        this.tokenProperties = tokenProperties;
        this.siteAuthProperties = siteAuthProperties;
        this.rateLimitService = rateLimitService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public SiteLoginCookieDto login(String siteCode, String username, String rawPassword, String clientIp) {
        validatePasswordPolicy(rawPassword);

        String trimmedSiteCode = siteCode.trim();
        String trimmedUsername = username.trim();
        String siteCodeNorm = normalizeForRateLimit(trimmedSiteCode);
        String usernameNorm = normalizeForRateLimit(trimmedUsername);

        if (siteAuthProperties.isSiteRateLimitEnabled()
                && !rateLimitService.loginAllowed(clientIp, siteCodeNorm, usernameNorm)) {
            throw rateLimited();
        }

        SiteEntity site = siteMapper.selectOne(new LambdaQueryWrapper<SiteEntity>()
                .eq(SiteEntity::getSiteCode, trimmedSiteCode));

        String siteStatus = site == null || site.getStatus() == null
                ? ""
                : site.getStatus().trim().toUpperCase(Locale.ROOT);
        if (site == null || !SiteAuthConstants.ACTIVE_SITE_STATUS.equals(siteStatus)) {
            // 站点不存在 / 非 ACTIVE：与错密同路径，避免枚举站点编码
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            recordLoginFailureIfEnabled(clientIp, siteCodeNorm, usernameNorm);
            denyBadCredential(trimmedSiteCode, trimmedUsername);
        }

        SiteUserEntity user = siteUserMapper.selectOne(new LambdaQueryWrapper<SiteUserEntity>()
                .eq(SiteUserEntity::getSiteId, site.getId())
                .eq(SiteUserEntity::getUsername, trimmedUsername));

        if (user == null || user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            recordLoginFailureIfEnabled(clientIp, siteCodeNorm, usernameNorm);
            denyBadCredential(trimmedSiteCode, trimmedUsername);
        }

        // 先校验密码：禁用账号 + 错密与未知用户同为 401，避免枚举禁用用户
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            recordLoginFailureIfEnabled(clientIp, siteCodeNorm, usernameNorm);
            denyBadCredential(trimmedSiteCode, user.getUsername());
        }

        String userStatus = user.getStatus() == null ? "" : user.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!SiteAuthConstants.ACTIVE_SITE_USER_STATUS.equals(userStatus)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), KEY_ACCOUNT_DISABLED);
        }

        if (siteAuthProperties.isSiteRateLimitEnabled()) {
            rateLimitService.resetLoginFailures(clientIp, siteCodeNorm, usernameNorm);
        }

        user.setLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
        siteUserMapper.updateById(user);

        String sessionId = UUID.randomUUID().toString();
        String refreshPlain = RefreshTokenUtils.generateOpaque();
        Instant expireAt = Instant.now().plus(tokenProperties.refreshTokenTtl());

        refreshSessionService.bindNewSession(
                user.getId(), site.getId(), user.getUsername(), sessionId, refreshPlain, expireAt);

        String accessToken = tokenService.createAccessToken(new IssuedAccessTokenDto(
                TokenAudience.SITE,
                String.valueOf(user.getId()),
                user.getUsername(),
                sessionId,
                site.getId(),
                Set.of()));

        long expiresInSec = tokenProperties.accessTokenTtl().toSeconds();
        SiteLoginResponseDto body = new SiteLoginResponseDto(accessToken, expiresInSec);

        log.info(
                "auth security login_success requestId={} realm=site userId={} siteId={}",
                MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID),
                user.getId(),
                site.getId());

        return new SiteLoginCookieDto(body, refreshPlain, expireAt);
    }

    @Override
    public SiteRefreshCookieDto refresh(Optional<String> refreshTokenPlainOptional, String clientIp) {
        if (siteAuthProperties.isSiteRateLimitEnabled() && !rateLimitService.refreshAllowed(clientIp)) {
            throw rateLimited();
        }

        String refreshPlain =
                refreshTokenPlainOptional.filter(s -> !s.isBlank()).orElseThrow(() -> unauthorizedRefresh(clientIp));

        SiteRotateRefreshDto rotated = refreshSessionService.rotate(refreshPlain);
        return switch (rotated) {
            case SiteRotateRefreshDto.Invalid ignored -> throw unauthorizedRefresh(clientIp);
            case SiteRotateRefreshDto.ReplayRevoked ignored -> {
                log.warn(
                        "auth security refresh_replay_detected requestId={} realm=site sessionRevoked=true",
                        MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID));
                throw unauthorizedRefresh(clientIp);
            }
            case SiteRotateRefreshDto.Rotated ok -> {
                // 旋转后立刻校验库状态：非 ACTIVE 吊销会话，禁止停用账号续命
                if (!isRefreshPrincipalActive(ok.siteUserId(), ok.siteId())) {
                    refreshSessionService.revokeByRefreshCookie(Optional.of(ok.newRefreshPlain()));
                    throw unauthorizedRefresh(clientIp);
                }
                String accessToken = tokenService.createAccessToken(new IssuedAccessTokenDto(
                        TokenAudience.SITE,
                        String.valueOf(ok.siteUserId()),
                        ok.username(),
                        ok.sessionId(),
                        ok.siteId(),
                        Set.of()));
                long expiresInSec = tokenProperties.accessTokenTtl().toSeconds();
                SiteRefreshResponseDto body = new SiteRefreshResponseDto(accessToken, expiresInSec);
                log.info(
                        "auth security refresh_success requestId={} realm=site userId={} siteId={}",
                        MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID),
                        ok.siteUserId(),
                        ok.siteId());
                yield new SiteRefreshCookieDto(body, ok.newRefreshPlain(), ok.expireAt());
            }
        };
    }

    /** Refresh 主体仍可用：site_user 与 site 均为 ACTIVE。 */
    private boolean isRefreshPrincipalActive(long siteUserId, long siteId) {
        SiteUserEntity user = siteUserMapper.selectById(siteUserId);
        if (user == null) {
            return false;
        }
        String userStatus = user.getStatus() == null ? "" : user.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!SiteAuthConstants.ACTIVE_SITE_USER_STATUS.equals(userStatus)) {
            return false;
        }
        SiteEntity site = siteMapper.selectById(siteId);
        if (site == null) {
            return false;
        }
        String siteStatus = site.getStatus() == null ? "" : site.getStatus().trim().toUpperCase(Locale.ROOT);
        return SiteAuthConstants.ACTIVE_SITE_STATUS.equals(siteStatus);
    }

    @Override
    public void logout(Optional<String> refreshTokenPlainOptional) {
        refreshSessionService.revokeByRefreshCookie(refreshTokenPlainOptional);
    }

    private void recordLoginFailureIfEnabled(String clientIp, String siteCodeNorm, String usernameNorm) {
        if (siteAuthProperties.isSiteRateLimitEnabled()) {
            rateLimitService.recordLoginFailure(clientIp, siteCodeNorm, usernameNorm);
        }
    }

    private BusinessException unauthorizedRefresh(String clientIp) {
        if (siteAuthProperties.isSiteRateLimitEnabled()) {
            rateLimitService.recordRefreshFailure(clientIp);
        }
        return new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), KEY_REFRESH_INVALID);
    }

    private BusinessException rateLimited() {
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS.getCode(), KEY_RATE_LIMITED);
    }

    private void denyBadCredential(String siteCode, String attemptedUsername) {
        log.warn(
                "auth security login_failed requestId={} realm=site siteCode={} username={}",
                MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID),
                siteCode,
                attemptedUsername);
        throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), KEY_LOGIN_FAILED);
    }

    private void validatePasswordPolicy(String rawPassword) {
        int maxLen = siteAuthProperties.getSiteLoginMaxPasswordLength();
        if (rawPassword != null && rawPassword.length() > maxLen) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), KEY_PASSWORD_TOO_LONG);
        }
    }

    private static String normalizeForRateLimit(String trimmed) {
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
