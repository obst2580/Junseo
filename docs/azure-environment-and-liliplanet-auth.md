> **과거 시점의 기록입니다.** 현재 운영 환경·커밋·FCM·Android·남은 작업은 [HANDOFF.md](../HANDOFF.md)를 먼저 읽으세요. 아래의 사전 조사/미완료/커밋 전 설명은 작성 당시 상태입니다.

# Azure 환경과 LiliPlanet 로그인 연동 확인

**2026-10-03 실행 결과:** 아래는 변경 전 조사 기록이다. 후속 사용자 요청에 따른 실제 구현·리소스 생성·배포 결과는 [배포 기록](azure-deployment.md)에 별도로 정리했다.

작성일시: 2026-10-02 20:50 KST

사용자 요청: Azure 환경을 확인하고 Junseo가 LiliPlanet 공통 로그인을 사용하도록 준비한다.

이번 확인은 Azure CLI 읽기 전용 조회, 로컬 플랫폼/제품 소스 및 온보딩 런북 검토, 공개 로그인/JWKS HTTP 응답 확인이다. 자원·네트워크·권한·인증 설정·DNS·비밀 값을 변경하지 않았으며, 실제 사용자 로그인이나 Junseo 배포를 시험한 결과는 아니다.

## 1. Azure 실제 상태

| 항목 | 2026-10-02 관측 |
| --- | --- |
| 구독 | 현재 Azure CLI의 `Azure subscription 1` |
| 리전/리소스 그룹 | Korea Central / `studylog-rg` |
| 앱 플랜 | `studylog-plan`, Linux B2, 인스턴스 1개, 앱 21개 |
| 앱 실행 상태 | 조회 시 Running 11개, Stopped 10개 |
| 중앙 Auth runtime | `liliplanet-auth`, Java 17, Always On 켜짐 |
| 중앙 도메인 | Auth runtime에 `auth.liliplanet.net`, `user.liliplanet.net`, `subscription.liliplanet.net` 연결 |
| 공통 Login | `liliplanet-login`에 `login.liliplanet.net` 연결, Running |
| 기존 Auth/Login 보안 설정 | HTTPS-only 꺼짐. Auth minimum TLS 1.2. 이번에 설정을 변경하지 않음 |
| PostgreSQL | `studylog-db`, PostgreSQL major 16, B1ms, 32GiB P4/120 IOPS, HA 없음 |
| DB 운영 설정 | max_connections=100 사용자 override, 백업 7일, 저장공간 자동 증가 꺼짐 |
| DB 네트워크 | public access 켜짐. 전체 IPv4 허용 `AllowAll`과 Azure 서비스 허용 규칙 2개 존재 |
| DB 개수 | 목록 15개 중 기본/관리 DB 3개, 애플리케이션 DB 12개 |
| 기존 Storage | `lilitourartifacts`, Standard LRS, anonymous Blob access 꺼짐, minimum TLS 1.0 |
| 기존 Key Vault | `studylog-kv`, soft delete 켜짐, public network 켜짐, RBAC authorization 꺼짐(Access Policy 모델) |
| Junseo 자원 | 이번 목록에 Junseo 이름의 Web App·애플리케이션 DB 없음 |

공유 Storage를 Junseo에 재사용하거나 minimum TLS를 올리는 것은 기존 사용자를 확인한 뒤 결정한다. 기존 Key Vault에 Managed Identity를 연결할 때도 현재 Access Policy 모델에 맞는 권한안이 필요하다. 실제 비밀 값은 문서와 채팅에 수집하지 않았다.

### 최근 7일 지표

조회 구간: **2026-09-25 20:42:20 ~ 2026-10-02 20:42:20 KST**. UTC로는 2026-09-25 11:42:20 ~ 2026-10-02 11:42:20. Azure Monitor에서 1시간 간격 Average/Maximum을 조회했으며 각 지표의 평균 샘플 168개를 얻었다.

