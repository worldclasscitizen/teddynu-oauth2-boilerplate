package com.teddynu.oauth2.controller;

import com.teddynu.oauth2.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 인증 관련 엔드포인트.
 *
 * GET  /api/auth/login/{provider}      → 해당 OAuth 프로바이더 로그인 페이지로 리다이렉트
 * GET  /api/auth/callback/{provider}   → 인가 코드 콜백 처리 → JWT 쿠키 설정 → /main 리다이렉트
 * POST /api/auth/logout                → JWT 무효화 → 쿠키 삭제 → / 리다이렉트
 */
@Controller
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** OAuth2 로그인 시작: 프로바이더의 인가 URL로 리다이렉트 */
    @GetMapping("/login/{provider}")
    public String login(@PathVariable String provider) {
        String authorizationUrl = authService.getAuthorizationUrl(provider);
        return "redirect:" + authorizationUrl;
    }

    /** OAuth2 콜백: 인가 코드를 받아 토큰 교환 + 사용자 저장 + JWT 발급 */
    @GetMapping("/callback/{provider}")
    public String callback(
            @PathVariable String provider,
            @RequestParam String code,
            HttpServletResponse response) {

        Map<String, String> tokens = authService.processCallback(provider, code);

        // Access Token → HttpOnly 쿠키
        Cookie accessCookie = new Cookie("access_token", tokens.get("accessToken"));
        accessCookie.setHttpOnly(true);
        accessCookie.setPath("/");
        accessCookie.setMaxAge(3600); // 1시간

        // Refresh Token → HttpOnly 쿠키 (갱신 경로에서만 전송)
        Cookie refreshCookie = new Cookie("refresh_token", tokens.get("refreshToken"));
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/api/auth/refresh");
        refreshCookie.setMaxAge(604800); // 7일

        response.addCookie(accessCookie);
        response.addCookie(refreshCookie);

        return "redirect:/main";
    }

    /** 로그아웃: JWT 블랙리스트 등록 + 쿠키 삭제 */
    @PostMapping("/logout")
    public String logout(Authentication authentication, HttpServletResponse response) {
        if (authentication != null) {
            Claims claims = (Claims) authentication.getPrincipal();
            String token = (String) authentication.getCredentials();
            Long userId = Long.valueOf(claims.getSubject());
            authService.logout(token, userId);
        }

        // 쿠키 만료 처리
        Cookie accessCookie = new Cookie("access_token", null);
        accessCookie.setHttpOnly(true);
        accessCookie.setPath("/");
        accessCookie.setMaxAge(0);

        Cookie refreshCookie = new Cookie("refresh_token", null);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/api/auth/refresh");
        refreshCookie.setMaxAge(0);

        response.addCookie(accessCookie);
        response.addCookie(refreshCookie);

        return "redirect:/";
    }
}
