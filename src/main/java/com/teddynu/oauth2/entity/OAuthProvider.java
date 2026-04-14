package com.teddynu.oauth2.entity;

/**
 * 지원하는 OAuth2 프로바이더 목록.
 * 새 프로바이더를 추가하려면 여기에 enum 값을 추가하고, OAuthClient 구현체를 작성하면 된다.
 */
public enum OAuthProvider {
    GOOGLE,
    KAKAO,
    NAVER,
    SSAFY
}
