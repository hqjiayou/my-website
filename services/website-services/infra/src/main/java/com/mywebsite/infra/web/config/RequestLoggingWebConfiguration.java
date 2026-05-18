package com.mywebsite.infra.web.config;

import com.mywebsite.infra.web.filter.RequestLoggingFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * 为依赖 {@code infra} 的 Servlet Web 应用注册访问日志与 RequestId 透传；WebFlux（如 Gateway）不会加载。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequestLoggingWebConfiguration {

    @Bean
    FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(Environment environment) {
        FilterRegistrationBean<RequestLoggingFilter> bean =
                new FilterRegistrationBean<>(new RequestLoggingFilter(environment));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
