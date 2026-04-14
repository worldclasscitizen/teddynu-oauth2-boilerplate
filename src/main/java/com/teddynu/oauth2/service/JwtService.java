package com.teddynu.oauth2.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 토큰 생성 / 검증 서비스.
 *
 * Access Token:  claims에 email, name, provider 포함. 만료: 1시간 (기본)
 * Refresh Token: subject(userId)만 포함.               만료: 7일 (기본)
 *
 * 서명 알고리즘: HS256 (HMAC-SHA256)
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry}") long accessTokenExpiry,
            @Value("${jwt.refresh-token-expiry}") long refreshTokenExpiry) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    /**
     * Access Token 생성.
     * claims: sub(userId), email, name, provider, type=access
     */
    public String generateAccessToken(Long userId, String email, String name, String provider) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(Map.of(
                        "email", email != null ? email : "",
                        "name", name != null ? name : "",
                        "provider", provider,
                        "type", "access"
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiry))
                .signWith(key)
                .compact();
    }

    /**
     * Refresh Token 생성.
     * claims: sub(userId), type=refresh
     */
    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiry))
                .signWith(key)
                .compact();
    }

    /** 토큰에서 Claims 추출 */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 토큰 유효성 검증 (서명 + 만료) */
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getAccessTokenExpiry() {
        return accessTokenExpiry;
    }

    public long getRefreshTokenExpiry() {
        return refreshTokenExpiry;
    }
}