| 자원/지표 | 시간별 평균의 평균 | 반환된 시간별 maximum 중 최대 |
| --- | ---: | ---: |
| B2 CPU | 13.46% | 93.00% |
| B2 메모리 | 77.46% | 90.00% |
| DB CPU | 10.53% | 77.91% |
| DB 메모리 | 61.11% | 85.93% |
| DB `active_connections` | 20.02 | 30 |
| DB 저장 사용률 | 13.68% | 13.68% |

지표 이름 `active_connections`는 이번 Azure Monitor 관측값이다. SQL의 active/idle 세션 분류, 앱별 pool 합계, 최소 권한 역할을 직접 조사한 값은 아니다. 측정 구간의 관측이 미래 부하나 장애 여유를 보장하지 않는다.

**판단:** 기존 B2에 Junseo JVM을 추가하는 것은 우선안에서 제외하고 전용 플랜에서 시험한다. 기존 DB는 사용 여유가 있어 보이지만, 앱별 pool·권한·넓은 방화벽의 영향 조사를 완료한 뒤 공유 여부를 정한다. B1/B2 선택은 Junseo 이미지 처리 부하를 측정한 뒤 한다.

기존 방화벽은 접속 중인 앱을 확인하고 좁은 허용 규칙을 먼저 추가한 뒤 단계적으로 전환한다. 이번 조회 결과만으로 넓은 규칙을 삭제하지 않는다.

## 2. 공통 로그인 계약 확인

| 항목 | 확인 결과 |
| --- | --- |
| 로그인 UI | `https://login.liliplanet.net` HTTP 200, 제목 `liliplanet - 로그인` |
| JWT 공개키 | `https://auth.liliplanet.net/.well-known/jwks.json` HTTP 200, RSA/RS256 공개키 1개, private key 필드 없음 |
| issuer | 운영 `APP_JWT_ISSUER`는 정확히 `auth.liliplanet.net` |
| 로그인 진입 | `?client=<제품 audience>&redirect=<인코딩한 callback URL>` |
| 인증 구현 | 중앙 UI와 Auth API가 이메일/비밀번호·소셜 로그인, RS256 JWT 발급을 담당 |
| callback | 현재 중앙 UI는 `?token=<JWT>`를 HTTPS callback에 전달 |
| 반환 주소 허용 | 현재 확인한 UI/Auth 소스는 LiliPlanet 도메인과 localhost 계열 host를 허용 |
| Junseo 등록 | 운영 `JWT_ALLOWED_AUDIENCES`에 `junseo-api` 없음. 확인한 플랫폼 소스 기본 allowlist와 로컬 Admin registry에도 없음 |
| 토큰 수명 | 플랫폼 소스 기본값은 43,200,000ms(12시간). 운영 JWT_EXPIRATION_MS 항목은 조회 결과에 없으며 실제 발급 토큰의 수명은 미검증 |
| 갱신/폐기 | 확인한 중앙 Auth 소스에 refresh/logout/revoke endpoint 없음. 운영에서 지원하는 별도 계약이 있는지는 미확인 |
| OIDC discovery | `/.well-known/openid-configuration`은 HTTP 403. 일반적인 discovery 기반 OIDC/PKCE 서버라고 가정하지 않음 |

서버 JWT 검증 항목은 **RS256 서명·JWKS kid·issuer 정확 일치·Junseo audience·exp·비어 있지 않은 sub/jti**다. 다른 LiliPlanet 제품의 audience 토큰은 거부한다. email과 표시 이름을 계정 연결 키로 사용하지 않는다.

공통 로그인 사용만으로 Junseo 제품 가입·초대 승인·동의·사진 접근권한·로그아웃 후 서버 폐기가 자동 구현되지는 않는다.

## 3. Junseo 로그인 전환안

다음 식별자는 코드와 등록 작업을 준비하기 위한 후보이며 운영에 적용한 상태가 아니다.

