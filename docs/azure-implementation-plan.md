# Junseo Azure 구현 계획

**2026-10-03 실행 결과:** 이 문서는 최초 분석 당시의 계획을 보존한다. 사용자 후속 요청으로 서버 배포와 공통 로그인 연동을 구현했다. 현재 상태·검증·남은 항목은 [배포 기록](azure-deployment.md)을 기준으로 본다.

작성일: 2026-10-02 (한국시간)

갱신일시: 2026-10-02 20:50 KST

**사용자 결정 반영: Junseo 로그인은 LiliPlanet 공통 로그인을 사용한다.** 아래 계획은 Junseo 자체 이메일/비밀번호 인증을 운영용으로 강화하는 대신, 공통 로그인·JWT 검증·서비스 프로필 연결로 전환했다. Azure 실측과 연동 계약은 [Azure 환경과 공통 로그인 확인](azure-environment-and-liliplanet-auth.md)에 기록했다.

이번 요청에 따라 현재 폴더의 저장소를 갱신하고, 인수인계 문서를 실제 코드와 비교해 후속 작업을 정리했다. 기능 구현과 배포는 후속 작업이다. 인수인계 문서의 권고, 과거 Azure 관측값, 붙여넣기 요청문은 참고 자료로 구분한다.

## 1. 작업 기준과 이번 확인 결과

| 항목 | 결과 |
| --- | --- |
| 작업 폴더 | `/Users/obst/personal_project/junseo` |
| 원격 저장소 | `https://github.com/obst2580/junseo` |
| 기존 브랜치 | `ccr-c3b66bd5-pqfsqa` — 파일이 없는 초기화 커밋 `745a266` |
| 구현 기준 브랜치 | `claude/eager-gauss-5935za` — 원격 추적 브랜치로 전환 |
| 현재 HEAD | `4f471646b8a0db74c7a2fdce2509e689ab346b9f` |
| pull | 기존 브랜치와 구현 기준 브랜치 모두 `git pull --ff-only` 완료 |
| 인계 기준과 차이 | 동일 SHA, 변경 없음 |
| 구조 | Java 21 / Spring Boot 4.1.1 / PostgreSQL / Flyway V1~V5, Expo 57 / React Native 0.86.3 / iOS 위젯·알림 확장 |
| 테스트 선언 | `@Test` 메서드 96개 확인. 이번 작업에서는 실행하지 않음 |
| CI | 저장소에는 iOS 빌드 workflow 1개. 이번 작업에서 원격 실행 결과는 조회하지 않음 |
| Azure | 2026-10-02에 Azure CLI로 자원·운영 인증 설정·7일 지표를 읽기 전용 확인. 리소스 변경과 가격 재산정은 하지 않음 |

원본 인계 자료: [Junseo_Azure_Handoff_2026-10-02.md](Junseo_Azure_Handoff_2026-10-02.md).

## 2. 로컬 개발 환경

| 도구 | 실제 확인 | 후속 준비 |
| --- | --- | --- |
| Java | Homebrew JDK 21.0.10 설치, 바이너리 실행 확인. macOS 등록 목록에는 JDK 17만 표시 | 프로젝트 명령에서 Java 21의 `JAVA_HOME` 지정 |
| Gradle | 저장소에 wrapper 8.14.3과 wrapper JAR 존재 | Java 21로 wrapper 실행·의존성 해결·빌드 확인 |
| Node / npm | Node 25.5.0 / npm 11.8.0, 현재 CI는 Node 22 | 프로젝트 Node 버전을 CI와 맞춘 뒤 `npm ci` |
| PostgreSQL | 클라이언트 16.12 설치. `localhost:5432`는 연결 수락 | 실제 서버 버전·전용 개발/테스트 DB·역할 확인. 현재 응답 서버의 버전은 미확인 |
| Xcode | `/Applications/Xcode.app`에 Xcode 26.2 설치, 명령 실행 확인. 기본 선택은 Command Line Tools | 프로젝트 명령에서 `DEVELOPER_DIR` 지정. 시뮬레이터·서명·실기기는 별도 확인 |
| 의존성 | `mobile/node_modules`, `backend/build` 없음 | 설치와 첫 실행 기록 확보 |
| Azure / GitHub CLI | `az`, `gh` 실행 파일 존재 | 필요 단계에서 계정·구독·권한 확인 |
| Docker | 현재 PATH에서 실행 파일 없음 | 로컬 PostgreSQL로 테스트 환경을 구성할 수 있어 필수 설치 항목으로 두지 않음 |

