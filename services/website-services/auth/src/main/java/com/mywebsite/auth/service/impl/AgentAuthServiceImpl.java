package com.mywebsite.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.auth.constant.AgentAuthConstants;
import com.mywebsite.auth.dto.IssuedAccessTokenDto;
import com.mywebsite.auth.dto.AgentLoginCookieDto;
import com.mywebsite.auth.dto.AgentLoginResponseDto;
import com.mywebsite.auth.dto.AgentRefreshCookieDto;
import com.mywebsite.auth.dto.AgentRefreshResponseDto;
import com.mywebsite.auth.dto.AgentRotateRefreshDto;
import com.mywebsite.auth.entity.AgentEntity;
import com.mywebsite.auth.entity.AgentUserEntity;
import com.mywebsite.auth.mapper.AgentMapper;
import com.mywebsite.auth.mapper.AgentUserMapper;
import com.mywebsite.auth.properties.AgentAuthProperties;
import com.mywebsite.auth.service.AgentAuthRateLimitService;
import com.mywebsite.auth.service.AgentAuthService;
import com.mywebsite.auth.service.AgentRefreshSessionService;
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
public class AgentAuthServiceImpl implements AgentAuthService {

    private static final Logger log = LoggerFactory.getLogger(AgentAuthServiceImpl.class);

    private static final String KEY_LOGIN_FAILED = "agent.auth.login.failed";

    private static final String KEY_REFRESH_INVALID = "agent.auth.refresh.invalid";

    private static final String KEY_ACCOUNT_DISABLED = "agent.auth.account.disabled";

    private static final String KEY_PASSWORD_TOO_LONG = "agent.auth.password.too-long";

    private static final String KEY_RATE_LIMITED = "agent.auth.rate-limited";

    private final AgentMapper agentMapper;

    private final AgentUserMapper agentUserMapper;

    private final PasswordEncoder passwordEncoder;

    private final TokenService tokenService;

    private final AgentRefreshSessionService refreshSessionService;

    private final TokenProperties tokenProperties;

    private final AgentAuthProperties agentAuthProperties;

    private final AgentAuthRateLimitService rateLimitService;

    /**
     * 启动时用注入的 encoder 生成一次；未知用户名 / 未知站点路径也走 matches，抹平枚举时间侧信道。
     */
    private final String dummyPasswordHash;

    public AgentAuthServiceImpl(
            AgentMapper agentMapper,
            AgentUserMapper agentUserMapper,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            AgentRefreshSessionService refreshSessionService,
            TokenProperties tokenProperties,
            AgentAuthProperties agentAuthProperties,
            AgentAuthRateLimitService rateLimitService) {
        this.agentMapper = agentMapper;
        this.agentUserMapper = agentUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshSessionService = refreshSessionService;
        this.tokenProperties = tokenProperties;
        this.agentAuthProperties = agentAuthProperties;
        this.rateLimitService = rateLimitService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public AgentLoginCookieDto login(String agentCode, String username, String rawPassword, String clientIp) {
        validatePasswordPolicy(rawPassword);

        String trimmedAgentCode = agentCode.trim();
        String trimmedUsername = username.trim();
        String agentCodeNorm = normalizeForRateLimit(trimmedAgentCode);
        String usernameNorm = normalizeForRateLimit(trimmedUsername);

        if (agentAuthProperties.isAgentRateLimitEnabled()
                && !rateLimitService.loginAllowed(clientIp, agentCodeNorm, usernameNorm)) {
            throw rateLimited();
        }

        AgentEntity agent = agentMapper.selectOne(new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getAgentCode, trimmedAgentCode));

        String agentStatus = agent == null || agent.getStatus() == null
                ? ""
                : agent.getStatus().trim().toUpperCase(Locale.ROOT);
        if (agent == null || !AgentAuthConstants.ACTIVE_SITE_STATUS.equals(agentStatus)) {
            // 站点不存在 / 非 ACTIVE：与错密同路径，避免枚举站点编码
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            recordLoginFailureIfEnabled(clientIp, agentCodeNorm, usernameNorm);
            denyBadCredential(trimmedAgentCode, trimmedUsername);
        }

        AgentUserEntity user = agentUserMapper.selectOne(new LambdaQueryWrapper<AgentUserEntity>()
                .eq(AgentUserEntity::getAgentId, agent.getId())
                .eq(AgentUserEntity::getUsername, trimmedUsername));

        if (user == null || user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            passwordEncoder.matches(rawPassword, dummyPasswordHash);
            recordLoginFailureIfEnabled(clientIp, agentCodeNorm, usernameNorm);
            denyBadCredential(trimmedAgentCode, trimmedUsername);
        }

        // 先校验密码：禁用账号 + 错密与未知用户同为 401，避免枚举禁用用户
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            recordLoginFailureIfEnabled(clientIp, agentCodeNorm, usernameNorm);
            denyBadCredential(trimmedAgentCode, user.getUsername());
        }

