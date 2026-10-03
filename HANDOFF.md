# Junseo 개발자 인수인계 — 먼저 읽을 문서

기준일: **2026-10-03 KST**. 이 문서는 현재 코드, 실제 운영 환경, 재현 절차와 남은 작업을 연결한다. 초기 검토 문서나 과거 검증 기록보다 이 문서의 현재 상태 설명을 우선한다.

## 1. 인계 시점의 상태

| 구분 | 현재 상태 |
|---|---|
| 저장소 / 작업 브랜치 | `obst2580/junseo` / `claude/eager-gauss-5935za` |
| 기준 코드 | `4f47164` 위에 Azure·공통 로그인·Android 구현. 원본 개발자의 `8962c76` 신고·차단·삭제·재설정 작업도 병합 |
| 운영 서버 | <https://junseo-api.liliplanet.net>, Java 21 / Spring Boot 4.1.1 |
| 로그인 | LiliPlanet 공통 로그인. 운영에서는 Junseo 자체 이메일·비밀번호 가입/로그인 비활성화 |
| 사진 | Azure의 비공개 Blob, 앱 서버의 Managed Identity로 접근 |
| Android | Expo SDK 57, 네이티브 홈 위젯, FCM, 암호화된 공유 세션 구현 |
| 사용자에게 전달한 APK | `0.1.2`, versionCode `3`, `com.junseo.app`, ARM64, Android 8 이상. 사진 업로드 수정 포함 |
| Android 푸시 설정 | Firebase 프로젝트·발송 계정·Azure Key Vault 참조 설정 완료, FCM 활성화 |
| iOS | 기존 Swift 위젯·알림 확장 유지, 공유 Keychain으로 토큰 저장 변경. APNs 운영 키·실기기 검증은 미완료 |
| 운영 DB | **V1~V7 적용 완료**. 병합한 신고 기능 **V8**, 내보낸 사람 목록 **V9** 는 아직 운영에 적용하지 않음 (다음 배포 때 Flyway 가 적용) |
| 현재 소스 | 서버 전체 테스트 **139개**, 모바일 회귀 테스트 **18개**, lint·타입 검사 통과 |

**Git의 최신 소스와 운영 배포본은 다르다.** 운영 JAR은 신고·차단 기능 병합 전 버전이며 SHA-256은 `24e0475de871a92917e625f0e6f0050089e7db732fd11085e1e3c0e8c7aecdc3`이다. [운영 배포 기록](docs/azure-release.json)의 소스 지문은 당시 배포본을 가리킨다. 병합 후 서버·앱을 이번 인계 요청만으로 재배포하지 않았다.

### 변경 이력

- 서버: 독립 App Service/플랜, 독립 DB와 역할, 비공개 Blob, Key Vault, HTTPS 도메인, 공개 health, 배포·검증 스크립트 추가.
- 인증: 중앙 RS256 JWT 검증, PKCE·브라우저 쿠키·일회용 code 교환, 외부 신원 매핑, 최초 닉네임 설정, Junseo 토큰 폐기, WebSocket 만료 처리.
- Android: APNs와 구별되는 기기 등록·FCM 발송, Expo config plugin, Kotlin 위젯·백그라운드 갱신·Keystore 저장소 추가.
- `0.1.1`: 로그인 브라우저 복귀 시 중복 code 교환과 세션 저장/화면 이동 경쟁 수정. 앱 프로세스 재생성 후 PKCE 복원, 오류·재시도 화면 추가.
- `0.1.2`: SDK 57의 multipart 인코더에 맞게 사진을 `expo-file-system`의 `File`로 전송. 이전 `{ uri, name, type }` 방식은 HTTP 요청 전에 실패해 네트워크 오류로 보였음.
- 병합: 원본 개발자의 신고·차단·계정 삭제·비밀번호 재설정·약관 코드를 보존. 배포된 V6/V7을 유지하고 원격 `V6__safety.sql`을 **`V8__safety.sql`**로 이동. 로컬 비밀번호 재설정 서비스는 `local` 인증 모드에서만 생성해 운영 플랫폼 인증 기동과 충돌하지 않도록 처리.
- 병합 후 검토 수정 (원본 개발자):
  - iOS CI `npm ci` 실패(lock 불일치) 수정. 메일 서버 없이 `/actuator/health` 가 503 이던 것(메일 health) 수정. Blob 삭제 오류가 남은 삭제를 멈추던 것 수정.
  - **계정 삭제를 리리플레닛 계정에 연결**: 앱이 삭제 직전 리리플레닛으로 다시 로그인(iOS 는 쿠키를 나누지 않는 창)하고 `exchange { reauth: true }` 로 받은 토큰으로 삭제. 서버는 그 토큰이 5분 안의 로그인(`auth_time` 또는 `iat`)인지 본다. 비밀번호는 묻지 않는다.
  - **내보내기 = 다시 못 들어옴**: 운영자 계정 삭제가 `(issuer, sub)` (로컬은 이메일) 를 `banned_identities`(V9) 에 기록. 다시 로그인하면 403 `ACCOUNT_BANNED`. `GET/DELETE /api/admin/bans`.
  - 로그인 시작 남용 방지 (주소당 대기 20개, 1만 개가 차면 가장 오래된 것부터 정리), FCM 죽은 토큰 정리 · 401 재시도 · 사람당 토큰 10개, 업로드 처리 대기 15초.
  - Android: 사진·동영상·음악 **읽기 권한 제거**(저장만 함), 위젯 새로고침이 쌓이지 않게, 일시적인 Keystore 오류로 로그아웃되지 않게. 앱: 로그아웃 뒤 로그인은 다른 계정을 고를 수 있게(iOS), 위젯 연결 오류가 로그인을 막지 않게, 예전 평문 토큰 사본을 앱 시작 때 삭제.
  - ops: `bootstrap.py` 가 DB 비밀번호를 평문 SQL 대신 서버 형식의 해시로 보낸다. 관리자 토큰을 Key Vault(`admin-token`)에 만들고 연결한다 (`JUNSEO_ADMIN_EMAIL` 은 환경 변수로 주면 설정). `register_auth.py` 는 공유 로그인 서버 재시작 전에 묻고, 플랫폼 저장소 경로는 `--platform-repo` 로 받는다.

