package com.mywebsite.site;

import com.mywebsite.common.env.LocalDotenvSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.mywebsite")
public class SiteApplication {

    public static void main(String[] args) {
        LocalDotenvSupport.tryLoadNearestEnv();
        SpringApplication.run(SiteApplication.class, args);
    }
}
