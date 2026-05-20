package com.mywebsite.auth.service;

import com.mywebsite.auth.dto.AdminLoginCookieDto;
import com.mywebsite.auth.dto.AdminRefreshCookieDto;
import com.mywebsite.auth.dto.AdminRegisterRequestDto;
import com.mywebsite.auth.dto.AdminRegisterResponseDto;
import java.util.Optional;

public interface AdminAuthService {

    AdminLoginCookieDto login(String username, String rawPassword);

    AdminRefreshCookieDto refresh(Optional<String> refreshTokenPlainOptional);

    void logout(Optional<String> refreshTokenPlainOptional);

    AdminRegisterResponseDto register(AdminRegisterRequestDto request);
}
