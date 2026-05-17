package com.mywebsite.infra.properties;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 国际化应用级配置（BCP 47 语言标签字符串，如 {@code ja-JP}）。 */
@ConfigurationProperties(prefix = "app.i18n")
public class I18nProperties {

    /**
     * 系统缺省回退语言；应放在各模块 {@code messages.properties}（日文）中。
     */
    private String defaultLocale = "ja-JP";

    private List<String> supportedLocales = List.of("ja-JP", "zh-CN", "en-US");

    @PostConstruct
    void validateLocaleTags() {
        requireValidBcp47(defaultLocale, "default-locale");
        if (supportedLocales == null || supportedLocales.isEmpty()) {
            throw new IllegalStateException("app.i18n.supported-locales must not be empty");
        }
        for (String tag : supportedLocales) {
            requireValidBcp47(tag, "supported-locales");
        }
    }

    private static void requireValidBcp47(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("app.i18n." + field + " must not be blank");
        }
        String tag = raw.replace('_', '-').trim();
        Locale locale = Locale.forLanguageTag(tag);
        if (locale.getLanguage().isEmpty()) {
            throw new IllegalStateException("Illegal BCP 47 locale tag in app.i18n." + field + ": " + raw);
        }
    }

    public Locale resolvedDefaultLocale() {
        return Locale.forLanguageTag(defaultLocale.replace('_', '-'));
    }

    public List<Locale> resolvedSupportedLocales() {
        List<Locale> locales = new ArrayList<>();
        for (String tag : supportedLocales) {
            locales.add(Locale.forLanguageTag(tag.replace('_', '-')));
        }
        return Collections.unmodifiableList(locales);
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public List<String> getSupportedLocales() {
        return supportedLocales;
    }

    public void setSupportedLocales(List<String> supportedLocales) {
        this.supportedLocales = supportedLocales == null ? List.of() : List.copyOf(supportedLocales);
    }
}