        String userStatus = user.getStatus() == null ? "" : user.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!AgentAuthConstants.ACTIVE_SITE_USER_STATUS.equals(userStatus)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), KEY_ACCOUNT_DISABLED);
        }

        if (agentAuthProperties.isAgentRateLimitEnabled()) {
            rateLimitService.resetLoginFailures(clientIp, agentCodeNorm, usernameNorm);
        }

        user.setLastLoginAt(OffsetDateTime.now(ZoneOffset.UTC));
        agentUserMapper.updateById(user);

        String sessionId = UUID.randomUUID().toString();
        String refreshPlain = RefreshTokenUtils.generateOpaque();
        Instant expireAt = Instant.now().plus(tokenProperties.refreshTokenTtl());

        refreshSessionService.bindNewSession(
                user.getId(), agent.getId(), user.getUsername(), sessionId, refreshPlain, expireAt);

        String accessToken = tokenService.createAccessToken(new IssuedAccessTokenDto(
                TokenAudience.AGENT,
                String.valueOf(user.getId()),
                user.getUsername(),
                sessionId,
                agent.getId(),
                Set.of()));

        long expiresInSec = tokenProperties.accessTokenTtl().toSeconds();
        AgentLoginResponseDto body = new AgentLoginResponseDto(accessToken, expiresInSec);

        log.info(
                "auth security login_success requestId={} realm=agent userId={} agentId={}",
                MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID),
                user.getId(),
                agent.getId());

        return new AgentLoginCookieDto(body, refreshPlain, expireAt);
    }

    @Override
    public AgentRefreshCookieDto refresh(Optional<String> refreshTokenPlainOptional, String clientIp) {
        if (agentAuthProperties.isAgentRateLimitEnabled() && !rateLimitService.refreshAllowed(clientIp)) {
            throw rateLimited();
        }

        String refreshPlain =
                refreshTokenPlainOptional.filter(s -> !s.isBlank()).orElseThrow(() -> unauthorizedRefresh(clientIp));

        AgentRotateRefreshDto rotated = refreshSessionService.rotate(refreshPlain);
        return switch (rotated) {
            case AgentRotateRefreshDto.Invalid ignored -> throw unauthorizedRefresh(clientIp);
            case AgentRotateRefreshDto.ReplayRevoked ignored -> {
                log.warn(
                        "auth security refresh_replay_detected requestId={} realm=agent sessionRevoked=true",
                        MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID));
                throw unauthorizedRefresh(clientIp);
            }
            case AgentRotateRefreshDto.Rotated ok -> {
                // 旋转后立刻校验库状态：非 ACTIVE 吊销会话，禁止停用账号续命
                if (!isRefreshPrincipalActive(ok.agentUserId(), ok.agentId())) {
                    refreshSessionService.revokeByRefreshCookie(Optional.of(ok.newRefreshPlain()));
                    throw unauthorizedRefresh(clientIp);
                }
                String accessToken = tokenService.createAccessToken(new IssuedAccessTokenDto(
                        TokenAudience.AGENT,
                        String.valueOf(ok.agentUserId()),
                        ok.username(),
                        ok.sessionId(),
                        ok.agentId(),
                        Set.of()));
                long expiresInSec = tokenProperties.accessTokenTtl().toSeconds();
                AgentRefreshResponseDto body = new AgentRefreshResponseDto(accessToken, expiresInSec);
                log.info(
                        "auth security refresh_success requestId={} realm=agent userId={} agentId={}",
                        MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID),
                        ok.agentUserId(),
                        ok.agentId());
                yield new AgentRefreshCookieDto(body, ok.newRefreshPlain(), ok.expireAt());
            }
        };
    }

    /** Refresh 主体仍可用：site_user 与 agent 均为 ACTIVE。 */
    private boolean isRefreshPrincipalActive(long agentUserId, long agentId) {
        AgentUserEntity user = agentUserMapper.selectById(agentUserId);
        if (user == null) {
            return false;
        }
        String userStatus = user.getStatus() == null ? "" : user.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!AgentAuthConstants.ACTIVE_SITE_USER_STATUS.equals(userStatus)) {
            return false;
        }
        AgentEntity agent = agentMapper.selectById(agentId);
        if (agent == null) {
            return false;
        }
        String agentStatus = agent.getStatus() == null ? "" : agent.getStatus().trim().toUpperCase(Locale.ROOT);
        return AgentAuthConstants.ACTIVE_SITE_STATUS.equals(agentStatus);
    }

    @Override
    public void logout(Optional<String> refreshTokenPlainOptional) {
        refreshSessionService.revokeByRefreshCookie(refreshTokenPlainOptional);
    }

    private void recordLoginFailureIfEnabled(String clientIp, String agentCodeNorm, String usernameNorm) {
        if (agentAuthProperties.isAgentRateLimitEnabled()) {
            rateLimitService.recordLoginFailure(clientIp, agentCodeNorm, usernameNorm);
        }
    }

    private BusinessException unauthorizedRefresh(String clientIp) {
        if (agentAuthProperties.isAgentRateLimitEnabled()) {
            rateLimitService.recordRefreshFailure(clientIp);
        }
        return new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), KEY_REFRESH_INVALID);
    }

    private BusinessException rateLimited() {
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS.getCode(), KEY_RATE_LIMITED);
    }

    private void denyBadCredential(String agentCode, String attemptedUsername) {
        log.warn(
                "auth security login_failed requestId={} realm=agent agentCode={} username={}",
                MDC.get(HttpRequestCorrelation.MDC_REQUEST_ID),
                agentCode,
                attemptedUsername);
        throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), KEY_LOGIN_FAILED);
    }

    private void validatePasswordPolicy(String rawPassword) {
        int maxLen = agentAuthProperties.getAgentLoginMaxPasswordLength();
        if (rawPassword != null && rawPassword.length() > maxLen) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), KEY_PASSWORD_TOO_LONG);
        }
    }

    private static String normalizeForRateLimit(String trimmed) {
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
