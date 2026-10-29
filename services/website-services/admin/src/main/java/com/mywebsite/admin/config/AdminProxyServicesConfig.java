package com.mywebsite.admin.config;

import com.mywebsite.admin.properties.AdminProxyServicesProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminProxyServicesProperties.class)
public class AdminProxyServicesConfig {}
