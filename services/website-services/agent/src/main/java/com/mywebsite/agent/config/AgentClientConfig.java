package com.mywebsite.agent.config;

import com.mywebsite.agent.properties.AgentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentClientConfig {}
