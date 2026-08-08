package com.mywebsite.admin.config;

import com.mywebsite.admin.properties.AdminMediaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AdminMediaProperties.class)
public class AdminMediaConfig {}
