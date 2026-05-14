package com.mywebsite.infra.storage;

import com.mywebsite.infra.properties.StorageProperties;
import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    @Bean
    public MinioClient minioClient(StorageProperties properties) {
        MinioClient.Builder builder = MinioClient.builder().endpoint(properties.getEndpoint());
        if (StringUtils.hasText(properties.getAccessKey()) && StringUtils.hasText(properties.getSecretKey())) {
            builder.credentials(properties.getAccessKey(), properties.getSecretKey());
        }
        return builder.build();
    }
}
