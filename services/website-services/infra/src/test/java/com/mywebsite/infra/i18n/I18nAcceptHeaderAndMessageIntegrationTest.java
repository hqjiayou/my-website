package com.mywebsite.infra.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.mywebsite.infra.config.I18nConfig;
import com.mywebsite.infra.properties.I18nProperties;
import com.mywebsite.infra.service.LocalizedMessageService;
import com.mywebsite.infra.service.impl.LocalizedMessageServiceImpl;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.LocaleResolver;

/** LocaleResolver + LocalizedMessageService（Accept-Language、日文 messages.properties 根回退）。 */
@SpringBootTest(
        classes = I18nAcceptHeaderAndMessageIntegrationTest.TestHarness.class,
        properties = {
            "app.i18n.default-locale=ja-JP",
            "app.i18n.supported-locales[0]=ja-JP",
            "app.i18n.supported-locales[1]=zh-CN",
            "app.i18n.supported-locales[2]=en-US",
        })
class I18nAcceptHeaderAndMessageIntegrationTest {

    @Autowired
    private LocaleResolver localeResolver;

    @Autowired
    private LocalizedMessageService localizedMessageService;

    @Test
    void noAcceptLanguageHeaderUsesConfiguredDefaultJapanese() {
        assertThat(localeResolver.resolveLocale(new MockHttpServletRequest()))
                .isEqualTo(Locale.forLanguageTag("ja-JP"));
    }

    @Test
    void acceptLanguageJaJpUsesJapaneseCatalog() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("Accept-Language", "ja-JP");
        assertThat(localeResolver.resolveLocale(r)).isEqualTo(Locale.forLanguageTag("ja-JP"));
    }

    @Test
    void acceptLanguageZhCnResolvesChinese() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("Accept-Language", "zh-CN");
        assertThat(localeResolver.resolveLocale(r)).isEqualTo(Locale.forLanguageTag("zh-CN"));
    }

    @Test
    void acceptLanguageEnUsResolvesEnglishUs() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("Accept-Language", "en-US");
        assertThat(localeResolver.resolveLocale(r)).isEqualTo(Locale.forLanguageTag("en-US"));
    }

    @Test
    void unsupportedKoKrFallsBackToDefaultJapaneseLocale() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("Accept-Language", "ko-KR");
        assertThat(localeResolver.resolveLocale(r)).isEqualTo(Locale.forLanguageTag("ja-JP"));
    }

    @Test
    void resolvesExistingKeyAccordingToResolvedRequestLocaleHeader() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("Accept-Language", "zh-CN");
        assertThat(localizedMessageService.getMessage(r, "localized.zh")).isEqualTo("简体中文条目");
    }

    @Test
    void resolvesEnglishWhenLocaleIsEnUs() {
        assertThat(localizedMessageService.getMessage("localized.en", Locale.forLanguageTag("en-US")))
                .isEqualTo("English entry");
    }

    /** zh 分区缺少 localized.en：回退日文根 bundle。 */
    @Test
    void missingKeyForTargetLocaleFallsBackToJapaneseRoot() {
        assertThat(localizedMessageService.getMessage("localized.en", Locale.forLanguageTag("zh-CN")))
                .isEqualTo("これは JA 側の占位（en 側で上書き）");
    }

    @Test
    void keyOnlyDefinedInJapaneseRootStillRetrievableForChineseLocale() {
        assertThat(localizedMessageService.getMessage("only.ja", Locale.forLanguageTag("zh-CN")))
                .isEqualTo("JA の値です");
    }

    @Test
    void completelyUnknownKeyReturnsKeyItself() {
        assertThat(localizedMessageService.getMessage(
                        "totally.nonexistent.message.key.xyz", Locale.forLanguageTag("ja-JP")))
                .isEqualTo("totally.nonexistent.message.key.xyz");
    }

    @Configuration
    @Import(I18nConfig.class)
    static class TestHarness {

        @Bean
        ReloadableResourceBundleMessageSource messageSource() {
            ReloadableResourceBundleMessageSource rs = new ReloadableResourceBundleMessageSource();
            rs.setBasenames("classpath:i18n/itest/messages");
            rs.setDefaultEncoding(StandardCharsets.UTF_8.name());
            rs.setFallbackToSystemLocale(false);
            return rs;
        }

        @Bean
        LocalizedMessageService localizedMessageService(
                MessageSource messageSource, I18nProperties i18nProperties, LocaleResolver localeResolver) {
            return new LocalizedMessageServiceImpl(messageSource, i18nProperties, localeResolver);
        }
    }
}
