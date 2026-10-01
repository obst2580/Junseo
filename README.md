# junseo

친한 친구(최대 20명)가 찍은 사진이 **내 홈 화면 위젯에 바로 뜨는** iOS 앱입니다.
위젯에는 사진과 함께 친구들이 남긴 **이모지 반응과 댓글**이 사진 아래에 같이 보입니다. 사진에 **1:1 답장**을 보내면 대화로 이어집니다.

<!-- SCREENSHOTS -->

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
  src/app/                  화면 (카메라 · 히스토리 · 사진 상세 · 친구 · 메시지 · 내 정보 · 위젯 안내)
  src/lib/widgetBridge.ts   App Group 저장소로 위젯·알림 확장에 토큰과 서버 주소를 넘김
  src/lib/push.ts           알림 권한 · APNs 토큰 등록 · 알림 탭 처리
  targets/_shared/          위젯과 알림 확장이 함께 쓰는 Swift 코드 (서버 동기화 · 캐시 · 이미지 축소)
  targets/widget/           홈 화면 위젯 (SwiftUI, 작은 크기·큰 크기) + iOS 26 위젯 푸시 처리
  targets/notification-service/  알림 서비스 확장
```

<!-- RUN -->
