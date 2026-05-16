package com.mywebsite.admin;

import com.mywebsite.common.env.LocalDotenvSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.mywebsite")
public class AdminApplication {

    public static void main(String[] args) {
        LocalDotenvSupport.tryLoadNearestEnv();
        SpringApplication.run(AdminApplication.class, args);
    }
}
