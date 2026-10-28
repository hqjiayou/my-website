package com.mywebsite.agent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.infra.service.LocalizedMessageService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class AgentAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    private final LocalizedMessageService localizedMessageService;

    public AgentAccessDeniedHandler(ObjectMapper objectMapper, LocalizedMessageService localizedMessageService) {
        this.objectMapper = objectMapper;
        this.localizedMessageService = localizedMessageService;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        String message = localizedMessageService.getMessage(request, ErrorCode.FORBIDDEN.getMessageKey());
        HttpMessageBody<Void> body = new HttpMessageBody<>(ErrorCode.FORBIDDEN.getCode(), message, null);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
