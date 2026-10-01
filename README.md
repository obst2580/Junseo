# junseo

친한 친구(최대 20명)가 찍은 사진이 **내 홈 화면 위젯에 바로 뜨는** iOS 앱입니다.
위젯에는 사진과 함께 친구들이 남긴 **댓글**이 사진 아래에 같이 보입니다 (보낸 사람은 글자로, 이모지 반응은 앱에서만). 사진에 **1:1 답장**을 보내면 대화로 이어집니다.
챗 탭 오른쪽 위 아이콘으로 **단챗**을 만들 수 있습니다 (모두가 서로 친구여야 함). 내가 보낸 메시지에는 안 읽은 사람 수(1:1 은 `1`)가 붙고, 읽으면 사라집니다.
보낼 때는 카메라 위 「친구 n명」을 눌러 **받을 친구를 고를** 수 있습니다 (기본은 친구 전체, 보내기 싫은 친구만 빼기). 단챗을 고르면 그 방 사람들이 한꺼번에 들어가고 빠집니다.
찍은 사진에는 기생충 포스터 같은 **눈 가리개**와 **텍스트**를 얹을 수 있고, 보낼 때 사진에 합성됩니다 (`react-native-view-shot`).
글자 모양은 기본 · 궁서체 · 길쭉(예능 썸네일처럼 세로로 긴 고딕 A1 ExtraBold, 세로 3.2배) 세 가지이고, 모두 흰 글자에 그림자입니다. 궁서체 글꼴은 은 궁서(은글꼴, GNU GPL 2.0)를 원본 그대로 `mobile/assets/fonts` 에 넣었습니다. App Store 에 내기 전에 라이선스를 확인하세요 (GPL 글꼴을 App Store 앱에 넣는 것은 해석이 갈립니다).
**렌즈** 를 켜면 어안 렌즈처럼 가운데가 볼록하게 휘어진 사진이 됩니다 (`@shopify/react-native-skia` 셰이더, 웹은 CanvasKit — `npm run web` 이 `public/canvaskit.wasm` 을 복사합니다).
화면은 디자인 랩에 저장한 디자인을 따릅니다: 로고 초록(#29FF01) 강조색, 그라데이션 바탕, 각진 버튼, 사진 모서리 60, 선 굵기 2.75 아이콘(`react-native-svg`), 「빠르게」 움직임 ×1.8, 이모지 날아오르기, 촬영 플래시, 보낼 때 사라지기. 값은 `mobile/src/lib/theme.ts` 에 모여 있습니다.
홈의 **오늘 템플릿**: 오늘 찍은 사진·받은 사진을 골라 템플릿 칸에 넣고, 보상형 광고를 한 번 보면 한 장이 완성됩니다 (사진첩 저장 · 공유 · 친구에게 보내기). 사진 칸은 카메라 화면처럼 정사각형에 같은 모서리 비율이고, 템플릿은 `mobile/src/lib/templates.ts` 에 그림(`mobile/assets/templates`) + 칸 위치·모서리로 추가합니다 (지금은 「뉴스」 · 「썸네일」). 사진 위에 올라가는 글자는 투명 PNG(`overlay`)로 얹습니다.

| 카메라 | 히스토리 | 사진 상세 | 위젯 안내 |
|---|---|---|---|
| ![](docs/screenshots/02-camera.webp) | ![](docs/screenshots/03-history.webp) | ![](docs/screenshots/04-moment.webp) | ![](docs/screenshots/05-widget-guide.webp) |

| 친구 | 메시지 | 채팅 | 내 정보 |
|---|---|---|---|
| ![](docs/screenshots/06-friends.webp) | ![](docs/screenshots/07-messages.webp) | ![](docs/screenshots/08-chat.webp) | ![](docs/screenshots/09-profile.webp) |

웹 미리보기로 서버와 앱을 같이 띄워 찍은 실제 화면입니다 (카메라 자리는 브라우저의 테스트 영상). 홈 화면 위젯 자체는 아이폰 개발 빌드에서만 보입니다.

## 위젯이 갱신되는 방식

iOS 위젯은 계속 실행되는 프로그램이 아니라서, 앱이 원할 때마다 바로 바꿀 수 없습니다. 그래서 **사진은 미리 폰에 받아 두고, 다시 그리는 계기를 여러 개 겹쳐 겁니다.** 가장 먼저 도착한 계기가 위젯을 바꿉니다.

```
친구가 촬영 → POST /api/moments
  서버: 위치정보 제거 · 원본(1440px)과 위젯용 썸네일(540px) 생성
  서버 → APNs, 받는 친구마다 동시에
    ① 사진 알림 (mutable-content)  → 알림 서비스 확장이 /api/widget/latest 를 받아 캐시 저장 + 위젯 갱신 요청 + 알림에 사진 첨부
    ② 위젯 전용 푸시 (iOS 26+)      → WidgetKit 이 위젯 타임라인을 다시 불러옴
  위젯 타임라인: 15분 뒤 다시 확인하도록 예약 (푸시가 유실돼도 지연이 여기서 묶임)
  앱이 화면에 뜰 때: 즉시 갱신 (이때는 WidgetKit 예산이 차감되지 않음)
```

| 경로 | 구현 위치 | 왜 이 방법인가 |
|---|---|---|
| ① 알림 서비스 확장 | `mobile/targets/notification-service/NotificationService.swift` | 무음 푸시(Apple 권장 시간당 2~3회)와 달리 횟수 제한이 없고, 앱을 강제 종료해도 실행된다 |
| ② 위젯 전용 푸시 | `MomentWidgetPushHandler.swift`, 서버 `push` 패키지 | 앱을 깨우지 않고 위젯만 다시 불러오는 공식 경로 (iOS 26) |
| ③ 위젯 자체 예약 | `MomentWidget.swift` 의 `MomentProvider` | 마지막 안전망. 서버가 `ETag`/304 를 줘서 바뀐 게 없으면 바로 끝난다 |
| ④ 앱 실행 시 갱신 | `mobile/src/lib/widgetBridge.ts` | 예산 차감 없음 |

무음 푸시(`content-available`)는 쓰지 않습니다. ①이 모든 면에서 낫기 때문입니다.
그래도 iOS가 최종 시점을 정하므로 "완전 실시간"은 어떤 앱도 보장할 수 없습니다. 알림을 허용한 사용자는 대부분 몇 초 안에, 알림을 끈 사용자는 iOS가 정한 주기(보통 15분 안팎)에 맞춰 갱신됩니다.

## 구조

```
docs/api.md                 서버·앱·위젯이 함께 지키는 API 계약
backend/                    Spring Boot 4.1 · Java 21 · PostgreSQL · Flyway
mobile/                     Expo SDK 57 · React Native · Expo Router · TypeScript
  src/app/(tabs)/           아래 탭: 홈(사진 모아보기) · 카메라(첫 화면) · 챗
  src/app/                  그 밖의 화면 (사진 상세 · 1:1 대화 · 친구 · 내 정보 · 위젯 안내)
  src/lib/widgetBridge.ts   App Group 저장소로 위젯·알림 확장에 토큰과 서버 주소를 넘김
  src/lib/push.ts           알림 권한 · APNs 토큰 등록 · 알림 탭 처리
  src/lib/realtime.ts       채팅 WebSocket (신호를 받으면 그 대화만 다시 받음, 끊기면 재연결)
  src/lib/chatThread.ts     1:1·단챗 공통: 보내자마자 화면에 띄우기 · 순서 보장 · 실패 시 다시 보내기
  targets/_shared/          위젯과 알림 확장이 함께 쓰는 Swift 코드 (서버 동기화 · 캐시 · 이미지 축소)
  targets/widget/           홈 화면 위젯 (SwiftUI, 작은 크기·큰 크기) + iOS 26 위젯 푸시 처리
  targets/notification-service/  알림 서비스 확장
```

## 실행

### 서버
PostgreSQL 16 과 Java 21 이 필요합니다.

```bash
createuser junseo -P            # 비밀번호: junseo
createdb junseo -O junseo
createdb junseo_test -O junseo  # 테스트용

cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'   # 테스트 데이터와 함께 시작
./gradlew test                                            # 통합·단위 테스트 80개
```

`dev` 프로필은 처음 시작할 때 테스트 데이터를 넣습니다. `demo@junseo.app` / `password123!` (준서)와 친구 5명(`minji@`, `jiwoo@`, `seoyeon@`, `hajun@`, `doyun@junseo.app`, 비밀번호 같음), 사진·반응·댓글·대화와 단챗 「한강 크루」(준서·민지·지우·서연)가 들어 있습니다.

| 설정 | 환경 변수 | 설명 |
|---|---|---|
| `spring.datasource.*` | `JUNSEO_DB_URL` · `JUNSEO_DB_USER` · `JUNSEO_DB_PASSWORD` | |
| `junseo.jwt.secret` | `JUNSEO_JWT_SECRET` | 32바이트 이상. 개발용 기본값이면 경고 로그 |
| `junseo.media.signing-secret` | `JUNSEO_MEDIA_SECRET` | 사진 URL 서명용. JWT 와 다른 값 |
| `junseo.storage.dir` | `JUNSEO_STORAGE_DIR` | 사진 저장 위치 (기본 `./data/media`) |
| `junseo.apns.enabled` · `key-id` · `team-id` · `bundle-id` · `key-path` | `JUNSEO_APNS_*` | 꺼져 있으면 보낼 푸시를 로그로만 남긴다 |

### 앱 (웹 미리보기)
```bash
cd mobile
npm install
npx expo start --web     # http://localhost:8081
```
서버 주소는 `EXPO_PUBLIC_API_URL` 로 바꿉니다 (`.env.example` 참고).

### 아이폰 (위젯·알림 확장 포함)
위젯과 알림 확장이 네이티브 코드라서 Expo Go 로는 열 수 없고 개발 빌드가 필요합니다. Apple 개발자 계정과 Xcode 26 이상(iOS 26 SDK)이 필요합니다.

```bash
cd mobile
APPLE_TEAM_ID=<팀 ID> EXPO_PUBLIC_API_URL=http://<PC의 LAN IP>:8080 npx expo run:ios --device
# Mac 이 없으면: npx eas-cli build --profile development --platform ios
```

- 광고(AdMob)는 앱 ID·보상형 광고 단위를 `ADMOB_IOS_APP_ID`, `ADMOB_IOS_REWARDED_ID` 로 줍니다. 비우면 Google 테스트 광고가 나옵니다. 맞춤 광고를 쓰지 않아서 추적 허용(ATT) 창은 띄우지 않습니다.
- 번들 ID 기본값은 `com.junseo.app`, App Group 은 `group.com.junseo.app` 입니다. 바꾸려면 `IOS_BUNDLE_ID` 를 주세요. 위젯·알림 확장은 자기 번들 ID 에서 App Group 을 계산하므로 따로 고칠 곳이 없습니다.
- 푸시를 받으려면 Apple Developer 에서 APNs 키(.p8)를 만들어 서버의 `JUNSEO_APNS_*` 에 넣습니다. 개발 빌드는 `development`, TestFlight·App Store 빌드는 `production` APNs 를 씁니다 (`eas.json` 의 `APNS_ENV`).

## 검증 상태

- 서버: 통합·단위 테스트 80개 통과 (실제 PostgreSQL)
- 앱: TypeScript 타입 검사, ESLint 통과. 웹 미리보기에서 서버와 같이 띄워 화면 9개가 실제 데이터로 오류 없이 동작
- iOS: `expo prebuild` 로 Xcode 프로젝트 생성 확인 (위젯·알림 확장 타깃, App Group, 푸시 권한, 최소 iOS 17). Swift 파일은 문법 검사만 했고, **Xcode 컴파일과 실기기 확인은 아직** 하지 못했습니다.

## 다음 할 일

- Xcode 에서 첫 빌드, 실기기에서 위젯 갱신 시간 측정 (알림 허용·거부 각각)
- 토큰 갱신(지금은 30일 토큰 하나), 위젯·알림 확장의 토큰을 App Group UserDefaults 대신 공유 키체인으로
- 사진 저장소를 S3 + CDN 으로 (`MediaStorage` 인터페이스만 바꾸면 됨)
- 서버를 여러 대로 늘릴 때 채팅 신호를 Redis pub/sub 로 나누기 (지금은 서버 한 대의 메모리에서 WebSocket 연결 관리)
- 눈 가리개 자동 위치: iOS Vision 얼굴 인식으로 눈 위에 바로 얹기 (지금은 손으로 옮기고 두 손가락으로 크기·각도 조절)