| 항목 | 준비안 |
| --- | --- |
| 제품명 | Junseo |
| service code | `junseo` |
| API JWT audience | `junseo-api` |
| API/callback host | `junseo-api.liliplanet.net` — 사용자 도메인 선호 확인 중 |
| HTTPS callback | `https://junseo-api.liliplanet.net/auth/callback` |
| 앱 scheme | 현재 `junseo`, 앱 복귀용 URL을 로그인 transaction과 결합해 설계 |
| 공개 로그인 주소 | `https://login.liliplanet.net` |
| API 검증 | `LILIPLANET_AUTH_ISSUER`, `LILIPLANET_AUTH_JWKS_URL`, `JUNSEO_JWT_AUDIENCE` |

### 사용자와 토큰 흐름

1. 앱의 로그인 버튼이 시스템 브라우저로 공통 Login을 연다. 이메일·비밀번호와 소셜 인증은 공통 플랫폼에서 처리한다.
2. Auth는 `aud=junseo-api` JWT를 발급해 허용된 HTTPS callback으로 전달한다.
3. callback은 JWT를 검증하고 로그인 시작 요청과 결합한다. 앱 복귀에는 PKCE/transaction에 결합한 짧은 일회용 교환 코드 방식을 우선 검토한다. JWT 원문을 딥링크에 다시 싣는 기존 제품 예제를 그대로 복제하지 않는다.
4. 앱은 검증한 중앙 JWT를 OS secure storage에 저장하고 Junseo API를 호출한다. callback query의 토큰은 즉시 제거하고 App Service HTTP/access log·오류 추적에서도 수집되지 않도록 구성한다.
5. 서버는 중앙 `(issuer, sub)`에 대응하는 Junseo 프로필을 조회/활성화한다. 닉네임·친구 코드·가입 허용·제품 동의는 Junseo onboarding에서 처리한다.
6. 앱·위젯·알림 확장은 공유 Keychain을 사용하고 REST와 WebSocket은 같은 JWT 검증 규칙·사용자 매핑을 따른다.
7. 만료/401에서는 토큰을 지우고 재로그인한다. 중앙 refresh 계약을 제공하게 되면 앱/확장의 동시 갱신을 조정한다.

HTTPS callback과 연결할 scheme/universal link, 로그인 시작·응답 결합 방식은 Expo 57 공식 문서와 실제 iOS 빌드에서 검증해야 한다. 위 흐름을 실제로 실행하거나 서명한 상태는 아니다.

### 기존 사용자 ID 충돌 방지

현재 Junseo의 `users.id`는 독립 bigint identity이고, JWT `sub`를 그대로 숫자로 변환해 이 ID로 사용한다. 중앙 Auth의 `sub`도 현재 숫자처럼 보이지만 서로 같은 사용자라는 보장이 없다.

- `users` 또는 별도 identity 테이블에 `(external_issuer, external_subject)` unique mapping을 추가한다.
- JWT의 subject를 해당 매핑으로 해석하고 기존 내부 ID를 친구·사진·댓글·채팅 FK에 계속 사용한다.
- 이메일이나 숫자 ID가 같다는 이유만으로 기존 계정을 자동 연결하지 않는다. 기존 데이터가 있으면 소유권을 확인하는 별도 전환 절차를 사용한다.
- Junseo는 운영용 비밀번호·자체 JWT signing key를 더 이상 관리하지 않으며 공통 계정 DB에 직접 연결하지 않는다.

## 4. 변경할 코드와 플랫폼 등록

| 작업 경계 | 대상과 변경 |
| --- | --- |
| Junseo backend | `SecurityConfig`를 RS256/JWKS 검증으로 전환, claim validator 추가 |
| Junseo backend | `CurrentUserArgumentResolver`·`RealtimeHandler`에 중앙 subject → 제품 ID 매핑 적용 |
| Junseo DB | identity mapping migration, 비밀번호 필드/기존 개발 인증의 전환안, 제품 가입 상태 |
| Junseo auth/API | 자체 signup/login 발급 경로를 운영에서 제거, callback/교환/onboarding 계약 추가 |
| Junseo mobile | 로그인/가입 UI를 공통 로그인 진입 + 닉네임 설정으로 전환, API/auth/tokenStore 연결 수정 |
| Junseo iOS | 공유 Keychain·entitlement와 위젯/알림 확장의 만료·로그아웃 처리 |
| 공통 플랫폼 소스 | auth-api와 platform-api 기본 audience allowlist에 `junseo-api` 추가·검증 |
| 공통 플랫폼 운영 | 기존 값을 보존해 `JWT_ALLOWED_AUDIENCES` 추가. 현재 설정은 변경하지 않음 |
| 공통 Admin | 서비스 registry에 `junseo`, audiences `[junseo-api]`, health 경로 등록. 현재 기능에는 AI 요구가 없으므로 AI credential·구독·모델 설정은 추가하지 않음 |
| Azure/DNS | 후보 API HTTPS domain, TLS, callback, 전용 앱 플랜, DB/Blob/Key Vault 접근 구성 |

