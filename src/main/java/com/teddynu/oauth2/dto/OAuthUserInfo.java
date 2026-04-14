package com.teddynu.oauth2.dto;

import com.teddynu.oauth2.entity.OAuthProvider;

/**
 * OAuth2 프로바이더로부터 받아온 사용자 정보 DTO.
 * 각 OAuthClient 구현체가 프로바이더별 JSON 응답을 이 형태로 변환한다.
 */
public record OAuthUserInfo(
    String providerId,
    String email,
    String name,
    OAuthProvider provider
) {}