확인한 프로젝트용 실행 경로는 다음과 같다. 전역 설정 변경 없이 해당 명령에만 적용할 수 있다.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
export PATH="$JAVA_HOME/bin:/opt/homebrew/opt/postgresql@16/bin:$PATH"
```

**첫 테스트 전에 DB 대상을 검증해야 한다.** `backend/src/test/resources/application-test.yml`은 Flyway clean을 허용하며, `TestConfig`는 clean/migrate, `IntegrationTest`는 테이블 truncate를 수행한다. 운영 DB와 기존 업무 DB에 연결하지 않는 전용 테스트 DB·역할을 준비하고, 테스트 대상 검증도 보강한다.

## 3. 구현 목표와 확정할 결정

작업 계획의 잠정 목표는 인계 문서의 **10명 초대형 iOS TestFlight 베타**다. 사용자 범위와 배치안은 아직 확정되지 않았다.

| 결정 | 잠정 후보 / 필요한 답 |
| --- | --- |
| 출시 범위 | 10명 초대 베타로 시작할지, 일반 공개까지 포함할지 |
| Azure 배치 | 전용 Linux App Service B1 + 기존 PostgreSQL의 Junseo 전용 DB·역할 + private Blob + Key Vault를 첫 후보로 검증 |
| 월 예산 | 금액과 B1 부하 시험 실패 시 B2 전환 허용 여부. 인계 문서의 가격은 현재 견적으로 확정하지 않음 |
| DB 공유 | 기존 앱의 접속 경로·연결 수·방화벽을 확인해 공유 가능 여부 판단. 변경 영향이 크면 전용 DB 검토 |
| 사진 정책 | 보관기간, 친구 해제 후 새 다운로드 차단 시점, 계정·사진 삭제와 백업 보존기간 |
| 인증 제공자 | LiliPlanet 공통 로그인 사용으로 확정. 제품 서비스 코드 `junseo`, JWT audience `junseo-api`를 준비안으로 사용 |
| 인증 운영 정책 | 중앙 Auth의 토큰 갱신·폐기 계약과 Junseo의 로그아웃/가입 허용 정책. 현재 확인한 중앙 소스에는 refresh/logout/revoke API가 없음 |
| API·로그인 반환 주소 | `junseo-api.liliplanet.net`을 후보로 사용. 도메인 선호 확인 중이며 DNS/배포를 적용한 상태는 아님 |
| Apple 준비 | 개발자 계정·Team·Bundle ID·APNs 키·실제 아이폰 2대 확보 여부 |

24시간은 현재 위젯 피드 조회 범위다. 서버 사진 자동 삭제 기간으로 해석하지 않는다. 접근 회수는 서버의 새 다운로드 차단을 뜻하며, 이미 받은 파일과 스크린샷은 회수할 수 없다.

## 4. 권장 진행 순서

### 0단계 — 실행 기준 확보

- [ ] Java 21·프로젝트 Node 버전·Xcode 실행 경로 고정, 간단한 개발 명령 문서화.
- [ ] 전용 개발 DB와 격리 테스트 DB·역할 준비, 테스트 DB 대상 확인 가드 추가.
- [ ] 기존 서버 테스트 96개와 JAR 빌드 실행, Flyway V1~V5 적용 기록 확보.
- [ ] `mobile`에서 `npm ci`, `npm run lint`, `npm run typecheck` 실행.
- [ ] 로컬 API와 앱의 가입·로그인·사진·친구·채팅 기본 동작 확인.

완료 기준: 실행한 커밋·명령·환경·결과가 기록되고, 기존 실패와 변경 후 실패를 구분할 수 있다.

### 1단계 — P0 운영 설정과 로그 수정

| 작업 | 주요 변경 대상 | 완료 기준 |
| --- | --- | --- |
| 운영 설정 분리·필수 검증 | `backend/src/main/resources/application*.yml`, `common/JunseoProperties.java`, `common/security/Secrets.java`, 신규 운영 설정 검증 코드 | prod에서 미디어 서명키 누락·개발 기본키·dev 동시 활성화 시 기동 실패. 자체 JWT 발급용 비밀키는 공통 Auth 전환 후 운영 경로에서 제거 |
| 공통 JWT 검증 설정 | `common/security/SecurityConfig.java`, 신규 JWT validator | RS256/JWKS, issuer `auth.liliplanet.net`, audience `junseo-api`, 필수 exp/sub/jti 검증. prod에서 자체 HS256 토큰 거부 |
| DB·프록시·CORS 운영 설정 | 운영 프로필, `common/security/SecurityConfig.java` | TLS 검증을 포함한 JDBC 설정, 작은 DB pool부터 검증, 프록시 헤더 처리와 운영 origin 검증 |
| 푸시 로그 최소화·APNs 설정 검증 | `push/LoggingPushSender.java`, `push/PushConfig.java`, 관련 오류 로그 | 이름·사진 URL·메시지/댓글 본문·기기 토큰을 로그에서 제거. 운영 APNs 활성화 정책과 필수 설정 명확화 |
| 앱 API 주소 검증 | `mobile/app.config.js`, `mobile/eas.json`, `mobile/src/lib/config.ts` | preview/production에 실제 HTTPS API 주소 필수. HTTP·localhost·개발 주소 빌드 거부. 앱·WSS·위젯의 주소 일치 |
| 베타 가입 제한 | 서비스 onboarding/membership API, 운영 설정 | 공통 계정으로 로그인한 뒤 Junseo 초대 대상만 서비스 프로필 활성화 가능 |

완료 기준: 잘못된 설정으로 기동/빌드를 시도하는 회귀 검증과 기존 정상 경로 검증 통과.

### 2단계 — P0 사진 저장, P1 권한 회수와 삭제

현재 `MediaConfig`는 `LocalMediaStorage`만 생성하고, `/media/{id}/{variant}.jpg`는 서명·만료와 파일 존재만 확인한다. Blob 환경변수를 추가하는 것만으로 해결되지 않는다.

- [ ] `MediaStorage`의 local/blob 구현 선택과 Blob 설정 추가. Azure SDK 의존성·Managed Identity 접근을 코드에서 준비.
- [ ] private Blob의 업로드·읽기·삭제 구현. 사용자 사진과 템플릿의 객체 경로/컨테이너·열람 정책 구분.
- [ ] 초기에는 서버가 DB 상태·열람 권한을 확인한 뒤 Blob을 제공하는 경로로 설계 검토.
- [ ] 수신자별 grant 또는 권한 버전 등으로 사진 URL 접근을 검증. 친구 해제 정책을 API·위젯·알림 확장에 함께 반영.
- [ ] 삭제한 사진은 Blob 삭제 실패 여부와 무관하게 새 다운로드 차단. 영속적인 삭제 재시도와 고아 객체 정리 설계.
- [ ] 업로드 일부 실패·DB rollback·삭제 실패에서 DB와 Blob 상태 일관성 검증.
- [ ] 템플릿을 버전별 객체 키에 저장하고 업로드 성공 후 DB 포인터 변경. 기존 버전 URL의 내용 유지 검증.
- [ ] 사진 보관/정리 작업과 백업·버전 보존 정책 구현.

주요 대상: `media/MediaStorage.java`, `MediaConfig.java`, `MediaController.java`, `MediaUrlSigner.java`, `LocalMediaStorage.java`, `moment/MomentService.java`, `MomentAccess.java`, `template/TemplateService.java`, `TemplateMediaController.java`, 신규 migration과 회귀 테스트.

완료 기준: 재시작·재배포 후 사진 유지, 다른 사용자의 조회 차단, 친구 해제·사진 삭제 후 동일 URL 재요청 결과가 정책과 일치, 삭제 실패 복구 통과.

### 3단계 — LiliPlanet 로그인 연결, P1 제한과 실시간 연결

- [ ] 중앙 Auth에 `junseo-api` audience를 추가하는 변경안 준비. 기본 allowlist 두 파일과 운영 설정에서 기존 audience를 보존.
- [ ] 앱 자체 이메일/비밀번호 로그인·가입 화면을 공통 로그인 진입과 Junseo 닉네임/onboarding 화면으로 전환. 비밀번호와 소셜 로그인 처리는 공통 플랫폼에 맡김.
- [ ] 시스템 브라우저에서 `https://login.liliplanet.net?client=junseo-api&redirect=...` 실행. HTTPS callback과 앱 복귀 연결.
- [ ] callback의 JWT를 검증하고 주소·로그·브라우저 이력에 원문이 남지 않게 처리. 앱 복귀에는 PKCE/transaction에 결합한 짧은 일회용 교환 코드를 검토하고, 로그인 시작과 응답을 결합해 세션 혼동을 차단.
- [ ] RS256 JWKS 검증을 REST·WebSocket에 함께 적용. `iss`, 자기 audience, `exp`, `sub`, `jti`와 키 갱신/장애 동작 검증.
- [ ] `(issuer, subject)`와 기존 Junseo 내부 사용자 ID의 명시적 매핑·unique constraint 추가. 기존 친구·사진·채팅 FK를 유지하며 이메일 또는 숫자 ID 일치만으로 계정을 연결하지 않음.
- [ ] 공통 로그인 성공과 Junseo 서비스 가입·동의·초대 허용을 분리. 닉네임·친구 코드 등 제품 프로필만 Junseo가 관리.
- [ ] 자체 signup/login/JWT 발급의 운영 경로 제거. 기존 개발·테스트 인증이 필요하면 운영에서 사용할 수 없는 별도 설정으로 유지.
- [ ] 중앙 Auth의 refresh/revoke 계약을 먼저 확정. 미제공 상태에서는 401/만료 때 재로그인하며, Junseo만 별도 refresh JWT를 발급하는 설계를 만들지 않음.
- [ ] 앱·위젯·알림 확장의 공유 Keychain과 entitlement 구성. 기존 UserDefaults 토큰 사본 제거·전환 처리.
- [ ] 위젯/확장의 중앙 토큰 만료·잠금·오프라인·재로그인·로그아웃 캐시 처리. refresh 추가 시 앱/확장의 동시 갱신 조정.
- [ ] 로그아웃은 기기 등록·로컬 토큰·캐시 정리와 서버 폐기를 구분. 중앙 계정 비활성화/탈퇴와 기존 JWT 사용 차단을 검증할 계약 추가.
- [ ] WebSocket의 인증 대기 제한·토큰 만료·사용자 상태 확인, 종료/재인증과 재접속 후 REST 동기화.
- [ ] 가입·로그인·업로드·기기 등록의 IP/계정별 제한과 기기 수·저장량 한도.
- [ ] 이미지 픽셀 상한·디코딩 경로·동시 처리 제한 보강. 현재 최대 5천만 픽셀 경로의 RSS를 측정.
- [ ] API 계약과 사용자 오류 응답을 `docs/api.md`에 반영.

