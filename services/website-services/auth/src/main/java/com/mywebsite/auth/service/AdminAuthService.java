package com.mywebsite.auth.service;

import com.mywebsite.auth.dto.AdminLoginCookieDto;
import com.mywebsite.auth.dto.AdminRefreshCookieDto;
import com.mywebsite.auth.dto.AdminRegisterRequestDto;
import com.mywebsite.auth.dto.AdminRegisterResponseDto;
import java.util.Optional;

public interface AdminAuthService {

    /** {@code clientIp} 由 controller 解析（见 ClientIpUtils），仅用于限流维度。 */
    AdminLoginCookieDto login(String username, String rawPassword, String clientIp);

    AdminRefreshCookieDto refresh(Optional<String> refreshTokenPlainOptional, String clientIp);

    void logout(Optional<String> refreshTokenPlainOptional);

    AdminRegisterResponseDto register(AdminRegisterRequestDto request, String clientIp);
}
