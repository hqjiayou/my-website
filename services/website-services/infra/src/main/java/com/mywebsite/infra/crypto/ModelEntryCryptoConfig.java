package com.mywebsite.infra.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ModelEntrySecretProperties.class)
public class ModelEntryCryptoConfig {}
