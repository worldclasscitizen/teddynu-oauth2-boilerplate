package com.teddynu.oauth2.service.oauth;

import com.teddynu.oauth2.dto.OAuthUserInfo;
import com.teddynu.oauth2.entity.OAuthProvider;

/**
 * OAuth2 프로바이더 클라이언트 인터페이스.
 *
 * 새 프로바이더를 추가하려면:
 * 1. OAuthProvider enum에 값 추가
 * 2. 이 인터페이스를 구현하는 @Service 클래스 작성
 * 3. application.yml에 해당 프로바이더의 설정 추가
 *
 * Spring이 자동으로 모든 구현체를 List<OAuthClient>로 주입하므로,
 * AuthService에서 provider별 라우팅이 자동 처리된다.
 */
public interface OAuthClient {

    /** 이 클라이언트가 담당하는 프로바이더 */
    OAuthProvider getProvider();

    /** 사용자를 리다이렉트할 OAuth2 인가 URL 생성 */
    String getAuthorizationUrl();

    /** 인가 코드를 받아 사용자 정보를 조회 (토큰 교환 + 프로필 조회) */
    OAuthUserInfo getUserInfo(String authorizationCode);
}
