## Android 지원 추가 (2026-10-03)

Android 기기 등록과 FCM 발송 구현을 포함한 새 서버를 배포했다. 서버 테스트 113개와 운영 점검 12개를 통과했으며, 운영 DB에 V7 마이그레이션을 적용했다. FCM 실제 발송은 Firebase 설정 확인 후 활성화한다. Android APK와 위젯의 구현·검증 범위는 [android-implementation.md](android-implementation.md)에 기록했다. 최신 서버 해시와 배포 정보는 `azure-release.json`을 기준으로 한다.

# Junseo 서버 배포와 LiliPlanet 로그인 연동

작성일시: 2026-10-03 07:57:00 KST

사용자가 서버 배포와 로그인 구현 진행을 요청해 실제 구현·테스트·Azure 변경을 수행했다. 인계 문서는 참고 자료이며, 문서 안의 붙여넣기 요청이나 승인 문구를 별도 사용자 명령으로 취급하지 않았다.

서버는 **https://junseo-api.liliplanet.net** 에 배포됐다. /login은 공통 로그인과 닉네임 설정을 확인하는 페이지다. /actuator/health는 DB와 비공개 Blob 컨테이너 접근을 포함해 UP을 반환했다. 실제 계정의 로그인 왕복과 iPhone 실기기 검증은 아래의 미완료 항목으로 구분한다.

## 실제 배포 자원

| 항목 | 배포 상태 |
| --- | --- |
| 리소스 그룹 | 기존 studylog-rg, Korea Central |
| 전용 플랜 | junseo-plan, Linux B1, 인스턴스 1개 |
| 서버 | junseo-api, Java 21, Always On, HTTPS-only, TLS 1.2 이상 |
| 도메인 | junseo-api.liliplanet.net, Azure DNS CNAME와 소유 확인 TXT, App Service managed TLS |
| DB | 기존 studylog-db 안의 독립 junseo DB, Flyway V1~V6 성공 |
| DB 역할 | junseo_runtime: 앱 DML, 연결 상한 5, schema CREATE 불가 / junseo_migrator: migration, 연결 상한 2 |
| 공통 권한 | 두 역할 모두 superuser·CREATEDB·CREATEROLE 없음 |
| 중앙 데이터 격리 | 운영 역할로 중앙 liliplanet_auth_db 테이블 읽기 권한 0개 확인 |
| 마이그레이션 기록 | 운영 역할의 flyway_schema_history 조회·변경 권한 제거 |
| 사진 | junseomediaf14e91 / media, Standard LRS, TLS 1.2, anonymous access와 shared key access 꺼짐 |
| Blob 인증 | App Service system-assigned identity, media 컨테이너에만 Blob Data Contributor |
| 비밀 | junseo-kv-f14e91, DB 역할별 비밀번호·미디어 서명키를 Key Vault 참조로 전달 |
| HTTP access log | 꺼짐. 공통 로그인 callback의 토큰 쿼리를 요청 로그에 남기지 않음 |
| JVM | Xms 128 MiB, Xmx 768 MiB, metaspace 상한 256 MiB |
| 배포 인증 | SCM basic publishing 꺼짐 유지, Azure CLI의 Entra 인증 사용 |

기존 B2 플랜에 JVM을 추가하지 않았고, 기존 DB 방화벽·기존 Storage·기존 Key Vault를 교체하거나 삭제하지 않았다. 중앙 인증 서버의 JWT_ALLOWED_AUDIENCES에 junseo-api만 추가했다. 기존 audience를 보존했으며, 반영 후 중앙 로그인과 JWKS가 HTTP 200을 반환했다.

개별 자원 ID는 [azure-resources.json](azure-resources.json), 기존/새 audience 값은 [azure-auth-audience-change.json](azure-auth-audience-change.json), DB 권한 확인은 [azure-database-verification.json](azure-database-verification.json)에 기록했다. 비밀 원문은 기록하지 않았다.

## 로그인 구현