주요 대상: `auth/*`, `common/security/*`, `realtime/*`, `device/*`, `media/ImageProcessor.java`, `mobile/src/lib/api.ts`, `auth.tsx`, `tokenStore.ts`, `widgetBridge.ts`, `realtime.ts`, `mobile/targets/_shared/WidgetShared.swift`, iOS 타깃 설정.

완료 기준: 공통 로그인 → API/위젯/채팅 연결, 다른 audience/변조/만료 토큰 차단, 사용자 ID 충돌 차단, 재로그인/폐기 계약의 회귀 검증 통과. 큰 사진 동시 업로드에서도 OOM 없이 제한 응답.

### 4단계 — CI, 배포 명세와 Azure 확인

- [ ] backend 테스트용 격리 PostgreSQL, JAR 아티팩트 빌드 workflow 추가.
- [ ] mobile lint/typecheck workflow 추가, 기존 iOS workflow와 동일 커밋으로 검증.
- [ ] 비밀을 노출하지 않는 liveness/readiness·DB/저장소 상태·APNs 결과 관측 준비.
- [ ] Bicep 또는 동등한 명세 작성. 신규 자원과 기존 참조 자원 구분, 공유 리소스 삭제·교체 여부를 미리보기에서 확인.
- [ ] DB runtime/migration 권한 분리, 최소 권한 Managed Identity/Key Vault/Blob 접근안 작성.
- [ ] APNs `.p8`를 읽는 현재 코드에 맞춰 Key Vault 로딩 또는 안전한 파일 주입 구현.
- [ ] 기존 앱별 송신 IP·접속 경로·플랜 7일 피크·DB 실제 연결 수와 pool 총합 조사.
- [x] 7일 Azure 지표와 현행 방화벽 확인: B2 메모리 평균 77.46%·최대 90%, DB 연결 지표 평균 20.02·최대 30, max_connections=100. 기존 B2 공유 배포 대신 전용 플랜 검증을 우선.
- [ ] 공통 Auth audience·Admin 서비스 registry와 HTTPS callback 도메인 등록. 현재 운영 allowlist와 로컬 registry에는 Junseo가 없음.
- [ ] 방화벽 변경안은 좁은 규칙 추가 → 앱별 점검 → 넓은 규칙 제거 순서와 rollback으로 구체화.
- [ ] 현재 가격·예산, 계정·구독·리소스 범위, 실제 적용할 변경 확인 후 Azure 구성과 배포.
- [ ] 검증한 동일 JAR 배포와 이전 JAR 복구, migration의 이전 코드 호환성 시험.

