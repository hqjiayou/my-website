package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminLoginCookieDto;
import com.mywebsite.admin.dto.AdminRefreshCookieDto;
import java.util.Optional;

public interface AdminAuthService {

    AdminLoginCookieDto login(String username, String rawPassword);

    AdminRefreshCookieDto refresh(Optional<String> refreshTokenPlainOptional);

    void logout(Optional<String> refreshTokenPlainOptional);
}
