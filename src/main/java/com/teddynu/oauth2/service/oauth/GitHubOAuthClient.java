package com.teddynu.oauth2.service.oauth;

import com.teddynu.oauth2.dto.OAuthUserInfo;
import com.teddynu.oauth2.entity.OAuthProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * GitHub OAuth2 클라이언트.
 *
 * 인가 URL: https://github.com/login/oauth/authorize
 * 토큰 교환: https://github.com/login/oauth/access_token
 * 사용자 정보: https://api.github.com/user
 *
 * 사용자 정보 응답 구조:
 * { "id": 12345, "login": "username", "email": "user@github.com", "name": "홍길동", ... }
 *
 * 참고: GitHub 토큰 교환 시 Accept: application/json 헤더가 필요하다.
 *       (기본 응답이 application/x-www-form-urlencoded 형식이기 때문)
 */
@Service
public class GitHubOAuthClient implements OAuthClient {

    @Value("${oauth2.github.client-id}")
    private String clientId;

    @Value("${oauth2.github.client-secret}")
    private String clientSecret;

    @Value("${oauth2.github.redirect-uri}")
    private String redirectUri;

    @Value("${oauth2.github.auth-url}")
    private String authUrl;

    @Value("${oauth2.github.token-url}")
    private String tokenUrl;

    @Value("${oauth2.github.user-info-url}")
    private String userInfoUrl;

    @Value("${oauth2.github.scope}")
    private String scope;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.GITHUB;
    }

    @Override
    public String getAuthorizationUrl() {
        return authUrl
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo getUserInfo(String code) {
        // Step 1: 인가 코드 → Access Token 교환
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("code", code);
        params.add("redirect_uri", redirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON)); // GitHub은 이 헤더가 필수

        ResponseEntity<Map> tokenResponse = restTemplate.exchange(
                tokenUrl, HttpMethod.POST,
                new HttpEntity<>(params, headers),
                Map.class
        );
        String accessToken = (String) tokenResponse.getBody().get("access_token");

        // Step 2: Access Token → 사용자 정보 조회
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);

        ResponseEntity<Map> userResponse = restTemplate.exchange(
                userInfoUrl, HttpMethod.GET,
                new HttpEntity<>(userHeaders),
                Map.class
        );
        Map<String, Object> userInfo = userResponse.getBody();

        return new OAuthUserInfo(
                String.valueOf(userInfo.get("id")),
                (String) userInfo.get("email"),
                (String) userInfo.get("name"),
                OAuthProvider.GITHUB
        );
    }
}
