package com.teddynu.oauth2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 설정
 *
 * StringRedisTemplate을 사용하여 Refresh Token과 Access Token 블랙리스트를 관리한다.
 * - Refresh Token: "RT:{userId}" 키로 저장
 * - Blacklist: "BL:{accessToken}" 키로 저장
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
