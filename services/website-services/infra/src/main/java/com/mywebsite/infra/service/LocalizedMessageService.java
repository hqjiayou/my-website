package com.mywebsite.infra.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

public interface LocalizedMessageService {

    String getMessage(String messageKey, Object... args);

    String getMessage(String messageKey, Locale locale, Object... args);

    String getMessage(HttpServletRequest request, String messageKey, Object... args);

    /** 当前请求已解析的 BCP 47 标签；无请求时用 app.i18n 默认语言。 */
    String currentLanguageTag();
}
