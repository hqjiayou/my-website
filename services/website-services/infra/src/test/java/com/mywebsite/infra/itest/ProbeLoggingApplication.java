package com.mywebsite.infra.itest;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.mywebsite.infra.itest.probe.ProbePingController;
import com.mywebsite.infra.web.config.RequestLoggingWebConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(
        exclude = {
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
        })
@Import({RequestLoggingWebConfiguration.class, ProbePingController.class})
public class ProbeLoggingApplication {}
