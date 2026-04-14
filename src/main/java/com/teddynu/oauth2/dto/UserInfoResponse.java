package com.teddynu.oauth2.dto;

/**
 * 프론트엔드에 반환할 사용자 정보 응답 DTO.
 * (REST API 방식으로 확장 시 사용)
 */
public record UserInfoResponse(
    String provider,
    String email,
    String name
) {}
