package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mywebsite.admin.dto.AdminLoginCookieDto;
import com.mywebsite.admin.dto.AdminRefreshCookieDto;
import com.mywebsite.admin.dto.RotateRefreshDto;
import com.mywebsite.admin.entity.AdminUserEntity;
import com.mywebsite.admin.mapper.AdminUserMapper;
import com.mywebsite.admin.properties.AdminAuthProperties;
import com.mywebsite.admin.service.AdminRefreshSessionService;
import com.mywebsite.auth.config.JwtProperties;
import com.mywebsite.auth.token.IssuedAccessToken;
import com.mywebsite.auth.token.TokenAudience;
import com.mywebsite.auth.token.TokenService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@SuppressWarnings({"unchecked"})
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthServiceImplTest {

    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private AdminRefreshSessionService refreshSessionService;

    @Mock
    private AdminAuthProperties adminAuthProperties;

    private JwtProperties jwtProperties;

    private AdminAuthServiceImpl adminAuthService;

    private AdminUserEntity activeUser;

    @BeforeEach
    void baseline() {
        jwtProperties = new JwtProperties();
        jwtProperties.setAccessTokenTtl(Duration.ofMinutes(15));

        adminAuthService =
                new AdminAuthServiceImpl(
                        adminUserMapper, passwordEncoder, tokenService, refreshSessionService, jwtProperties, adminAuthProperties);

        activeUser = new AdminUserEntity();
        activeUser.setId(9L);
        activeUser.setUsername("u");
        activeUser.setDisplayName("U");
        activeUser.setStatus("ACTIVE");
        activeUser.setPasswordHash("$bcrypt");
        activeUser.setCreatedAt(OffsetDateTime.now());
        activeUser.setUpdatedAt(OffsetDateTime.now());

        when(adminAuthProperties.getRefreshTokenTtl()).thenReturn(Duration.ofDays(7));
        when(adminAuthProperties.getAdminLoginMaxPasswordLength()).thenReturn(128);
        when(adminUserMapper.selectOne(any(Wrapper.class))).thenReturn(activeUser);
        when(passwordEncoder.matches(eq("pwd"), eq("$bcrypt"))).thenReturn(true);
        when(tokenService.createAccessToken(any())).thenAnswer(inv -> "jwt-" + inv.getArguments()[0]);
    }

    @Test
    void loginBindsRefreshSessionIssuesAccessViaTokenService() {
        AdminLoginCookieDto out = adminAuthService.login("u", "pwd");

        assertThat(out.loginResponse().accessToken()).startsWith("jwt-");
        assertThat(out.refreshPlain()).isNotBlank();

        ArgumentCaptor<IssuedAccessToken> issuance = ArgumentCaptor.forClass(IssuedAccessToken.class);
        verify(tokenService).createAccessToken(issuance.capture());
        assertThat(issuance.getValue().audience()).isEqualTo(TokenAudience.ADMIN);
        assertThat(issuance.getValue().subjectUserId()).isEqualTo("9");
        assertThat(issuance.getValue().username()).isEqualTo("u");

        ArgumentCaptor<Instant> expCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshSessionService)
                .bindNewSession(eq(9L), eq("u"), anyString(), anyString(), expCaptor.capture());
        assertThat(expCaptor.getValue()).isAfter(Instant.now());
    }

    @Test
    void refreshOnSuccessfulRotationIssuesNewAccessWithSameSessionId() {
        when(refreshSessionService.rotate("opaque-a"))
                .thenReturn(new RotateRefreshDto.Rotated(9L, "u", "sid-z", "opaque-b", Instant.now().plusSeconds(3600)));

        AdminRefreshCookieDto dto = adminAuthService.refresh(Optional.of("opaque-a"));

        assertThat(dto.refreshPlain()).isEqualTo("opaque-b");

        ArgumentCaptor<IssuedAccessToken> issuance = ArgumentCaptor.forClass(IssuedAccessToken.class);
        verify(tokenService).createAccessToken(issuance.capture());
        assertThat(issuance.getValue().sessionId()).isEqualTo("sid-z");
    }

    @Test
    void refreshOnReplayRevokesAsUnauthorized() {
        when(refreshSessionService.rotate(anyString())).thenReturn(new RotateRefreshDto.ReplayRevoked());

        assertThatThrownBy(() -> adminAuthService.refresh(Optional.of("replayed")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void logoutDelegatesRevokeAndAcceptsAbsentCookiePlain() {
        adminAuthService.logout(Optional.empty());
        verify(refreshSessionService).revokeByRefreshCookie(Optional.empty());
    }

    @Test
    void loginDoesNotLeakEnumerationForMissingUser() {
        when(adminUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> adminAuthService.login("ghost", "pwd"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED.getCode());

        verify(refreshSessionService, never())
                .bindNewSession(anyLong(), anyString(), anyString(), anyString(), any(Instant.class));
    }
}
