package com.mywebsite.admin;

import com.mywebsite.common.env.LocalDotenvSupport;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.mywebsite")
// MyBatis 自动扫描只覆盖启动类所在包（com.mywebsite.admin），agent-core 的 Mapper 不会被带上；
// 用量记账的 AgentAiUsageMapper 在 agent-core（admin 的试聊与用量端点共用，Issue #140），须显式登记。
// 显式 @MapperScan 存在时自动扫描退场，故 admin 自家包一并列全。
@MapperScan({"com.mywebsite.admin.mapper", "com.mywebsite.agent.mapper"})
public class AdminApplication {

    public static void main(String[] args) {
        LocalDotenvSupport.tryLoadNearestEnv();
        SpringApplication.run(AdminApplication.class, args);
    }
}
