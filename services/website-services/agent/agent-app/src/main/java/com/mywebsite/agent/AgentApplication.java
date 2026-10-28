package com.mywebsite.agent;

import com.mywebsite.common.env.LocalDotenvSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.mywebsite")
public class AgentApplication {

    public static void main(String[] args) {
        LocalDotenvSupport.tryLoadNearestEnv();
        SpringApplication.run(AgentApplication.class, args);
    }
}
