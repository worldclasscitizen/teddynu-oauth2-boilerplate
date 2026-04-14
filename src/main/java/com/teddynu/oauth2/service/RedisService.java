package com.teddynu.oauth2.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 기반 토큰 관리 서비스.
 *
 * 저장 구조:
 * - Refresh Token:  key = "RT:{userId}",       value = refreshToken,  TTL = 7일
 * - Blacklist:      key = "BL:{accessToken}",  value = "true",        TTL = AT 잔여 만료시간
 *
 * 로그아웃 시:
 * 1) Access Token을 블랙리스트에 등록 (남은 만료시간만큼 TTL 설정)
 * 2) Refresh Token 삭제
 */
@Service
public class RedisService {

    private static final String REFRESH_TOKEN_PREFIX = "RT:";
    private static final String BLACKLIST_PREFIX = "BL:";

    private final StringRedisTemplate redisTemplate;

    public RedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Refresh Token 저장 */
    public void saveRefreshToken(Long userId, String refreshToken, Duration expiry) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + userId,
                refreshToken,
                expiry
        );
    }

    /** Refresh Token 조회 */
    public String getRefreshToken(Long userId) {
        return redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userId);
    }

    /** Refresh Token 삭제 */
    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
    }

    /** Access Token 블랙리스트 등록 */
    public void blacklistAccessToken(String token, Duration expiry) {
        redisTemplate.opsForValue().set(
                BLACKLIST_PREFIX + token,
                "true",
                expiry
        );
    }

    /** Access Token 블랙리스트 여부 확인 */
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }
}
