package com.mywebsite.infra.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

public interface LocalizedMessageService {

    String getMessage(String messageKey, Object... args);

    String getMessage(String messageKey, Locale locale, Object... args);

    String getMessage(HttpServletRequest request, String messageKey, Object... args);
}
