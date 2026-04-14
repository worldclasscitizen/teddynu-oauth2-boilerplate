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
import java.util.UUID;

/**
 * Naver OAuth2 클라이언트.
 *
 * 인가 URL: https://nid.naver.com/oauth2.0/authorize
 * 토큰 교환: https://nid.naver.com/oauth2.0/token
 * 사용자 정보: https://openapi.naver.com/v1/nid/me
 *
 * 사용자 정보 응답 구조:
 * {
 *   "resultcode": "00",
 *   "message": "success",
 *   "response": {
 *     "id": "abc123",
 *     "email": "user@naver.com",
 *     "name": "홍길동"
 *   }
 * }
 *
 * 참고: Naver는 CSRF 방지를 위해 state 파라미터를 필수로 요구한다.
 */
@Service
public class NaverOAuthClient implements OAuthClient {

    @Value("${oauth2.naver.client-id}")
    private String clientId;

    @Value("${oauth2.naver.client-secret}")
    private String clientSecret;

    @Value("${oauth2.naver.redirect-uri}")
    private String redirectUri;

    @Value("${oauth2.naver.auth-url}")
    private String authUrl;

    @Value("${oauth2.naver.token-url}")
    private String tokenUrl;

    @Value("${oauth2.naver.user-info-url}")
    private String userInfoUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.NAVER;
    }

    @Override
    public String getAuthorizationUrl() {
        String state = UUID.randomUUID().toString();
        return authUrl
                + "?client_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&state=" + state;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo getUserInfo(String code) {
        // Step 1: 인가 코드 → Access Token 교환
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("code", code);
        params.add("state", "oauth_state");

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
        Map<String, Object> body = userResponse.getBody();

        // Naver 응답 파싱: response 객체 안에 id, email, name
        Map<String, Object> naverResponse = (Map<String, Object>) body.get("response");

        return new OAuthUserInfo(
                (String) naverResponse.get("id"),
                (String) naverResponse.get("email"),
                (String) naverResponse.get("name"),
                OAuthProvider.NAVER
        );
    }
}
