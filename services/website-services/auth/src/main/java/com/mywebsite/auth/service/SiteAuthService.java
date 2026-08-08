package com.mywebsite.auth.service;

import com.mywebsite.auth.dto.SiteLoginCookieDto;
import com.mywebsite.auth.dto.SiteRefreshCookieDto;
import java.util.Optional;

public interface SiteAuthService {

    /** {@code clientIp} 由 controller 解析（见 ClientIpUtils），仅用于限流维度。 */
    SiteLoginCookieDto login(String siteCode, String username, String rawPassword, String clientIp);

    SiteRefreshCookieDto refresh(Optional<String> refreshTokenPlainOptional, String clientIp);

    void logout(Optional<String> refreshTokenPlainOptional);
}
