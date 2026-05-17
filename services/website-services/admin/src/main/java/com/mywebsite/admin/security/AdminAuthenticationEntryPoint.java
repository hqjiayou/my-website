package com.mywebsite.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.common.api.ApiResponse;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.infra.service.LocalizedMessageService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    private final LocalizedMessageService localizedMessageService;

    public AdminAuthenticationEntryPoint(ObjectMapper objectMapper, LocalizedMessageService localizedMessageService) {
        this.objectMapper = objectMapper;
        this.localizedMessageService = localizedMessageService;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        String message = localizedMessageService.getMessage(request, ErrorCode.UNAUTHORIZED.getMessageKey());
        ApiResponse<Void> body = ApiResponse.fail(ErrorCode.UNAUTHORIZED.getCode(), message);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
