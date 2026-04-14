# OAuth2 + JWT Boilerplate

> **어느 프로젝트에나 붙여넣을 수 있는** Google / Kakao / Naver / SSAFY OAuth2 로그인 보일러플레이트.
> JWT 토큰 발급 · Redis 기반 토큰 관리 · MySQL 사용자 저장까지 한 번에 제공합니다.

---

## 목차

1. [기술 스택](#기술-스택)
2. [프로젝트 구조](#프로젝트-구조)
3. [빠른 시작](#빠른-시작)
4. [OAuth2 + JWT 인증 흐름 가이드](#oauth2--jwt-인증-흐름-가이드)
   - [전체 흐름 한눈에 보기](#1-전체-흐름-한눈에-보기)
   - [Step 1: 로그인 요청](#step-1-로그인-요청)
   - [Step 2: 인가 코드 수신 (Callback)](#step-2-인가-코드-수신-callback)
   - [Step 3: 토큰 교환 + 사용자 정보 조회](#step-3-토큰-교환--사용자-정보-조회)
   - [Step 4: JWT 발급 + Redis 저장](#step-4-jwt-발급--redis-저장)
   - [Step 5: 인증된 요청 처리 (JWT 필터)](#step-5-인증된-요청-처리-jwt-필터)
   - [Step 6: 로그아웃](#step-6-로그아웃)
5. [JWT 토큰 구조 상세](#jwt-토큰-구조-상세)
6. [Redis 저장 구조](#redis-저장-구조)
7. [SSAFY OAuth2 상세 명세](#ssafy-oauth2-상세-명세)
8. [프로바이더 추가 가이드](#프로바이더-추가-가이드)
9. [환경 변수 목록](#환경-변수-목록)
10. [자주 묻는 질문 (FAQ)](#자주-묻는-질문-faq)

---

## 기술 스택

| 구분 | 기술 | 버전 | 용도 |
|------|------|------|------|
| Framework | Spring Boot | 3.3.x | 웹 애플리케이션 프레임워크 |
| Language | Java | 17+ | 메인 언어 |
| Template | Thymeleaf | - | 서버사이드 HTML 렌더링 |
| Database | MySQL | 8.0 | 사용자 정보 영구 저장 |
| Cache | Redis | 7.x | Refresh Token 저장 + AT 블랙리스트 |
| Auth | jjwt | 0.12.6 | JWT 토큰 생성/검증 |
| Security | Spring Security | 6.x | 인증/인가 필터 체인 |
| Build | Gradle | - | 빌드 도구 |
| Infra | Docker Compose | - | MySQL + Redis 로컬 실행 |

---

## 프로젝트 구조

```
teddynu-oauth2-boilerplate/
├── docker-compose.yml          # MySQL + Redis 컨테이너
├── .env.example                # 환경 변수 템플릿
├── build.gradle                # 의존성 관리
├── settings.gradle
│
└── src/main/
    ├── java/com/teddynu/oauth2/
    │   ├── OAuth2BoilerplateApplication.java   # 엔트리포인트
    │   │
    │   ├── config/
    │   │   ├── SecurityConfig.java             # Spring Security 설정
    │   │   └── RedisConfig.java                # Redis 설정
    │   │
    │   ├── controller/
    │   │   ├── AuthController.java             # 인증 API (login, callback, logout)
    │   │   └── PageController.java             # 페이지 라우팅 (/, /main)
    │   │
    │   ├── dto/
    │   │   ├── OAuthUserInfo.java              # OAuth 사용자 정보 DTO
    │   │   └── UserInfoResponse.java           # API 응답 DTO
    │   │
    │   ├── entity/
    │   │   ├── OAuthProvider.java              # enum: GOOGLE, KAKAO, NAVER, SSAFY
    │   │   └── User.java                       # JPA 사용자 엔티티
    │   │
    │   ├── filter/
    │   │   └── JwtAuthenticationFilter.java    # JWT 인증 필터
    │   │
    │   ├── repository/
    │   │   └── UserRepository.java             # 사용자 JPA Repository
    │   │
    │   └── service/
    │       ├── AuthService.java                # 인증 오케스트레이터
    │       ├── JwtService.java                 # JWT 생성/검증
    │       ├── RedisService.java               # Redis 토큰 관리
    │       └── oauth/
    │           ├── OAuthClient.java            # OAuth 클라이언트 인터페이스
    │           ├── GoogleOAuthClient.java       # Google 구현
    │           ├── KakaoOAuthClient.java        # Kakao 구현
    │           ├── NaverOAuthClient.java        # Naver 구현
    │           └── SsafyOAuthClient.java        # SSAFY 구현
    │
    └── resources/
        ├── application.yml                     # 애플리케이션 설정
        └── templates/
            ├── login.html                      # 랜딩 페이지 (로그인 버튼)
            └── main.html                       # 메인 페이지 (사용자 정보)
```

---

## 빠른 시작

### 1) 사전 요구사항

- Java 17+
- Docker & Docker Compose (MySQL, Redis 실행용)
- 각 OAuth 프로바이더의 Client ID / Secret

### 2) 인프라 실행 (MySQL + Redis)

```bash
cd teddynu-oauth2-boilerplate
docker-compose up -d
```

### 3) 환경 변수 설정

```bash
cp .env.example .env
# .env 파일을 열어 각 프로바이더의 Client ID, Secret을 입력
```

### 4) 애플리케이션 실행

```bash
# Gradle Wrapper 생성 (최초 1회)
gradle wrapper

# 실행
./gradlew bootRun
```

### 5) 브라우저에서 확인

http://localhost:8080 접속 → OAuth2 로그인 버튼 클릭

---

## OAuth2 + JWT 인증 흐름 가이드

이 보일러플레이트의 핵심 인증/인가 로직을 단계별로 설명합니다.

### 1) 전체 흐름 한눈에 보기

```
┌─────────┐     ┌──────────────┐     ┌──────────────────┐     ┌────────┐
│  사용자  │     │  Application │     │  OAuth Provider   │     │ MySQL  │
│ (브라우저)│     │ (Spring Boot)│     │ (Google/Kakao/    │     │ + Redis│
│         │     │              │     │  Naver/SSAFY)     │     │        │
└────┬────┘     └──────┬───────┘     └────────┬──────────┘     └───┬────┘
     │                 │                      │                    │
     │ ① 로그인 클릭    │                      │                    │
     │────────────────>│                      │                    │
     │                 │                      │                    │
     │ ② 인가 URL 리다이렉트                    │                    │
     │<────────────────│                      │                    │
     │                 │                      │                    │
     │ ③ 프로바이더 로그인 + 동의               │                    │
     │────────────────────────────────────────>│                    │
     │                 │                      │                    │
     │ ④ 인가 코드 (code) 포함 리다이렉트        │                    │
     │<───────────────────────────────────────│                    │
     │                 │                      │                    │
     │ ⑤ /callback?code=xxx                  │                    │
     │────────────────>│                      │                    │
     │                 │                      │                    │
     │                 │ ⑥ code → Access Token │                    │
     │                 │─────────────────────>│                    │
     │                 │                      │                    │
     │                 │ ⑦ Access Token 반환   │                    │
     │                 │<─────────────────────│                    │
     │                 │                      │                    │
     │                 │ ⑧ 사용자 정보 요청      │                    │
     │                 │─────────────────────>│                    │
     │                 │                      │                    │
     │                 │ ⑨ 사용자 정보 반환      │                    │
     │                 │<─────────────────────│                    │
     │                 │                      │                    │
     │                 │ ⑩ DB 저장 + JWT 생성  │                    │
     │                 │───────────────────────────────────────────>│
     │                 │                      │                    │
     │ ⑪ JWT 쿠키 + /main 리다이렉트           │                    │
     │<────────────────│                      │                    │
```

### Step 1: 로그인 요청

사용자가 로그인 버튼을 클릭하면 `AuthController.login()`이 호출됩니다.

```
사용자 → GET /api/auth/login/google → 302 Redirect → Google 인가 페이지
```

**코드 위치**: `AuthController.java`

```java
@GetMapping("/login/{provider}")
public String login(@PathVariable String provider) {
    String authorizationUrl = authService.getAuthorizationUrl(provider);
    return "redirect:" + authorizationUrl;
}
```

**동작 원리**:
1. URL의 `{provider}` 부분을 추출 (예: `google`)
2. `AuthService`가 해당 프로바이더의 `OAuthClient` 구현체를 찾음
3. `OAuthClient.getAuthorizationUrl()`로 인가 URL을 생성
4. 사용자를 해당 URL로 302 리다이렉트

**생성되는 인가 URL 예시 (Google)**:
```
https://accounts.google.com/o/oauth2/v2/auth
  ?client_id=YOUR_CLIENT_ID
  &redirect_uri=http://localhost:8080/api/auth/callback/google
  &response_type=code
  &scope=email%20profile
```

### Step 2: 인가 코드 수신 (Callback)

사용자가 프로바이더에서 로그인하면, 프로바이더가 우리 서버로 인가 코드를 포함해 리다이렉트합니다.

```
Google 인증 서버 → 302 → GET /api/auth/callback/google?code=4/0AX4XfWg...
```

**코드 위치**: `AuthController.java`

```java
@GetMapping("/callback/{provider}")
public String callback(
        @PathVariable String provider,
        @RequestParam String code,
        HttpServletResponse response) {
    Map<String, String> tokens = authService.processCallback(provider, code);
    // ... 쿠키 설정 후 /main으로 리다이렉트
}
```

### Step 3: 토큰 교환 + 사용자 정보 조회

콜백에서 받은 인가 코드로 두 번의 HTTP 요청이 발생합니다.

**코드 위치**: 각 `OAuthClient` 구현체의 `getUserInfo()` 메서드

```
① 인가 코드 → Access Token 교환
   POST {token-url}
   Body: grant_type=authorization_code & client_id=... & code=...
   Response: { "access_token": "ya29.xxx", ... }

② Access Token → 사용자 정보 조회
   GET {user-info-url}
   Header: Authorization: Bearer ya29.xxx
   Response: { "id": "123", "email": "user@gmail.com", "name": "홍길동" }
```

각 프로바이더마다 응답 JSON 구조가 다릅니다:

| 프로바이더 | id 필드 | email 필드 | name 필드 |
|-----------|---------|-----------|----------|
| Google | `id` | `email` | `name` |
| Kakao | `id` | `kakao_account.email` | `kakao_account.profile.nickname` |
| Naver | `response.id` | `response.email` | `response.name` |
| SSAFY | `userId` | `email` | `name` |

각 `OAuthClient` 구현체가 이 차이를 캡슐화하여, 통일된 `OAuthUserInfo` 레코드로 변환합니다.

### Step 4: JWT 발급 + Redis 저장

사용자 정보를 DB에 저장(또는 갱신)한 뒤, 자체 JWT 토큰을 발급합니다.

**코드 위치**: `AuthService.processCallback()`

```java
// 1) DB에 사용자 저장 (upsert)
User user = userRepository.findByProviderAndProviderId(provider, userInfo.providerId())
    .map(existing -> { /* 기존 사용자 정보 갱신 */ })
    .orElseGet(() -> { /* 신규 사용자 생성 */ });

// 2) JWT Access Token 생성 (claims에 사용자 정보 포함)
String accessToken = jwtService.generateAccessToken(
    user.getId(), user.getEmail(), user.getName(), provider.name()
);

// 3) JWT Refresh Token 생성
String refreshToken = jwtService.generateRefreshToken(user.getId());

// 4) Refresh Token을 Redis에 저장 (TTL: 7일)
redisService.saveRefreshToken(user.getId(), refreshToken, Duration.ofMillis(...));
```

**왜 OAuth Access Token을 직접 사용하지 않고 JWT를 발급하나요?**

- OAuth Access Token은 **프로바이더의 API를 호출**하기 위한 것입니다.
- 우리 서버의 인증은 **자체 발급한 JWT**로 처리합니다.
- 이렇게 분리하면 프로바이더에 종속되지 않고, 토큰 만료/갱신 정책을 우리가 제어할 수 있습니다.

### Step 5: 인증된 요청 처리 (JWT 필터)

로그인 이후 모든 요청은 `JwtAuthenticationFilter`를 거칩니다.

**코드 위치**: `JwtAuthenticationFilter.java`

```
요청 → [JwtAuthenticationFilter] → Controller

필터 동작 순서:
1. 쿠키 또는 Authorization 헤더에서 JWT 추출
2. JWT 서명 + 만료시간 검증 (jjwt 라이브러리)
3. Redis 블랙리스트 확인 (로그아웃된 토큰인지)
4. 유효하면 → SecurityContext에 Authentication 설정
5. 유효하지 않으면 → SecurityContext 비어 있음 → 403
```

```java
String token = resolveToken(request);  // 쿠키 또는 헤더에서 추출

if (token != null && jwtService.isValid(token) && !redisService.isBlacklisted(token)) {
    Claims claims = jwtService.parseClaims(token);
    // SecurityContext에 인증 정보 설정
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(claims, token, Collections.emptyList())
    );
}
```

**컨트롤러에서 사용자 정보 접근하기:**

```java
@GetMapping("/main")
public String mainPage(Authentication authentication, Model model) {
    Claims claims = (Claims) authentication.getPrincipal();
    String email = claims.get("email", String.class);      // 이메일
    String provider = claims.get("provider", String.class); // 프로바이더
    String name = claims.get("name", String.class);         // 이름
}
```

### Step 6: 로그아웃

JWT는 서버에 저장되지 않으므로(stateless), 토큰 무효화를 위해 **Redis 블랙리스트**를 사용합니다.

**코드 위치**: `AuthService.logout()`

```java
public void logout(String accessToken, Long userId) {
    // 1) Access Token을 블랙리스트에 등록 (남은 만료시간만큼 TTL)
    redisService.blacklistAccessToken(accessToken, Duration.ofMillis(accessTokenExpiry));

    // 2) Refresh Token 삭제
    redisService.deleteRefreshToken(userId);
}
```

```
로그아웃 흐름:
1. POST /api/auth/logout
2. Redis에 Access Token 블랙리스트 등록 ("BL:{token}" = "true")
3. Redis에서 Refresh Token 삭제 ("RT:{userId}" 키 삭제)
4. 브라우저 쿠키 만료 처리 (MaxAge=0)
5. / (로그인 페이지)로 리다이렉트
```

---

## JWT 토큰 구조 상세

### Access Token

```json
{
  "sub": "1",                    // 사용자 DB ID
  "email": "user@gmail.com",    // 이메일
  "name": "홍길동",              // 이름
  "provider": "GOOGLE",         // OAuth 프로바이더
  "type": "access",             // 토큰 타입
  "iat": 1713000000,            // 발급 시각 (Unix timestamp)
  "exp": 1713003600             // 만료 시각 (+1시간)
}
```

- **저장 위치**: 브라우저 HttpOnly 쿠키 (`access_token`)
- **만료**: 1시간 (설정 가능)
- **용도**: API 요청 인증

### Refresh Token

```json
{
  "sub": "1",                    // 사용자 DB ID
  "type": "refresh",            // 토큰 타입
  "iat": 1713000000,
  "exp": 1713604800             // 만료 시각 (+7일)
}
```

- **저장 위치**: 브라우저 HttpOnly 쿠키 (`refresh_token`) + Redis (`RT:{userId}`)
- **만료**: 7일 (설정 가능)
- **용도**: Access Token 갱신

### 왜 HttpOnly 쿠키를 사용하나요?

| 저장소 | XSS 공격 | CSRF 공격 | 선택 이유 |
|--------|---------|----------|----------|
| localStorage | **취약** (JS로 접근 가능) | 안전 | - |
| 일반 쿠키 | **취약** (JS로 접근 가능) | 취약 | - |
| **HttpOnly 쿠키** | **안전** (JS 접근 불가) | 주의 필요 | **보안성 우수** |

CSRF는 SameSite 속성이나 CSRF 토큰으로 추가 방어 가능합니다.

---

## Redis 저장 구조

```
┌───────────────────────────────────────────────────────┐
│ Redis                                                 │
├────────────────────┬──────────────────┬───────────────┤
│ Key                │ Value            │ TTL           │
├────────────────────┼──────────────────┼───────────────┤
│ RT:1               │ eyJhbGciOi...    │ 7일 (604800s) │
│ RT:2               │ eyJhbGciOi...    │ 7일           │
│ BL:eyJhbGci...     │ true             │ 1시간 (3600s) │
└────────────────────┴──────────────────┴───────────────┘

RT: = Refresh Token (사용자별 1개)
BL: = Blacklisted Access Token (로그아웃 시 등록)
```

---

## SSAFY OAuth2 상세 명세

SSAFY OAuth2는 표준 OAuth2 Authorization Code 방식을 따르되, 몇 가지 차이점이 있습니다.

### 주의 사항

| 항목 | 표준 OAuth2 | SSAFY OAuth2 |
|------|------------|--------------|
| 리다이렉트 파라미터명 | `redirect_uri` | **`redirect_url`** |
| 토큰 요청 URL | 별도 | `POST /ssafy/oauth2/token` |
| 사용자 정보 응답 | 프로바이더마다 다름 | `{ userId, email, name }` |

### API 엔드포인트

```
인가 코드 요청:  GET  https://project.ssafy.com/oauth/sso-check
토큰 교환:      POST https://project.ssafy.com/ssafy/oauth2/token
토큰 갱신:      POST https://project.ssafy.com/ssafy/oauth2/token (grant_type=refresh_token)
사용자 정보:    GET  https://project.ssafy.com/ssafy/resources/userInfo
```

### 인가 코드 요청 파라미터

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `client_id` | String | 앱 등록 시 발급받은 Client ID |
| `redirect_url` | String | 인가 코드를 전달받을 서버 URL |
| `response_type` | String | `code`로 고정 |

### 토큰 교환 요청 파라미터 (POST Body)

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `grant_type` | String | `authorization_code`로 고정 |
| `client_id` | String | Client ID |
| `client_secret` | String | Client Secret |
| `redirect_url` | String | Redirect URL |
| `code` | String | 인가 코드 |

### 토큰 교환 응답

| 필드 | 타입 | 설명 |
|------|------|------|
| `token_type` | String | `bearer` 고정 |
| `access_token` | String | 액세스 토큰 |
| `scope` | String | 권한 범위 |
| `expires_in` | String | AT 만료시간(초) |
| `refresh_token` | String | 리프레시 토큰 |
| `refresh_token_expires_in` | Integer | RT 만료시간(초) |

### 사용자 정보 응답

| 필드 | 타입 | 설명 |
|------|------|------|
| `userId` | String | 사용자 고유 식별 ID |
| `email` | String | 이메일 (동의 시) |
| `name` | String | 이름 (동의 시) |

---

## 프로바이더 추가 가이드

새 OAuth2 프로바이더를 추가하려면 **3가지**만 하면 됩니다.

### 1) `OAuthProvider` enum에 값 추가

```java
// entity/OAuthProvider.java
public enum OAuthProvider {
    GOOGLE, KAKAO, NAVER, SSAFY,
    GITHUB  // ← 추가
}
```

### 2) `OAuthClient` 구현체 작성

```java
@Service
public class GithubOAuthClient implements OAuthClient {

    @Value("${oauth2.github.client-id}")
    private String clientId;
    // ... 나머지 설정값

    @Override
    public OAuthProvider getProvider() {
        return OAuthProvider.GITHUB;
    }

    @Override
    public String getAuthorizationUrl() {
        // GitHub 인가 URL 생성 로직
    }

    @Override
    public OAuthUserInfo getUserInfo(String code) {
        // 1. code → access token 교환
        // 2. access token → 사용자 정보 조회
        // 3. OAuthUserInfo로 변환하여 반환
    }
}
```

### 3) `application.yml`에 설정 추가

```yaml
oauth2:
  github:
    client-id: ${GITHUB_CLIENT_ID:}
    client-secret: ${GITHUB_CLIENT_SECRET:}
    redirect-uri: ${GITHUB_REDIRECT_URI:http://localhost:8080/api/auth/callback/github}
    auth-url: https://github.com/login/oauth/authorize
    token-url: https://github.com/login/oauth/access_token
    user-info-url: https://api.github.com/user
    scope: user:email
```

**끝!** `@Service`가 붙어 있으면 Spring이 자동으로 `List<OAuthClient>`에 주입하고,
`AuthService`가 `getProvider()` 반환값으로 자동 라우팅합니다.

---

## 환경 변수 목록

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `MYSQL_HOST` | localhost | MySQL 호스트 |
| `MYSQL_PORT` | 3306 | MySQL 포트 |
| `MYSQL_DATABASE` | oauth2_boilerplate | DB 이름 |
| `MYSQL_USERNAME` | root | DB 사용자명 |
| `MYSQL_PASSWORD` | root1234 | DB 비밀번호 |
| `REDIS_HOST` | localhost | Redis 호스트 |
| `REDIS_PORT` | 6379 | Redis 포트 |
| `REDIS_PASSWORD` | (빈 값) | Redis 비밀번호 |
| `JWT_SECRET` | - | **반드시 변경** - 32자 이상 랜덤 문자열 |
| `GOOGLE_CLIENT_ID` | - | Google OAuth Client ID |
| `GOOGLE_CLIENT_SECRET` | - | Google OAuth Client Secret |
| `GOOGLE_REDIRECT_URI` | http://localhost:8080/api/auth/callback/google | Google 콜백 URI |
| `KAKAO_CLIENT_ID` | - | Kakao REST API Key |
| `KAKAO_CLIENT_SECRET` | - | Kakao Client Secret |
| `KAKAO_REDIRECT_URI` | http://localhost:8080/api/auth/callback/kakao | Kakao 콜백 URI |
| `NAVER_CLIENT_ID` | - | Naver Client ID |
| `NAVER_CLIENT_SECRET` | - | Naver Client Secret |
| `NAVER_REDIRECT_URI` | http://localhost:8080/api/auth/callback/naver | Naver 콜백 URI |
| `SSAFY_CLIENT_ID` | - | SSAFY Client ID |
| `SSAFY_CLIENT_SECRET` | - | SSAFY Client Secret |
| `SSAFY_REDIRECT_URI` | http://localhost:8080/api/auth/callback/ssafy | SSAFY 콜백 URI |

---

## 자주 묻는 질문 (FAQ)

### Q: 프로덕션에서 JWT_SECRET은 어떻게 관리하나요?

환경 변수 또는 Secret Manager(AWS Secrets Manager, Vault 등)에 보관하세요.
절대 코드나 Git에 하드코딩하면 안 됩니다. `.env` 파일은 `.gitignore`에 포함되어 있습니다.

### Q: Access Token이 만료되면 어떻게 되나요?

현재 보일러플레이트에서는 만료된 AT로 요청 시 인증이 실패하여 로그인 페이지로 돌아갑니다.
프로덕션에서는 프론트엔드에서 401 응답을 감지하고, Refresh Token으로 AT 갱신 API를 호출하는 로직을 추가하세요.

### Q: HTTPS는 필수인가요?

로컬 개발에서는 HTTP로 테스트 가능하지만, 프로덕션에서는 반드시 HTTPS를 사용해야 합니다.
쿠키에 `Secure` 플래그를 추가하고, `SameSite=Lax` 또는 `Strict`로 설정하세요.

### Q: 여러 프로바이더로 같은 이메일 계정이 로그인하면?

`provider + providerId` 조합으로 유니크 제약이 걸려 있으므로, 같은 이메일이라도 프로바이더가 다르면 별도 계정으로 처리됩니다.
계정 연동(Account Linking)이 필요하면 이메일 기준 병합 로직을 추가로 구현하세요.

### Q: DB에 저장되는 컬럼은?

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGINT (PK) | 자동 증가 |
| `provider` | VARCHAR(20) | GOOGLE, KAKAO, NAVER, SSAFY |
| `provider_id` | VARCHAR(255) | 프로바이더 고유 사용자 ID |
| `email` | VARCHAR(255) | 이메일 |
| `name` | VARCHAR(255) | 이름 |
| `created_at` | DATETIME | 생성일시 |
| `updated_at` | DATETIME | 수정일시 |
