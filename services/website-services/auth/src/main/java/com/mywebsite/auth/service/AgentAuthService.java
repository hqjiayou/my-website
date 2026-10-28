package com.mywebsite.auth.service;

import com.mywebsite.auth.dto.AgentLoginCookieDto;
import com.mywebsite.auth.dto.AgentRefreshCookieDto;
import java.util.Optional;

public interface AgentAuthService {

    /** {@code clientIp} 由 controller 解析（见 ClientIpUtils），仅用于限流维度。 */
    AgentLoginCookieDto login(String agentCode, String username, String rawPassword, String clientIp);

    AgentRefreshCookieDto refresh(Optional<String> refreshTokenPlainOptional, String clientIp);

    void logout(Optional<String> refreshTokenPlainOptional);
}
