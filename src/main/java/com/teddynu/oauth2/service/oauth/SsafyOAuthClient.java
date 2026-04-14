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
 * SSAFY OAuth2 클라이언트.
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │ SSAFY OAuth2 흐름 요약                                          │
 * ├──────────────────────────────────────────────────────────────────┤
 * │ 1. 인가 코드 요청                                                │
 * │    GET https://project.ssafy.com/oauth/sso-check                │
 * │    params: client_id, redirect_url, response_type=code          │
 * │    ※ redirect_url (redirect_uri 아님)                            │
 * │                                                                  │
 * │ 2. Access Token 요청                                             │
 * │    POST https://project.ssafy.com/ssafy/oauth2/token            │
 * │    body: grant_type=authorization_code,                          │
 * │          client_id, client_secret, redirect_url, code            │
 * │    응답: { token_type, access_token, scope, expires_in,          │
 * │           refresh_token, refresh_token_expires_in }              │
 * │                                                                  │
 * │ 3. 사용자 정보 조회                                               │
 * │    GET https://project.ssafy.com/ssafy/resources/userInfo       │
 * │    header: Authorization: Bearer {ACCESS_TOKEN}                  │
 * │    응답: { userId, email, name }                                  │
 * │                                                                  │
 * │ 4. 토큰 갱신                                                     │
 * │    POST https://project.ssafy.com/ssafy/oauth2/token            │
 * │    body: grant_type=refresh_token,                               │
 * │          client_id, client_secret, refresh_token                 │
 * └──────────────────────────────────────────────────────────────────┘
 */
@Service
public class SsafyOAuthClient implements OAuthClient {

    @Value("${oauth2.ssafy.client-id}")
    private String clientId;

    @Value("${oauth2.ssafy.client-secret}")
    private String clientSecret;

    @Value("${oauth2.ssafy.redirect-uri}")
    private String redirectUri;

    @Value("${oauth2.ssafy.auth-url}")
    private String authUrl;

    @Value("${oauth2.ssafy.token-url}")
    private String tokenUrl;

    @Value("${oauth2.ssafy.user-info-url}")
    private String userInfoUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.SSAFY;
    }

    @Override
    public String getAuthorizationUrl() {
        // SSAFY는 redirect_url 파라미터명을 사용 (redirect_uri 아님)
        return authUrl
                + "?client_id=" + clientId
                + "&redirect_url=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code";
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo getUserInfo(String code) {
        // Step 1: 인가 코드 → Access Token 교환
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_url", redirectUri);  // SSAFY: redirect_url
        params.add("code", code);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "x-www-form-urlencoded",
                StandardCharsets.UTF_8));

        ResponseEntity<Map> tokenResponse = restTemplate.exchange(
                tokenUrl, HttpMethod.POST,
                new HttpEntity<>(params, headers),
                Map.class
        );
        String accessToken = (String) tokenResponse.getBody().get("access_token");

        // Step 2: Access Token → 사용자 정보 조회
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.set("Authorization", "Bearer " + accessToken);
        userHeaders.setContentType(new MediaType("application", "x-www-form-urlencoded",
                StandardCharsets.UTF_8));

        ResponseEntity<Map> userResponse = restTemplate.exchange(
                userInfoUrl, HttpMethod.GET,
                new HttpEntity<>(userHeaders),
                Map.class
        );
        Map<String, Object> userInfo = userResponse.getBody();

        // SSAFY 응답: { userId, email, name }
        return new OAuthUserInfo(
                (String) userInfo.get("userId"),
                (String) userInfo.get("email"),
                (String) userInfo.get("name"),
                OAuthProvider.SSAFY
        );
    }
}
