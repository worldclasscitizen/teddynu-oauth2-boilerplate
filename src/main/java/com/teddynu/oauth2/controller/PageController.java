package com.teddynu.oauth2.controller;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 페이지 컨트롤러.
 *
 * GET /      → 로그인 페이지 (login.html)
 * GET /main  → 메인 페이지 (main.html) — 인증 필요
 */
@Controller
public class PageController {

    /** 랜딩 페이지 (OAuth2 로그인 버튼들) */
    @GetMapping("/")
    public String loginPage() {
        return "login";
    }

    /** 메인 페이지 — JWT Claims에서 사용자 정보를 추출하여 모델에 전달 */
    @GetMapping("/main")
    public String mainPage(Authentication authentication, Model model) {
        Claims claims = (Claims) authentication.getPrincipal();

        model.addAttribute("provider", claims.get("provider", String.class));
        model.addAttribute("email", claims.get("email", String.class));
        model.addAttribute("name", claims.get("name", String.class));

        return "main";
    }
}
