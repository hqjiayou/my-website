package com.mywebsite.site.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.common.api.HttpMessageBody;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.infra.security.oauth2.OauthResourceServerUnauthorizedSupport;
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
public class SiteAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    private final LocalizedMessageService localizedMessageService;

    public SiteAuthenticationEntryPoint(ObjectMapper objectMapper, LocalizedMessageService localizedMessageService) {
        this.objectMapper = objectMapper;
        this.localizedMessageService = localizedMessageService;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        boolean invalidBearer = OauthResourceServerUnauthorizedSupport.isBearerTokenInvalidCause(authException);
        String messageKey = invalidBearer ? "auth.token.invalid" : ErrorCode.UNAUTHORIZED.getMessageKey();

        String message = localizedMessageService.getMessage(request, messageKey);
        HttpMessageBody<Void> body = new HttpMessageBody<>(ErrorCode.UNAUTHORIZED.getCode(), message, null);

        OauthResourceServerUnauthorizedSupport.addWwwAuthenticateIfBearerInvalid(response, authException);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
