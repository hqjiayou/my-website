package com.mywebsite.infra.config;

import com.mywebsite.infra.properties.I18nProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/** 仅依赖 {@code Accept-Language} 的 Locale 解析；默认与回退均由 {@link I18nProperties} 绑定。 */
@Configuration
@EnableConfigurationProperties(I18nProperties.class)
@ConditionalOnClass(LocaleResolver.class)
public class I18nConfig {

    @Bean
    LocaleResolver localeResolver(I18nProperties i18nProperties) {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(i18nProperties.resolvedDefaultLocale());
        resolver.setSupportedLocales(i18nProperties.resolvedSupportedLocales());
        return resolver;
    }
}
