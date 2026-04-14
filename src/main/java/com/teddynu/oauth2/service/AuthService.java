package com.teddynu.oauth2.service;

import com.teddynu.oauth2.dto.OAuthUserInfo;
import com.teddynu.oauth2.entity.OAuthProvider;
import com.teddynu.oauth2.entity.User;
import com.teddynu.oauth2.repository.UserRepository;
import com.teddynu.oauth2.service.oauth.OAuthClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 인증 서비스 — OAuth2 콜백 처리의 핵심 오케스트레이터.
 *
 * 처리 흐름:
 * 1. OAuthClient를 통해 프로바이더에서 사용자 정보 가져오기
 * 2. DB에서 기존 사용자 조회 → 있으면 정보 갱신, 없으면 신규 생성
 * 3. JWT Access Token + Refresh Token 생성
 * 4. Refresh Token을 Redis에 저장
 * 5. 토큰 쌍 반환
 */
@Service
public class AuthService {

    private final Map<OAuthProvider, OAuthClient> oauthClients;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RedisService redisService;

    public AuthService(
            List<OAuthClient> clients,
            UserRepository userRepository,
            JwtService jwtService,
            RedisService redisService) {
        this.oauthClients = clients.stream()
                .collect(Collectors.toMap(OAuthClient::getProvider, Function.identity()));
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.redisService = redisService;
    }

    /** 해당 프로바이더의 OAuth2 인가 URL 반환 */
    public String getAuthorizationUrl(String providerName) {
        OAuthProvider provider = OAuthProvider.valueOf(providerName.toUpperCase());
        OAuthClient client = oauthClients.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("지원하지 않는 OAuth 프로바이더: " + providerName);
        }
        return client.getAuthorizationUrl();
    }

    /**
     * OAuth2 콜백 처리.
     * 인가 코드로 사용자 정보를 가져오고, DB 저장 후 JWT 토큰 쌍을 반환한다.
     */
    public Map<String, String> processCallback(String providerName, String code) {
        OAuthProvider provider = OAuthProvider.valueOf(providerName.toUpperCase());
        OAuthClient client = oauthClients.get(provider);

        // 1) OAuth 프로바이더에서 사용자 정보 조회
        OAuthUserInfo userInfo = client.getUserInfo(code);

        // 2) DB에서 기존 사용자 찾기 → 없으면 신규 생성
        User user = userRepository.findByProviderAndProviderId(provider, userInfo.providerId())
                .map(existing -> {
                    existing.setEmail(userInfo.email());
                    existing.setName(userInfo.name());
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(
                        new User(provider, userInfo.providerId(), userInfo.email(), userInfo.name())
                ));

        // 3) JWT 토큰 생성
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getName(), provider.name()
        );
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        // 4) Refresh Token → Redis 저장
        redisService.saveRefreshToken(
                user.getId(),
                refreshToken,
                Duration.ofMillis(jwtService.getRefreshTokenExpiry())
        );

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken
        );
    }

    /** 로그아웃: Access Token 블랙리스트 등록 + Refresh Token 삭제 */
    public void logout(String accessToken, Long userId) {
        redisService.blacklistAccessToken(
                accessToken,
                Duration.ofMillis(jwtService.getAccessTokenExpiry())
        );
        redisService.deleteRefreshToken(userId);
    }
}
