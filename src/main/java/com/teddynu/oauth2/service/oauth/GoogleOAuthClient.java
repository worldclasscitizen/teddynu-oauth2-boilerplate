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
 * Google OAuth2 클라이언트.
 *
 * 인가 URL: https://accounts.google.com/o/oauth2/v2/auth
 * 토큰 교환: https://oauth2.googleapis.com/token
 * 사용자 정보: https://www.googleapis.com/oauth2/v2/userinfo
 *
 * 응답 예시 (userinfo):
 * { "id": "1234567890", "email": "user@gmail.com", "name": "홍길동", ... }
 */
@Service
public class GoogleOAuthClient implements OAuthClient {

    @Value("${oauth2.google.client-id}")
    private String clientId;

    @Value("${oauth2.google.client-secret}")
    private String clientSecret;

    @Value("${oauth2.google.redirect-uri}")
    private String redirectUri;

    @Value("${oauth2.google.auth-url}")
    private String authUrl;

    @Value("${oauth2.google.token-url}")
    private String tokenUrl;

    @Value("${oauth2.google.user-info-url}")
    private String userInfoUrl;

    @Value("${oauth2.google.scope}")
    private String scope;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.GOOGLE;
    }

    @Override
    public String getAuthorizationUrl() {
        return authUrl
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo getUserInfo(String code) {
        // Step 1: 인가 코드 → Access Token 교환
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

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
                OAuthProvider.GOOGLE
        );
    }
}