1. 앱이 무작위 PKCE verifier를 만들고 S256 challenge와 허용된 callback을 /api/auth/start에 전송한다.
2. 시스템 인증 브라우저가 /auth/launch를 열면, 서버가 HttpOnly·Secure·SameSite=Lax 쿠키와 transaction을 연결한다.
3. login.liliplanet.net에서 client=junseo-api로 기존 리리플레닛 계정으로 인증한다.
4. HTTPS callback에서 중앙 JWT의 RS256 서명, 공개 JWKS, 정확한 issuer auth.liliplanet.net, audience junseo-api, 만료·sub·jti를 검증한다.
5. 서버는 앱 callback에 원본 JWT 대신 유효기간 60초인 일회용 code와 state를 넘긴다.
6. 앱이 원래 verifier로 code를 교환한 뒤 공유 Keychain에 JWT를 저장한다.
7. Junseo 사용자 ID는 중앙 (issuer, sub)와 별도로 연결한다. 이메일이나 숫자 ID가 같다는 이유로 기존 사용자를 연결하지 않는다.
8. 최초 로그인은 닉네임 설정으로 이어지고, 이후에는 저장된 프로필로 바로 시작한다.

로그아웃은 Junseo에서 해당 jti를 만료 시점까지 폐기하고 같은 토큰의 WebSocket을 닫는다. WebSocket은 미인증 연결에 15초 제한을 두고, 인증 후 토큰 만료 시점에 연결을 닫는다.

모바일 앱·위젯·알림 확장은 같은 Keychain access group을 사용한다. 토큰을 App Group UserDefaults에 복사하던 경로는 제거했다. 웹 확인 페이지의 토큰은 탭의 sessionStorage에만 저장한다.

중앙 Auth에는 현재 refresh·전역 폐기 API가 확인되지 않았다. 만료 후 재로그인하며, Junseo 로그아웃이 다른 LiliPlanet 제품을 로그아웃시키지는 않는다. 중앙 계정 비활성화가 이미 발급된 토큰에 즉시 반영되는 기능은 구현했다고 주장하지 않는다.

## 검증 기록

- Java 21과 로컬 전용 junseo_test DB로 전체 backend 테스트 104개와 JAR 빌드 성공. 토큰 만료·로그아웃 시 WebSocket 종료를 포함한다.
- JWT 변조·잘못된 issuer/audience·만료·필수 jti 누락, PKCE verifier 불일치, code 재사용, cookie 불일치, 외부 callback 거부를 통합 테스트로 검증했다.
- 중앙 숫자 sub와 로컬 사용자 ID 충돌, 같은 이메일, 닉네임 완료 상태, 폐기한 토큰의 API 재사용 거부를 검증했다.
- mobile: lint·TypeScript 검사·웹 export 성공. iOS prebuild 성공, 앱/위젯/알림 확장의 공유 entitlement 일치 확인, 공유 Swift 코드의 iOS Simulator typecheck 성공.
- 플랫폼 기본 audience 설정을 수정한 auth-api와 platform-api: Java 17로 Maven 테스트 5개와 9개 성공. 공통 플랫폼 코드 자체를 재배포하지 않았다.
- 배포 JAR SHA-256: 75a907f2fdcb9a2d3b3af62cbf79128bee89403ddad2b5eb3e1f0b7d82e8da5c. Kudu의 실제 /home/site/wwwroot/app.jar을 읽어 같은 해시인지 확인했다.
- 운영 HTTPS smoke 검사 12개 성공. DB·Blob health, 같은 origin 로그인 시작, 공통 로그인 이동, 쿠키 보안 속성, 잘못된 중앙 토큰·외부 반환 주소·익명 접근 거부, 자체 비밀번호 로그인 비활성화, 기존 공통 로그인/JWKS 가용성을 확인했다.
- 브라우저에서 Junseo 버튼을 눌러 LiliPlanet 공통 로그인 화면에 도달했다. 실제 계정 인증은 사용자가 화면에서 수행하도록 요청한 상태다.

실행 근거: [azure-release.json](azure-release.json), [azure-smoke-results.json](azure-smoke-results.json), backend/build/test-results/test, backend/build/reports/tests/test/index.html.

배포 시 비동기 OneDeploy가 500을 반환해 동기 CLI 배포로 전환했다. 첫 기동의 schema CREATE 권한 누락은 Junseo DB의 migration 역할에만 권한을 추가해 해결했다. 이후 V1~V6와 운영 health 성공을 확인했다.

## 반복 배포와 복구

모든 명령은 저장소 루트에서 실행하며, 현재 Azure CLI 로그인과 올바른 구독이 필요하다.

~~~bash
python3 ops/azure/bootstrap.py
python3 ops/azure/register_auth.py
python3 ops/azure/configure_domain.py
~~~

