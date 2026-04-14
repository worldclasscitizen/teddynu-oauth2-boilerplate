package com.teddynu.oauth2.filter;

import com.teddynu.oauth2.service.JwtService;
import com.teddynu.oauth2.service.RedisService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 인증 필터.
 *
 * 매 요청마다 실행되며, 다음 순서로 토큰을 탐색한다:
 * 1. Cookie의 "access_token" 값
 * 2. Authorization 헤더의 "Bearer {token}" 값
 *
 * 유효한 토큰이 있고 블랙리스트에 없으면:
 * - Claims를 principal로, 원본 토큰을 credentials로 하여
 *   SecurityContext에 Authentication을 설정한다.
 *
 * 이후 Controller에서 Authentication.getPrincipal()로 Claims에 접근 가능:
 *   Claims claims = (Claims) authentication.getPrincipal();
 *   String email = claims.get("email", String.class);
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RedisService redisService;

    public JwtAuthenticationFilter(JwtService jwtService, RedisService redisService) {
        this.jwtService = jwtService;
        this.redisService = redisService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null && jwtService.isValid(token) && !redisService.isBlacklisted(token)) {
            Claims claims = jwtService.parseClaims(token);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            claims,   // principal → Claims 객체
                            token,    // credentials → 원본 JWT 문자열
                            Collections.emptyList()
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        chain.doFilter(request, response);
    }

    /** 요청에서 JWT 토큰을 추출한다 (Cookie 우선, Authorization 헤더 보조) */
    private String resolveToken(HttpServletRequest request) {
        // 1) Cookie에서 탐색
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // 2) Authorization 헤더에서 탐색
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }

        return null;
    }
}
