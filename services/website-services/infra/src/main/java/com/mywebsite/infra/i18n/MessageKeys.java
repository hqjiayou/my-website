package com.mywebsite.infra.i18n;

import java.util.regex.Pattern;

/** 判断 {@link com.mywebsite.common.api.ApiResponse#getMessage()} 是否仍为待本地化的 messageKey。 */
public final class MessageKeys {

    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+$");

    private MessageKeys() {}

    public static boolean looksLikeMessageKey(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return PATTERN.matcher(value).matches();
    }
}
