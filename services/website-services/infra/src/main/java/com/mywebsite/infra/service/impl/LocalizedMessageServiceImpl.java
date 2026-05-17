package com.mywebsite.infra.service.impl;

import com.mywebsite.infra.properties.I18nProperties;
import com.mywebsite.infra.service.LocalizedMessageService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.LocaleResolver;

@Service
public class LocalizedMessageServiceImpl implements LocalizedMessageService {

    private static final Logger log = LoggerFactory.getLogger(LocalizedMessageServiceImpl.class);

    private final MessageSource messageSource;

    private final I18nProperties i18nProperties;

    private final LocaleResolver localeResolver;

    public LocalizedMessageServiceImpl(MessageSource messageSource, I18nProperties i18nProperties, LocaleResolver localeResolver) {
        this.messageSource = messageSource;
        this.i18nProperties = i18nProperties;
        this.localeResolver = localeResolver;
    }

    @Override
    public String getMessage(String messageKey, Object... args) {
        return getMessage(messageKey, currentLocaleWithoutRequestFallback(), args);
    }

    @Override
    public String getMessage(HttpServletRequest request, String messageKey, Object... args) {
        Locale locale = localeResolver.resolveLocale(request);
        return getMessage(messageKey, locale, args);
    }

    @Override
    public String getMessage(String messageKey, Locale locale, Object... args) {
        Locale resolved = locale != null ? locale : i18nProperties.resolvedDefaultLocale();
        try {
            // messages.properties(JA) 作为 bundle 根的 Spring 既定回退，已覆盖 zh/en 缺失 key → 日文
            return messageSource.getMessage(messageKey, args, resolved);
        } catch (NoSuchMessageException ignored) {
            log.warn("Missing message key '{}' for locale {} (fallback message not found)", messageKey, resolved);
            return messageKey;
        }
    }

    private Locale currentLocaleWithoutRequestFallback() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletRequestAttributes) {
                Locale locale = localeResolver.resolveLocale(servletRequestAttributes.getRequest());
                if (locale != null && !locale.getLanguage().isEmpty()) {
                    return locale;
                }
            }
        } catch (IllegalStateException ignored) {
        }
        return i18nProperties.resolvedDefaultLocale();
    }
}