완료 기준: 변경할 리소스·권한·비용과 복구 절차를 검토할 수 있고, 배포 후 API·DB·Blob·WSS 경로 및 기존 앱 정상 동작을 확인.

### 5단계 — 실기기, 복구와 베타 인수

- [ ] Apple Team·Bundle ID·App Group·APNs 환경을 맞춘 서명 빌드와 TestFlight 준비.
- [ ] 아이폰 2대로 사진·댓글·반응·1:1/단체 채팅·푸시·위젯 전체 흐름 확인.
- [ ] 전경/배경/잠금/강제종료/저전력/알림 거부/오프라인/토큰 만료/서버 재시작 검증.
- [ ] APNs 전송 결과, 확장 수신, 위젯 표시 시간을 따로 기록. 고정 갱신 시간을 보장하는 문구 점검.
- [ ] 초기 부하안: 큰 사진 순차 1개·동시 2~3개, API 동시 10요청, WS 20연결. 최대 RSS·p95·오류율·DB 연결 측정.
- [ ] 인계 문서의 제안 기준(지속 메모리 80% 미만, OOM 0, API 오류율 1% 미만)을 검토하고 p95 목표를 정해 B1/B2 판단.
- [ ] 별도 환경에 DB와 Blob을 함께 복원, 사진 누락·고아 객체·소요시간 기록.
- [ ] 개인정보 최소 로그, 경보 전달, 백업 실패·저장량·예산 알림과 지원 절차 검증.
- [ ] 10명 베타 시작 후 실제 사용량·안정성·비용을 확인하고 100명 확대 여부 결정.

