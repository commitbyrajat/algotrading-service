package com.algotrading.app.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PositionSizingProperties.class)
public class PositionSizingConfig {
}
