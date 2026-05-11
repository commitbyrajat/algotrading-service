package com.algotrading.app.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;


/**
 * Redis configuration.
 *
 * <p>Uses {@link StringRedisTemplate} – sufficient for storing plain string
 * tokens. All keys and values are UTF-8 strings, keeping Redis inspection
 * trivial with {@code redis-cli}.</p>
 */
@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate is configured automatically by Spring Boot's
     * auto-configuration using the {@code spring.data.redis.*} properties.
     * This bean is declared explicitly to make the dependency visible and
     * allow easy override in tests.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}