완료 기준: 커밋·환경·기기·시간·결과가 있는 인수 기록을 남기고 출시 차단 결함을 해결.

## 5. 일반 공개 전 추가 작업

베타를 일반 공개로 바꿀 경우 다음을 출시 범위에 포함한다.

- 공통 Auth의 이메일 소유 확인, 비밀번호 복구·변경·본인 재인증 지원을 확인하고 부족한 계약은 플랫폼에서 보완.
- 계정 삭제와 DB·사진·기기·캐시·백업 보존 안내.
- 신고·차단, 서비스 대상 국가/연령, 약관·개인정보·알림 미리보기 정책.
- AdMob 실광고·동의/연령 설정과 번들 글꼴 라이선스 확인.
- 최신 공식 스토어 정책과 적용 법률 검토, App Store 심사 준비.

Redis·다중 인스턴스·고가용성·비동기 푸시 outbox 확대는 실제 사용량과 중단 허용 범위에 따라 후속 설계한다. 현재 WebSocket 연결 목록은 한 JVM 메모리에 있으므로 인스턴스 수만 늘리는 방식으로 확장하지 않는다.

## 6. 다음 착수 단위

첫 착수 단위는 **0단계 실행 기준 확보 → 공통 로그인 식별자/사용자 매핑/JWT 검증 → 1단계 운영 설정·로그·HTTPS 주소 검증**이다. 로그인 연결을 먼저 검증하고 사진 저장/권한 정책을 이어서 적용한다.

각 변경은 필요한 테스트, mobile 변경 시 lint/typecheck, API 문서 갱신을 포함해 검토 가능한 단위로 나눈다. Expo/EAS/React Native API를 수정할 때는 `mobile/AGENTS.md`에 따라 Expo 57 공식 문서를 먼저 확인한다.

현재 상태: 저장소 갱신·코드 비교·도구 확인·Azure 읽기 전용 조사·공통 로그인 계약 확인·계획 갱신 완료. 기능 코드 수정, 의존성 설치, 빌드/테스트, Azure 변경, 배포, 실기기 로그인 검증은 후속 작업으로 남아 있다.