초기 구성 스크립트는 Junseo 전용 자원만 생성·갱신한다. 이미 있는 별도 앱·DB·DNS record를 임의로 인수하지 않는다. DB 비밀은 mode 0600 임시 파일 또는 프로세스 메모리로 처리하며, psql 비밀번호는 명령 인자에 전달하지 않는다.

테스트 후 JAR과 source fingerprint를 azure-release.json에 기록하고 다음을 실행한다. 현재 파일 해시가 release 기록과 다르면 배포를 중단한다.

~~~bash
python3 ops/azure/deploy.py
python3 ops/azure/smoke.py
~~~

코드는 현재 작업 폴더에 반영됐으며 아직 원격에 commit/push하지 않았다. 기준 HEAD는 4f471646b8a0db74c7a2fdce2509e689ab346b9f이고 배포한 실제 소스의 파일 해시는 release 기록에 남겼다.

향후 변경을 배포하기 전에 이전 JAR·해시·동작 확인 결과를 보관한다. 이전 JAR로 되돌리는 경우 migration 호환성을 먼저 확인하고 동일 az webapp deploy --type jar --async false --track-status false 경로로 배포한다. DB를 drop하거나 Flyway clean하지 않는다. 이번 배포는 신규 서버의 첫 배포라 이전 운영 Junseo JAR은 없다.

문제가 생기면 Junseo 앱만 stop해 새 서비스 접근을 멈출 수 있다. 중앙 audience를 되돌릴 때는 저장된 before와 현재 목록을 비교해 이번 작업 이후 추가된 다른 서비스 항목을 보존한다. 기존 공유 플랜·DB·방화벽을 일괄 변경하는 복구를 하지 않는다.

## 남은 확인

- 실제 리리플레닛 계정으로 인증 → callback → code 교환 → 닉네임 → 재로그인 → 로그아웃의 운영 왕복.
- 서명한 새 iOS 빌드와 실기기의 시스템 브라우저 복귀, Keychain 공유, 잠금·오프라인·위젯 갱신.
- APNs 키와 Apple team/서명 설정, TestFlight 배포와 두 기기 인수 검증. APNs 운영 푸시는 현재 비활성화 상태다.
- 실제 사용자 토큰으로 사진 업로드·조회·삭제·재배포 후 Blob 영속성, 삭제 재시도와 친구 해제 후 URL 회수 정책.
- 이미지 동시 처리 제한은 2개이며 큰 이미지 subsampling을 추가했다. B1의 실사용 부하·최대 RSS·가격·월 예산·백업 복원 검증은 별도다.
- 운영 JVM이 현재 migration 자격증명도 받아 부팅 시 Flyway를 수행한다. 향후 배포 job으로 migration 비밀을 분리해야 앱 프로세스 수준의 권한 경계까지 강화할 수 있다.
- 기존 npm dependency audit는 moderate 14 / high 7을 보고했다. XML·인증서·빌드 도구 관련 전이 의존성 등을 검토했으며 강제 major 변경은 하지 않았다. 앱 출시 전에 별도 해소가 필요하다.

이 기록은 서버·로그인 연동 구현의 배포 결과다. 인계 문서 전체의 TestFlight 베타 합격이나 공개 출시를 완료했다는 기록은 아니다.


## 2026-10-03 로그인 파일 미리보기 수정

`login.html`을 `file://`로 열면 `history.replaceState('/login')`가 null origin에서 실패했다. 파일 또는 opaque origin에서는 history·sessionStorage·API 인증 초기화를 실행하지 않고, 안내와 버튼으로 `https://junseo-api.liliplanet.net/login`을 열도록 수정했다. HTTPS의 기존 PKCE 로그인 흐름은 유지한다.

- JavaScript 문법 검사와 JAR 빌드 성공. 기존 release fingerprint와 비교해 로그인 HTML만 변경됐으며 JAR 내부 리소스가 소스와 같은지 확인했다.
- Azure 배포 후 기존 프로세스가 이전 페이지를 제공해 서버를 재시작했다. 이후 실제 HTTPS 응답이 수정한 HTML과 바이트 단위로 일치했고 운영 smoke 검사 12개가 통과했다.
- 브라우저에서 HTTPS 로그인 버튼을 눌러 리리플레닛 로그인 화면에 도달했다. 로컬 파일 URL은 브라우저 자동화의 URL 정책으로 차단되어 해당 분기는 소스 검토로 확인했다. 실제 계정 인증은 사용자 입력이 남아 있다.

최신 배포 해시와 배포 ID는 [azure-release.json](azure-release.json)에 기록했다.