## 2. 처음 시작할 때

```bash
git fetch origin
git switch claude/eager-gauss-5935za
git pull --ff-only
```

다른 브랜치에서 작업한다면 이 브랜치를 병합한 후 아래 절차를 따른다. 이미 변경 중인 체크아웃은 먼저 본인의 작업을 보관한다.

필요 도구: Git, Java 21, PostgreSQL 16, Node/npm, Python 3, Azure CLI. Android 로컬 빌드는 Android SDK와 Java 17도 사용한다. 운영 스크립트의 Python 의존성은 `requests`이다.

```bash
python3 -m venv .venv
.venv/bin/python -m pip install requests
cd mobile
npm ci
npm test
npm run lint
npm run typecheck
```

로컬 PostgreSQL에 개발·테스트 DB를 만든다. 아래 비밀번호는 **로컬 개발용 값**이다. 운영 비밀번호는 Key Vault에서 관리한다.

```bash
createuser junseo -P             # 로컬 비밀번호: junseo
createdb junseo -O junseo
createdb junseo_test -O junseo
cd backend
./gradlew test bootJar --console=plain
./gradlew bootRun --args='--spring.profiles.active=dev'
```

테스트는 실제 PostgreSQL의 **`junseo_test` 스키마를 초기화**한다. 테스트 URL을 운영 DB로 바꾸면 안 된다. `dev`는 로컬 이메일 로그인과 데모 데이터를 제공한다. 예: `demo@junseo.app` / `password123!`. Gradle 실행에는 Java 21을 선택한다 (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`은 macOS 예시).

현재 모바일 로그인 UI는 공통 로그인 방식이다. 로컬 `dev` 서버의 이메일 로그인 API와 UI를 바로 연결하는 모드는 아직 없다. 모바일 개발 빌드에서 기존 운영 로그인 동작을 확인하려면 공개 API 주소를 사용한다. 개발 중 새 백엔드의 인증까지 검증하려면 별도 HTTPS 개발 서버와 중앙 callback 설정이 필요하다. 운영 사용자 데이터로 테스트할 때에는 테스트 계정과 테스트 친구만 사용한다.

Expo 웹 미리보기의 `http://localhost:8081/auth-callback`은 **운영 반환 주소 허용 목록에 없다**. 로컬 웹에서 플랫폼 인증을 검증하려면 개발용 서버의 `junseo.auth.return-uris`와 CORS 설정을 별도로 맞춰야 한다. 브라우저의 운영 로그인 확인은 <https://junseo-api.liliplanet.net/login>을 사용한다. HTML 파일을 직접 `file://`로 열면 HTTPS 페이지 안내로 이동하며 로컬 파일에서 인증하지 않는다.

## 3. Azure 서버 정보

| 항목 | 값 |
|---|---|
| 구독 | `f14e91e2-b819-4cd6-ac39-e4a3909c17b9` |
| Tenant | `59a2b2e0-4eda-44d9-b0b3-82ee126b5cc3` |
| 리소스 그룹 / 위치 | `studylog-rg` / Korea Central |
| App Service | `junseo-api`, Linux Java 21, Always On, WebSocket 활성화 |
| 전용 플랜 | `junseo-plan`, B1, 인스턴스 1개 |
| API / 로그인 확인 | <https://junseo-api.liliplanet.net> / `/login` |
| Azure 기본 호스트 | <https://junseo-api.azurewebsites.net> |
| 배포 SCM | <https://junseo-api.scm.azurewebsites.net>, Entra 로그인 사용, basic publishing 비활성화 |
| Health | <https://junseo-api.liliplanet.net/actuator/health>, 정상 응답 `{"status":"UP"}` |
| PostgreSQL 호스트 / DB | `studylog-db.postgres.database.azure.com:5432` / `junseo` |
| DB 역할 | `junseo_runtime` (DML, 연결 상한 5), `junseo_migrator` (DDL, 연결 상한 2) |
| 사진 저장 계정 / 컨테이너 | `junseomediaf14e91` / `media` |
| Blob endpoint | `https://junseomediaf14e91.blob.core.windows.net` |
| Key Vault | `junseo-kv-f14e91` |
| 앱 Managed Identity principal | `4c799c33-0c26-4274-be2f-673581d37fb2` |
| DNS / 인증서 | Azure DNS의 CNAME·`asuid` TXT, App Service managed TLS |

DB 서버와 리소스 그룹은 다른 서비스와 공유하지만 Junseo DB·역할·플랜·사진 저장소·Key Vault는 분리했다. 기존 DB 방화벽과 다른 서비스의 저장소는 변경하지 않았다. 앱 identity는 `media` 컨테이너에 Blob Data Contributor, Key Vault secret에 get 권한이 있다. Blob anonymous/shared-key access는 꺼져 있다. 사진 URL은 서버가 서명하며 실제 파일은 API가 Blob에서 읽어 전달한다.

DB는 TLS `verify-full`을 사용한다. Hikari pool은 최대 5 / minimum idle 0 / connection timeout 10초. JVM은 `-Xms128m -Xmx768m -XX:MaxMetaspaceSize=256m`. HTTP access log는 중앙 callback의 JWT 쿼리를 남기지 않도록 꺼 두었다. 오류 조사 때도 JWT·쿠키·기기 토큰을 문서나 로그에 붙이지 않는다.

### 운영 설정 위치

`application-prod.yml` + App Service **Environment variables**에서 설정한다. 비밀 값은 아래 Key Vault 참조이며 저장소에 원문이 없다.

| 환경 변수 | 운영 값 / 관리 방식 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (`dev`, `test`와 동시 사용 금지) |
| `SERVER_PORT` | `80` |
| `JUNSEO_PUBLIC_BASE_URL` | `https://junseo-api.liliplanet.net` |
| `JUNSEO_DB_URL` | `jdbc:postgresql://studylog-db.postgres.database.azure.com:5432/junseo?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory` |
| `JUNSEO_DB_USER` / `JUNSEO_DB_PASSWORD` | `junseo_runtime` / secret `db-runtime-password` 참조 |
| `JUNSEO_MIGRATION_USER` / `JUNSEO_MIGRATION_PASSWORD` | `junseo_migrator` / secret `db-migration-password` 참조 |
| `JUNSEO_MEDIA_SECRET` | secret `media-signing-secret` 참조 |
| `JUNSEO_BLOB_ENDPOINT` | 위 Blob endpoint |
| `JUNSEO_FCM_ENABLED` / `JUNSEO_FCM_PROJECT_ID` | `true` / `junseo-cbca7` |
| `JUNSEO_FCM_SERVICE_ACCOUNT_JSON` | secret `fcm-service-account` 참조 |
| `JUNSEO_APNS_ENABLED` | 미설정 기본값 `false`; Apple 키 확보 후 별도 활성화 |
| `JUNSEO_ADMIN_TOKEN`, `JUNSEO_ADMIN_EMAIL` | 관리자·신고 운영용. 현재 운영 미설정. `bootstrap.py` 를 다시 실행하면 토큰은 Key Vault `admin-token` 참조로 설정되고, 메일은 실행할 때 `JUNSEO_ADMIN_EMAIL` 을 주면 설정된다 |
| `JUNSEO_SMTP_HOST/PORT/USER/PASSWORD`, `JUNSEO_MAIL_FROM` | 병합한 로컬 인증 재설정/신고 메일용. 현재 운영 미설정 (이번 인계 점검에서 확인) |

Key Vault 참조 형식: `@Microsoft.KeyVault(SecretUri=https://junseo-kv-f14e91.vault.azure.net/secrets/<이름>)`. 해당 참조의 상태가 `Resolved`인지 확인한다. 플랫폼 인증은 `prod` 프로필에서 강제되고, 개발 기본 서명키·HTTP 운영 endpoint·로컬 파일 저장소는 ProductionGuard가 거부한다.

운영 프로세스에는 현재 migration 자격증명도 전달되어 **부팅 때 Flyway가 실행**된다. 향후 migration을 배포 job으로 분리하는 작업이 남았다.

## 4. LiliPlanet 로그인

| 항목 | 값 |
|---|---|
| 로그인 UI | <https://login.liliplanet.net> |
| JWT issuer | 문자열 **`auth.liliplanet.net`** — `https://`를 덧붙이지 않음 |
| JWKS | <https://auth.liliplanet.net/.well-known/jwks.json> |
| JWT audience / 중앙 client | `junseo-api` |
| 중앙 callback | `https://junseo-api.liliplanet.net/auth/callback?state=...` |
| 앱 callback | **`junseo://auth`** (`app.config.js` scheme과 Router `auth.tsx` 일치) |
| 운영 웹 반환 주소 | `https://junseo-api.liliplanet.net/login` |

흐름:

1. 앱이 PKCE verifier를 만들고 S256 challenge로 `POST /api/auth/start` 요청. 반환 주소는 서버 allowlist로 제한.
2. 서버가 `state`, `launchUrl` 반환. 앱이 transaction을 SecureStore에 저장한 뒤 시스템 인증 브라우저를 연다.
3. `GET /auth/launch?state=...`가 HttpOnly·Secure·SameSite=Lax 쿠키를 설정하고 중앙 UI로 이동 (`client=junseo-api`, HTTPS redirect).
4. 중앙 JWT가 HTTPS callback으로 들어오면 RS256 서명·issuer·audience·만료·sub·jti·쿠키·state를 검증.
5. 앱 URL에는 JWT 대신 **60초짜리 일회용 code**와 state만 전달.
6. 앱이 원래 verifier로 `POST /api/auth/exchange` 호출. 서버가 `accessToken`, `expiresAt`, `user`, `needsOnboarding` 반환.
7. 토큰 저장을 마친 후 앱 상태와 화면을 변경. 최초 로그인은 닉네임 설정 (`PATCH /api/me`), 이후 홈으로 이동.

로그인 transaction TTL은 10분, 교환 code는 60초·1회. callback 처리와 브라우저 결과가 겹쳐도 교환/저장은 한 번만 실행한다. 브라우저 dismiss 이후 들어오는 Android intent와 앱 프로세스 재생성도 저장된 PKCE transaction으로 복구한다. 중요한 구현: `PlatformLoginFlow.java`, `PlatformJwtConfig.java`, `PlatformIdentityService.java`, `mobile/src/lib/loginTransaction.ts`, `platformLogin.ts`, `auth.tsx`, `mobile/src/app/auth.tsx`.

중앙 `(issuer, sub)`를 Junseo 내부 사용자 ID로 연결한다. 이메일이나 중앙 숫자 sub가 같다는 이유로 로컬 계정에 합치지 않는다. 앱 세션은 iOS 공유 Keychain, Android Keystore AES-GCM 저장소, 웹 탭 sessionStorage에 저장한다. 위젯의 App Group UserDefaults에 JWT를 복사하던 iOS 경로는 제거했다.

`POST /api/auth/logout`는 Junseo `revoked_tokens`에 jti를 만료까지 기록하고 해당 WebSocket을 닫는다. **다른 LiliPlanet 서비스의 전역 로그아웃은 아니다.** 중앙 refresh/전역 폐기 API는 확인되지 않아 만료 후 재로그인한다. 계정 비활성화의 즉시 반영도 아직 별도 연동이 필요하다.

WebSocket `/ws`는 첫 프레임 `{"type":"auth","token":"..."}`으로 인증한다. URL 쿼리에 토큰을 붙이지 않는다. 인증 전 15초 timeout, 인증 후 JWT 만료 시 연결 종료, 플랫폼 jti 폐기 확인을 구현했다. 로컬 비밀번호 토큰은 `Sessions`의 `ver` 검사도 유지한다.

**현재 B1 한 인스턴스를 유지해야 한다.** 로그인 transaction/code와 WebSocket hub는 메모리에 있다. 재시작 시 진행 중인 로그인은 다시 시작해야 하며, 여러 인스턴스로 확장하기 전에 transaction 저장소와 실시간 fan-out을 공유 저장소로 옮겨야 한다.

중앙 `liliplanet-auth`의 `JWT_ALLOWED_AUDIENCES`에 기존 항목을 보존하고 `junseo-api`를 추가했다. [변경 기록](docs/azure-auth-audience-change.json)을 참고한다. 별도 플랫폼 저장소의 `auth-api`, `platform-api` 기본 audience도 작업 당시 수정·테스트했으나 **이 Junseo Git 저장소에 포함되지 않는다**. 해당 저장소 담당자가 자체 변경·커밋 상태를 확인해야 한다. `register_auth.py` 는 공유 `liliplanet-auth` 를 재시작하기 전에 묻고(`--yes` 로 생략), 플랫폼 저장소는 `--platform-repo <경로>` 를 줄 때만 고친다.

## 5. Firebase / Android 푸시

| 항목 | 값 |
|---|---|
| Firebase project | `junseo-cbca7` (number `321160323457`) |
| Android package | `com.junseo.app` |
| Firebase Android app ID | `1:321160323457:android:977f115ceb66aadc528880` |
| 발송 service account | `junseo-fcm-sender@junseo-cbca7.iam.gserviceaccount.com` |
| 부여된 역할 | `roles/firebasecloudmessaging.admin` |
| 발송 API | FCM HTTP v1, `https://fcm.googleapis.com/v1/projects/junseo-cbca7/messages:send` |
| 서버 credential | Key Vault `fcm-service-account` → App Service 참조 |

Firebase는 **Android 알림 전송용**이다. 로그인은 LiliPlanet, DB·사진·API는 Azure를 사용한다. Expo Push Service 토큰을 쓰지 않고 `getDevicePushTokenAsync()`로 받은 네이티브 FCM/APNs 토큰을 등록한다.

`PUT /api/devices` 예:

```json
{ "token": "<FCM 기기 토큰>", "kind": "app", "environment": "production", "platform": "android" }
```

Android는 `app` / `production`만 허용, FCM 토큰의 대소문자를 보존한다. iOS는 기존 `app` 또는 `widget`, APNs environment 구분을 유지한다. `platform`을 생략하면 하위 호환으로 `ios`다. 로그아웃 시 `DELETE /api/devices/{token}`.

사진·채팅 이벤트가 commit된 뒤 `PushNotifier` → `PlatformPushSender`가 기기의 platform에 따라 APNs/FCM을 선택한다. Android 위젯 갱신은 NORMAL data 메시지, 사진·채팅 알림은 HIGH data 메시지다. FCM `UNREGISTERED`는 해당 토큰을 제거하고, 일시 실패는 영구 삭제하지 않는다.

네이티브 `WidgetMessagingService`가 Expo 알림 처리를 유지하면서 WorkManager 갱신을 예약한다. `WidgetRefreshWorker`가 API에서 사진·친구 목록을 받고 캐시/위젯을 갱신한다. 15분 주기 예약은 보조 경로이며 Android 절전 정책 때문에 정확한 15분 갱신을 보장하지 않는다. 로그아웃·만료·계정 변경 시 이전 사진과 자격증명을 지우고 세대 검사를 통해 이전 작업이 새 계정 캐시를 덮지 못하게 한다.

### 두 종류의 JSON 구분

- **앱 설정 `google-services.json`**: Firebase Console → Project settings → Your apps → Android `com.junseo.app`에서 다운로드. `mobile/.credentials/firebase/google-services.json`에 두고 빌드 때 `GOOGLE_SERVICES_JSON`으로 경로 전달. 프로젝트·package가 위 값과 일치해야 한다. 서버 발송용 private key는 들어 있지 않다.
- **서버 발송 service-account JSON**: private key가 있는 서버 비밀. APK나 `GOOGLE_SERVICES_JSON`에 넣지 않는다. 현재 값은 Key Vault에서 관리한다. 기존 운영 credential은 인계 시 매번 새로 생성할 필요가 없다. 키 교체는 Firebase IAM 키와 Key Vault secret을 함께 관리한다.

서버 credential 교체가 필요한 경우, 로컬 ignored 경로에 올바른 두 JSON을 놓고 저장소 루트에서 `.venv/bin/python ops/azure/configure_fcm.py`를 실행한다. 이 명령은 **Key Vault secret과 운영 App Service 설정을 변경**한다. 실행 전에 대상 구독·프로젝트·package·발송 계정을 확인한다. 과거 OAuth refresh 및 HTTP v1 `validate_only` 200, Azure secret 참조 `Resolved`, health UP을 확인했다. **이 검증은 실기기 수신 증거가 아니다.** 두 로그인 기기 사이의 실제 사진·메시지 알림 및 잠금/절전 상태 위젯 갱신 확인이 남았다.

근거: [Firebase 설정](docs/firebase-setup.json), [서버 credential 검증](docs/firebase-server-verification.json), `push/FcmPushSender.java`, `mobile/src/lib/push.ts`, `mobile/modules/junseo-widget/`.

## 6. Android 빌드·서명·설치

현재 배포 APK는 Play Store 공개판이 아닌 **내부 테스트용 release APK**다. Git에는 APK·ZIP·서명키·JSON 비밀·생성된 `android/`를 넣지 않았다.

개발자에게 별도 보안 전달이 필요한 파일:

| 파일 | 용도 / 출처 |
|---|---|
| `mobile/.credentials/junseo-preview.jks` | 기존 설치본과 동일하게 업데이트하기 위한 서명키. 현재 빌드 작업자에게서 안전하게 전달받아야 함 |
| `mobile/.credentials/android-preview.json` | 위 key alias/password. 키와 함께 별도 전달, 접근 권한 0600 |
| `mobile/.credentials/firebase/google-services.json` | Firebase Console에서 다시 다운로드 가능 |
| FCM service-account private JSON | 서버 키 교체 작업에만 필요. 운영값은 Key Vault에 있으며 Git에 없음 |

**기존 APK의 서명 인증서 SHA-256**: `b4e83ab62d183a33e043d492c80168c51784fabd21998e2c0372120b6e02fb6c`. 같은 package라도 다른 키로 서명하면 설치된 앱을 업데이트할 수 없다. `build_preview.py`는 기존 키가 없으면 중단한다. `--new-preview-key`는 새 서명 신원을 명시적으로 만들 때만 쓰며 기존 배포본 업데이트에 사용하지 않는다. 키를 잃으면 Google/Firebase 설정으로 복구할 수 없다.

macOS 로컬 빌드, 저장소 루트에서:

```bash
chmod 600 mobile/.credentials/junseo-preview.jks mobile/.credentials/android-preview.json
export ANDROID_HOME="$HOME/Library/Android/sdk"
export GOOGLE_SERVICES_JSON="$PWD/mobile/.credentials/firebase/google-services.json"
export EXPO_PUBLIC_API_URL=https://junseo-api.liliplanet.net
python3 ops/android/build_preview.py
```

스크립트는 Java 17, `expo prebuild`, ARM64 release 빌드와 개인 preview 키 서명을 수행하고 인증서 정보를 출력한다. 결과는 `mobile/artifacts/junseo-<package.json version>-android-arm64.apk`. Android SDK/Java 경로가 다른 경우 환경을 맞춘다. `--skip-build`는 이미 빌드한 APK에 서명하는 용도다.

다음 배포는 `mobile/package.json`의 버전과 `mobile/app.config.js`의 `android.versionCode`를 올린다. Kotlin/리소스 수정은 **`mobile/modules/junseo-widget/`**, native 설정은 **`mobile/plugins/withJunseoAndroid.js` / `app.config.js`**에서 한다. 생성된 `mobile/android/`와 `mobile/ios/`를 직접 수정하면 다음 prebuild에 사라진다. 새 native module은 Expo Go로 검증할 수 없다.

```bash
# USB 디버깅이 연결된 테스트 폰에 업데이트 설치
adb install -r mobile/artifacts/junseo-0.1.2-android-arm64.apk

# 홈 화면을 길게 누르기 → 위젯 → Junseo → 배치 → 위젯 편집으로 친구 선택
```

폰에서는 전달받은 APK/ZIP을 다운로드해 파일 앱에서 APK를 열고, 해당 다운로드 앱에 대해 앱 설치 권한을 허용한다. APK는 ARM64용이며 현재 삼성 S26 대상이다. 같은 서명이면 제거 없이 업데이트할 수 있다. 실제 전달본은 `Junseo-0.1.2-Android.zip`이며 카카오톡 패밀리 방에 올렸다. 채팅 첨부 보관 기간이 있으므로 영구 배포 저장소로 취급하지 않는다.

APK `0.1.2` SHA-256: `eb13d1826fced0f2e21caa9a09e2d08b616cd2eecd9cd3ff4b74276b63a1c50b`, 78,616,185 bytes. 상세 검사와 이전 버전 이력: [android-release.json](docs/android-release.json). 지금 소스를 다시 빌드한 APK는 병합된 안전 기능도 포함하므로 과거 `0.1.2` 파일과 바이트가 같다고 가정하지 않는다.

## 7. iOS / APNs

`mobile/targets/widget/`, `notification-service/`, `_shared/`가 Swift 구현이다. 기본 bundle ID `com.junseo.app`, App Group `group.com.junseo.app`, Keychain access group `$(AppIdentifierPrefix)com.junseo.app.auth`. 앱·위젯·알림 확장 모두 동일 entitlement/provisioning 권한이 필요하다. `IOS_BUNDLE_ID`를 변경하면 서버의 APNs bundle 설정도 맞춘다.

```bash
cd mobile
APPLE_TEAM_ID=<Apple팀ID> EXPO_PUBLIC_API_URL=https://junseo-api.liliplanet.net npx expo run:ios --device
# 클라우드 빌드: npx eas-cli@latest build --profile development --platform ios
```

Apple 개발자 계정·Xcode/iOS SDK·서명 설정을 준비해야 한다. APNs 발송은 `JUNSEO_APNS_ENABLED`, `JUNSEO_APNS_KEY_ID`, `JUNSEO_APNS_TEAM_ID`, `JUNSEO_APNS_BUNDLE_ID`, `JUNSEO_APNS_KEY_PATH`로 설정하며 `.p8`은 Git 밖에 보관한다. `key-path`는 서버가 읽을 수 있는 **파일 경로**다. 현재 서버에 해당 파일을 안전하게 배치하는 절차와 키 설정은 완료되지 않았다. JSON FCM secret을 APNs key-path에 넣으면 안 된다.

개발 build는 APNs development, TestFlight/App Store는 production을 사용한다 (`APNS_ENV`, `eas.json`). iOS prebuild·공유 Swift typecheck 검증 기록은 있으나 최신 병합 소스의 새 iOS 실기기 빌드·TestFlight·APNs 수신은 미완료다. [widget-check.md](docs/widget-check.md)를 따른다.

## 8. 서버 수정 후 배포 절차

운영 재배포 전 V8 · V9 와 아래 미완료 기능을 검토한다. **배포하면 Flyway V8 · V9 가 자동 적용**되므로 원본 개발자의 예전 `V6__safety.sql`이 적용된 개발 DB는 별도 조정이 필요하다. 버전 6으로 신고 기능이 이미 기록된 DB에 현재 V6을 그냥 실행하거나 `repair`로 무시하지 않는다. 폐기 가능한 로컬 DB는 재생성하고, 보존해야 하는 DB는 history/스키마를 비교해 migration 계획을 세운다.

먼저 테스트·빌드·새 후보 manifest를 준비한다. 이 단계는 Azure를 변경하지 않는다.

```bash
cd backend
./gradlew test bootJar --console=plain
cd ..
python3 ops/azure/prepare_release.py
```

`backend/build/azure-release-candidate.json`에 JAR 해시·소스 지문·테스트 결과가 생성된다 (Gitignored). 테스트 후 소스가 바뀌었거나 실패/skip 결과가 있으면 준비가 중단된다. `docs/azure-release.json`은 마지막 실제 운영 배포 기록이므로 후보 준비 때 덮어쓰지 않는다.

실제 배포를 진행할 때에는 기존 JAR·배포 manifest·DB 백업/복구 경로를 먼저 확보한다. Azure 권한을 가진 계정으로:

```bash
az login --tenant 59a2b2e0-4eda-44d9-b0b3-82ee126b5cc3
az account set --subscription f14e91e2-b819-4cd6-ac39-e4a3909c17b9
.venv/bin/python ops/azure/deploy.py
.venv/bin/python ops/azure/smoke.py
```

`deploy.py`는 후보의 JAR/소스 해시와 구독을 확인하고 동기 `az webapp deploy --type jar --async false --track-status false`로 업로드한다. SCM에서 실제 `app.jar` 해시를 다시 읽고, health UP과 Flyway 완료 후 runtime의 migration-history 권한을 제거한다. 성공 시 `docs/azure-release.json`을 새 배포 기록으로 갱신한다. 갱신된 배포/검증 문서도 후속 커밋한다. 비동기 OneDeploy는 기존 환경에서 실패한 기록이 있어 동기 경로를 유지한다.

`psql`이 PATH에 있어야 한다. 별도 위치는 `JUNSEO_PSQL`로 지정한다. DB TLS root CA는 `JUNSEO_PG_SSLROOTCERT`로 지정하며 기본은 `/opt/homebrew/etc/ca-certificates/cert.pem`. Linux/다른 Mac에서는 실제 신뢰 CA 번들 경로로 설정한다.

스크립트 구분:

| 스크립트 | 용도 / 영향 |
|---|---|
| `prepare_release.py` | 로컬 테스트/JAR 검증과 후보 manifest 생성 |
| `deploy.py` | **실제 운영 JAR 변경**, DB migration 실행 후 확인 |
| `smoke.py` | 공개 API 12항목 확인, 점검 결과 JSON 갱신. 사용자 계정 로그인·FCM 수신을 대신하지 않음 |
| `bootstrap.py` | 최초 자원·DB 역할·권한·비밀·App Service 설정 구성. 일상 배포마다 실행하지 않음 |
| `configure_domain.py` | DNS/도메인/인증서 구성 변경. 현재 도메인은 이미 연결됨 |
| `register_auth.py` | 공유 중앙 audience 추가 (재시작 전에 확인을 묻는다). 플랫폼 저장소는 `--platform-repo` 를 줄 때만. 현재 등록 완료 |
| `configure_fcm.py` | FCM 비밀 저장·운영 설정 변경. 현재 설정 완료 |

문제 확인: Azure Portal → `junseo-api` → Deployment Center / Diagnose and solve problems / Log stream. health와 Key Vault 참조 상태, managed identity 권한, DB 연결 한도를 먼저 확인한다. `az webapp log tail --resource-group studylog-rg --name junseo-api`는 작업자 콘솔에서 확인하되 원본 로그를 공유하기 전에 사용자 정보·자격증명 노출 여부를 점검한다.

업로드 성공 뒤 health가 실패하면 무작정 bootstrap을 재실행하지 않는다. JAR이 바뀐 상태일 수 있으므로 배포 ID/해시와 기동 로그를 확인한다. 이전 JAR로 복구할 때는 **새 DB migration과의 호환성**을 검토하고 같은 동기 CLI 배포 경로를 사용한다. DB drop/Flyway clean은 운영 복구 수단으로 사용하지 않는다. PostgreSQL은 공유 서버이므로 복원 시 Junseo만 복구하는 계획도 필요하다.

## 9. 검증 범위와 다음 작업

| 확인 항목 | 증거 / 제한 |
|---|---|
| 병합 후 backend | 실제 로컬 PostgreSQL, 139/139 성공 (검토 수정 포함). `docs/handoff-verification.json` 은 병합 시점(126) 기록 |
| 모바일 회귀 | 로그인 8 + 계정 삭제 본인 확인 5 + multipart 업로드 5 = 18 성공, lint/typecheck 성공 |
| 기존 Android `0.1.2` | release 서명·같은 키 업데이트 설치·Firebase SDK 초기화·Azure API 설정·로그인 화면 확인 |
| native 업로드 transport | 실제 SDK File multipart가 서버의 401 응답까지 도달. 인증된 사용자 사진 전송 성공을 의미하지 않음 |
| Android Keystore | 이전 빌드 instrumentation 4개 성공. 이번 문서 작업에서 네이티브 전체 빌드를 반복하지 않음 |
| FCM | OAuth refresh / validate_only 200 / Azure 참조 Resolved. 실기기 수신 미확인 |
| 운영 서버 | 기존 배포본의 공개 smoke 12개 성공. 최신 점검 시각은 결과 파일 참고 |
| iOS | 기존 prebuild/Swift 검사 기록. 실기기·APNs·TestFlight 미완료 |

우선 이어서 할 일:

1. **두 실기기로 인수 검증**: 공통 로그인 → 닉네임 → 앱 재시작 → 친구 추가 → 카메라/갤러리 사진 전송 → 상대 조회 → FCM → 잠금/절전 상태 위젯, 개인/그룹 채팅, 로그아웃 후 이전 사진 제거. S26의 로그인 검은 화면 및 사진 네트워크 오류는 코드·회귀 검증을 마쳤으나 전체 실기기 성공 판정은 별도로 기록해야 한다.
2. **안전 기능 배포**: 신고/차단 · 계정 삭제(리리플레닛 재로그인) · 내보내기는 코드 완료, V8 · V9 배포 후 사용 가능. 계정 삭제는 Junseo 앱 데이터만 지운다 (리리플레닛 계정 자체는 남는다고 앱에 안내함). 실기기에서 삭제 흐름 확인 필요: iOS 는 쿠키를 나누지 않는 창이라 비밀번호를 다시 넣고, Android(Custom Tabs)는 리리플레닛 로그인이 남아 있으면 바로 돌아올 수 있다. 중앙 토큰에 `auth_time` 이 없으면 `iat` 로 판단하므로, 중앙이 조용히 토큰을 재발급하는 기능을 넣으면 `auth_time` 을 꼭 넣어야 한다. 비밀번호 찾기는 LiliPlanet 에서 처리한다 (Junseo `password-reset` 은 local 모드 API).
3. **운영 관리자·약관·메일**: `static/legal/`의 운영자/문의/시행일 등 placeholder를 채우고 운영 정책을 검토. 신고 담당자, 관리자 비밀, 처리 절차, SMTP를 설정. 원격 README의 삭제/재설정 완료 설명은 로컬 인증 기준이므로 플랫폼 운영 완료로 해석하지 않는다.
4. **iOS 서명/APNs**: Apple 계정, `.p8`, provisioning, shared Keychain·위젯 실기기, TestFlight.
5. **운영 보강**: 중앙 token refresh/계정 변경 연동, migration job 분리, DB/Blob 복구 연습, B1 부하·메모리·예산 측정, 사진 삭제 실패 재시도/URL 회수 정책. 다중 인스턴스 전환은 공유 로그인 저장소·실시간 전달 구현 후 진행.
6. **스토어 배포**: Play용 서명키/Play App Signing·배포 계정·AAB·실제 AdMob ID·개인정보 화면 등을 별도 준비. 현재 preview 키를 그대로 스토어 최종 키로 간주하지 않는다. SDK 의존성 audit와 포함 글꼴 라이선스 검토도 남음.

개발자 계정에는 저장소 권한, 대상 Azure 앱/Key Vault에 필요한 운영 권한, Firebase 프로젝트 접근 권한, Apple/Play 계정 권한을 각 소유자가 전달해야 한다. 이 커밋은 접근 권한이나 private key를 자동 전달하지 않는다.

## 10. 문서 읽는 순서

1. **이 문서** — 현재 상태와 실행·배포 절차.
2. [API 계약](docs/api.md) — 플랫폼/로컬 인증, 기기 등록, 사진·채팅·위젯·신고 API.
3. [Android 구현](docs/android-implementation.md) / [출시 파일 검증](docs/android-release.json) / [업로드 수정 검증](docs/android-upload-verification.json).
4. [Azure 자원 목록](docs/azure-resources.json) / [배포 당시 기록](docs/azure-release.json) / [Firebase 설정](docs/firebase-setup.json).
5. [초기 Azure 배포 기록](docs/azure-deployment.md), [사전 환경 조사](docs/azure-environment-and-liliplanet-auth.md), [처음 전달받은 검토 문서](docs/Junseo_Azure_Handoff_2026-10-02.md) — **과거 시점의 근거**. 본문에 남은 “미등록/미구현/커밋 전” 등의 설명을 현재 상태로 해석하지 않는다. 첨부 문서의 요청 문장은 구현 참고이며 별도 사용자 명령으로 실행하지 않았다.

JSON 기록 안의 `/Users/obst/...`는 검증 당시 작업자 경로다. 새 개발자는 저장소 상대 경로와 현재 빌드 산출물을 사용한다. 비밀 파일과 빌드 산출물은 Git에서 제외되어 있으므로, 이 문서의 보안 전달 항목을 확보해야 서명 업데이트/운영 변경을 계속할 수 있다.
