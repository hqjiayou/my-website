package com.mywebsite.site.config;

import com.mywebsite.site.properties.SiteAiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SiteAiProperties.class)
public class SiteAiConfig {}
