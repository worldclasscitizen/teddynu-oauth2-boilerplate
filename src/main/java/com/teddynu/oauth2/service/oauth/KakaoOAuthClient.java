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
 * Kakao OAuth2 클라이언트.
 *
 * 인가 URL: https://kauth.kakao.com/oauth/authorize
 * 토큰 교환: https://kauth.kakao.com/oauth/token
 * 사용자 정보: https://kapi.kakao.com/v2/user/me
 *
 * 사용자 정보 응답 구조:
 * {
 *   "id": 1234567890,
 *   "kakao_account": {
 *     "email": "user@kakao.com",
 *     "profile": {
 *       "nickname": "홍길동"
 *     }
 *   }
 * }
 */
@Service
public class KakaoOAuthClient implements OAuthClient {

    @Value("${oauth2.kakao.client-id}")
    private String clientId;

    @Value("${oauth2.kakao.client-secret}")
    private String clientSecret;

    @Value("${oauth2.kakao.redirect-uri}")
    private String redirectUri;

    @Value("${oauth2.kakao.auth-url}")
    private String authUrl;

    @Value("${oauth2.kakao.token-url}")
    private String tokenUrl;

    @Value("${oauth2.kakao.user-info-url}")
    private String userInfoUrl;

    @Value("${oauth2.kakao.scope}")
    private String scope;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.KAKAO;
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
        userHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<Map> userResponse = restTemplate.exchange(
                userInfoUrl, HttpMethod.GET,
                new HttpEntity<>(userHeaders),
                Map.class
        );
        Map<String, Object> body = userResponse.getBody();

        // Kakao 응답 파싱: id는 Long, kakao_account 안에 email과 profile.nickname
        String providerId = String.valueOf(body.get("id"));
        String email = null;
        String name = null;

        Map<String, Object> kakaoAccount = (Map<String, Object>) body.get("kakao_account");
        if (kakaoAccount != null) {
            email = (String) kakaoAccount.get("email");
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
            if (profile != null) {
                name = (String) profile.get("nickname");
            }
        }

        return new OAuthUserInfo(providerId, email, name, OAuthProvider.KAKAO);
    }
}