현재 플랫폼 working tree에는 Admin 평가 기능 관련 미커밋 작업이 있다. 이번 확인에서는 해당 저장소를 수정하거나 pull하지 않았다. 플랫폼 원격 main과 로컬 HEAD는 조회 시 `74184407549b79a4b37d97e6217c89426f6259c7`로 같았지만, Azure 배포 artifact가 이 SHA와 같은지는 확인하지 않았다.

## 5. 인증 관련 남은 확인과 검증

- [ ] `junseo-api` audience 운영 등록과 실제 로그인 토큰 발급 확인.
- [ ] callback domain/TLS와 iOS 시스템 브라우저 복귀 확인.
- [ ] 다른 audience·변조·만료·필수 claim 누락·JWKS 회전/장애 회귀 테스트.
- [ ] 기존 Junseo ID와 중앙 sub의 숫자 충돌, 같은 이메일, 동시 첫 가입의 중복 프로필 방지.
- [ ] 제품 초대 대상·onboarding·닉네임·동의와 제품 데이터 권한 확인.
- [ ] 웹 callback과 앱 복귀의 transaction/PKCE, 재사용·취소·만료·잘못된 반환 URL 검증.
- [ ] 앱/위젯/확장에서 잠금·오프라인·토큰 만료·재로그인·로그아웃 캐시 동작.
- [ ] 중앙 refresh/개별 폐기/전체 계정 탈퇴 계약 확인. 미지원 기능을 지원한다고 기록하지 않음.
- [ ] 제품 로그아웃과 중앙 계정 비활성화 시 이미 발급된 JWT·WebSocket이 어떻게 차단되는지 별도 설계.
- [ ] 콜백 JWT·Authorization·사진 URL·푸시 토큰·개인정보 본문이 로그와 추적에 남지 않는지 확인.

## 6. 확인 근거

- Azure CLI: account metadata, appservice plan/webapp, PostgreSQL/firewall/database/max_connections, Storage, Key Vault, 비밀이 아닌 인증 설정 항목만 출력한 appsettings 조회.
- Azure Monitor: 위 7일 구간의 App Service plan `CpuPercentage/MemoryPercentage`, PostgreSQL `cpu_percent/memory_percent/active_connections/storage_percent`.
- 공개 HTTP: Login UI·JWKS·OIDC discovery status 조회. 토큰/비밀번호를 입력하거나 계정을 생성하지 않음.
- 플랫폼 소스: `/Users/obst/personal_project/liliplanet-platform`의 `AGENTS.md`, `NEW_SERVICE_ONBOARDING.md`, auth/login/user 구현과 Admin registry.
- 플랫폼 기준 문서: `/Users/obst/Documents/obst/개인프로젝트/liliplanet/00-새서비스-로그인-구독-런북.md`, `PLATFORM_MONOREPO.md`, `SERVICE_PREWORK_REQUESTS.md`, `SOURCE_MAP.md`, `INTEGRATION_GUIDE.md`.
- 기존 모바일 예제: Lilitour의 공통 로그인·HTTPS callback·JWKS 검증 구현. Junseo에 그대로 복사한 상태는 아님.

비용은 이번 조사에서 재산정하지 않았다. 인계 문서의 비용은 실행 시 현재 단가와 구독 혜택을 다시 확인한다.
