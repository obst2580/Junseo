# API 계약

서버(`backend/`), 앱(`mobile/`), iOS 위젯·알림 확장(`mobile/targets/`), Android 위젯(`mobile/modules/junseo-widget/`)이 모두 이 문서를 기준으로 맞춘다.

- 운영 주소: `https://junseo-api.liliplanet.net` / 로컬 주소: `http://<host>:8080`
- 실제 배포 범위와 미완료 플랫폼 계정 기능은 [HANDOFF.md](../HANDOFF.md) 참고. 신고·차단 API는 병합 소스에 있으나 운영 V8 미배포
- 인증: `Authorization: Bearer <accessToken>` (가입·로그인·`/media` 제외 전부 필요)
- 요청·응답: JSON, 시각은 초 단위 ISO-8601 UTC 문자열 (`2026-09-30T12:34:56Z`, 소수점 없음)
- ID: 숫자(long)

## 오류 형식

```json
{ "code": "FRIEND_LIMIT_REACHED", "message": "친구는 20명까지 추가할 수 있어요." }
```

| HTTP | code | 상황 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 입력값 형식 오류 |
| 400 | `INVALID_IMAGE` | JPEG/PNG가 아니거나 10MB 초과, 디코딩 실패 |
| 400 | `CANNOT_ADD_SELF` | 내 초대 코드로 나를 추가 |
| 400 | `NOT_ALLOWED_ON_OWN_MOMENT` | 내 사진에 반응·답장 |
| 400 | `WRONG_PASSWORD` | 계정 삭제할 때 비밀번호가 틀림 |
| 400 | `RESET_CODE_INVALID` | 비밀번호 재설정 코드가 틀렸거나 만료 · 5번 넘게 틀림 |
| 401 | `UNAUTHORIZED` | 토큰 없음·만료·잘못됨 |
| 401 | `INVALID_CREDENTIALS` | 로그인 실패 |
| 403 | `FORBIDDEN` | 권한 없음 (남의 댓글 삭제 등) |
| 403 | `NOT_FRIENDS` | 친구가 아닌 사람에게 메시지, 친구가 아닌 사람을 단챗에 초대 |
| 403 | `NOT_MUTUAL_FRIENDS` | 단챗에 서로 친구가 아닌 사람이 섞임 |
| 403 | `REAUTH_REQUIRED` | 리리플레닛 계정 삭제인데 토큰이 방금(5분 안) 로그인한 것이 아님. 앱은 다시 로그인한 뒤 그 토큰으로 보낸다 (401 이 아니라서 세션은 그대로) |
| 403 | `ACCOUNT_BANNED` | 운영자가 내보낸 사람의 로그인 · 가입 |
| 404 | `NOT_FOUND` | 대상 없음, 또는 볼 권한이 없는 사진 (존재 여부를 숨기려고 404로 통일) |
| 404 | `INVITE_CODE_NOT_FOUND` | 초대 코드 없음 |
| 409 | `EMAIL_TAKEN` | 이미 가입된 이메일 |
| 409 | `ALREADY_FRIENDS` | 이미 친구 |
| 409 | `FRIEND_LIMIT_REACHED` | 나 또는 상대가 친구 20명 |
| 409 | `BLOCKED_USER` | 내가 차단한 사람의 초대 코드 (나를 차단한 사람의 코드는 `INVITE_CODE_NOT_FOUND`) |
| 409 | `REAUTH_NO_ACCOUNT` | 본인 확인 로그인(`reauth: true`)을 이 앱 계정이 없는 리리플레닛 계정으로 함 |
| 429 | `TOO_MANY_REQUESTS` | 한 주소에서 로그인 시작이 너무 많음 · 사진 처리 대기 15초 초과 |

## 공통 객체

```jsonc
// UserSummary
{ "id": 12, "displayName": "민지" }

// Me
{ "id": 12, "email": "minji@example.com", "displayName": "민지", "inviteCode": "K7Q2MX9A", "friendCount": 3, "friendLimit": 20, "needsOnboarding": false,
  "loginMethod": "platform" }   // "platform" (리리플레닛, 비밀번호 없음) | "password" (로컬 이메일 계정)

// ReactionCount
{ "emoji": "❤️", "count": 2 }

// Comment
{ "id": 91, "momentId": 301, "author": UserSummary, "text": "대박", "createdAt": "..." }

// Moment (목록·단건 공통)
{
  "id": 301,
  "sender": UserSummary,
  "createdAt": "...",
  "imageUrl": "/media/301/full.jpg?exp=1790000000&sig=...",   // 서명된 상대 경로. 앞에 기본 주소를 붙여 쓴다
  "thumbUrl": "/media/301/thumb.jpg?exp=1790000000&sig=...",
  "reactions": [ReactionCount],       // 많은 순
  "myReactions": [ReactionCount],    // 내가 누른 이모지별 횟수, 많은 순
  "commentCount": 4,
  "recentComments": [Comment]         // 최근 2개, 오래된 것 → 최신 순
}

// Message
{ "id": 77, "senderId": 12, "receiverId": 15, "text": "ㅋㅋㅋ", "createdAt": "...", "readAt": null,
  "moment": { "id": 301, "thumbUrl": "/media/..." } | null,   // 사진에 답장한 메시지면 원본 사진
  "clientId": "m1x2y3-ab12cd34" | null }                      // 보낼 때 붙인 ID. 보낸 사람에게만 보인다
```

## 인증

### 운영: LiliPlanet 플랫폼 인증

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/api/auth/config` | | `{ mode: "platform", loginUrl, audience: "junseo-api" }` |
| POST | `/api/auth/start` | `{ codeChallenge: S256, returnUri }` | `{ state, launchUrl }` |
| GET | `/auth/launch?state=...` | 시스템 브라우저 | 302 중앙 로그인 + HttpOnly/Secure/SameSite=Lax 쿠키 |
| GET | `/auth/callback?state=...&token=...` | 중앙 로그인 callback + 위 쿠키 | 302 허용된 반환 주소, `code`/`state`만 전달 |
| POST | `/api/auth/exchange` | `{ code, codeVerifier, reauth? }` | `{ accessToken, expiresAt, user: Me, needsOnboarding }` (accessToken 은 준서 세션 토큰) |

- 중앙(LiliPlanet) 토큰은 로그인할 때 한 번만 확인한다: RS256 / issuer `auth.liliplanet.net` / audience `junseo-api`, 중앙 JWKS 검증. sub·jti·exp 필수. API 에는 쓰지 않는다 (보내면 401).
- 중앙 sub는 Junseo 사용자 ID가 아니다. `(issuer, sub)`로 내부 ID를 찾는다.
- transaction 10분, code 60초·일회용·PKCE S256. 운영 return URI는 `junseo://auth`와 운영 `/login`만 허용.
- 로그인 시작은 계정 없이 부를 수 있어서, 한 주소(요청의 remote address)가 대기 중인 로그인을 20개까지만 가질 수 있다 (넘으면 429). 표가 가득 차면(1만 개) 가장 오래된 대기 로그인을 지워 자리를 만든다 — 아무도 다른 사람의 로그인을 막을 수 없다.
- `reauth: true`: 계정 삭제 전 본인 확인. 이미 있는 계정만 돌려주고 새 계정을 만들지 않는다 (없으면 409 `REAUTH_NO_ACCOUNT`). 앱은 돌아온 `user.id` 가 지금 계정과 같은지 확인한다.
- 운영자가 내보낸 `(issuer, sub)` 는 다시 로그인해도 403 `ACCOUNT_BANNED`.
- 최초 프로필은 `needsOnboarding: true`. `PATCH /api/me`로 이름을 저장하면 완료된다.
- 중앙 전역 로그아웃은 미연동 (준서 로그아웃이 다른 LiliPlanet 제품을 로그아웃시키지 않는다). 운영 자체 signup/login/password-reset endpoint는 비활성화되어 404다.
- iOS 공유 Keychain / Android Keystore / 웹 sessionStorage 사용. 토큰은 callback 앱 URL에 넣지 않는다.

### 준서 세션 (두 로그인 방식 공통)

중앙 토큰은 12시간쯤이면 끝나고 갱신 API 가 없어서, 그것을 그대로 쓰면 위젯 · 알림 확장 · 앱이 하루 두 번 로그아웃된다. 그래서 로그인 뒤에는 준서가 발급한 세션 토큰을 쓴다.

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/auth/renew` | Bearer 세션 토큰 | 200 `{ accessToken, expiresAt }` 같은 세션, 새 만료 |
| POST | `/api/auth/logout` | Bearer 세션 토큰 | 204 이 세션의 모든 토큰 · WebSocket 끝. 이 세션을 만든 중앙 토큰도 다시 로그인에 쓸 수 없다 |

- 토큰: HS256 (`JUNSEO_JWT_SECRET`), `sub` = 준서 사용자 ID, `sid` = 세션, `ver` = 비밀번호 세대, `auth_time` = 실제로 로그인한 시각 (연장해도 그대로), 유효 90일 (`JUNSEO_SESSION_TTL`).
- 모든 요청에서 세션이 열려 있는지(`sessions` 테이블), 계정이 있는지, 비밀번호 세대가 같은지 본다. 로그아웃 · 계정 삭제 · 내보내기 · 비밀번호 변경은 즉시 끝난다.
- 앱은 열릴 때 · 앞으로 나올 때 토큰이 하루 넘었으면 연장해 위젯 · 알림 확장이 읽는 곳에 저장한다. 연장해도 예전 토큰은 자기 만료까지 쓸 수 있다 (위젯이 잠깐 예전 것을 들고 있어도 된다). 90일 동안 앱을 한 번도 열지 않으면 다시 로그인한다.
- 계정 삭제용 본인 확인(`reauth: true`)은 10분짜리 세션이고 연장할 수 없다.

### 로컬 개발: 이메일·비밀번호 인증

아래는 `junseo.auth.mode=local` (기본값, `dev`/`test`)에서만 활성화된다. 현재 모바일 로그인 UI는 위 플랫폼 방식이다.

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/auth/signup` | `{ email, password(8~72자), displayName(1~20자) }` | 201 `{ accessToken, expiresAt, user: Me }` |
| POST | `/api/auth/login` | `{ email, password }` | 200 `{ accessToken, expiresAt, user: Me }` |
| POST | `/api/auth/password-reset` | `{ email }` | 204 (가입된 이메일이면 6자리 코드를 메일로 보낸다) |
| POST | `/api/auth/password-reset/confirm` | `{ email, code, newPassword(8~72자) }` | 200 `{ accessToken, expiresAt, user: Me }` |

- 토큰: 위의 준서 세션 토큰과 같다 (로그인 · 가입 · 재설정이 세션을 하나 연다). 위젯과 알림 확장도 같은 토큰을 쓴다.
- 비밀번호를 바꾸면 모든 세션이 끝나고 세대도 올라가서 그 전에 받은 토큰(다른 폰 · 위젯 포함)이 모두 401 이 된다. WebSocket 인증/재연결도 같은 검사를 한다.
- 재설정: 없는 이메일이어도 똑같이 204 (가입 여부를 알 수 없게). 코드는 15분 동안 유효하고, 1분 안에 다시 요청하면 새로 보내지 않는다. 5번 틀리면 코드가 무효가 되어 다시 받아야 한다. 메일은 SMTP(`JUNSEO_SMTP_*`)로 보내고, 설정이 없으면 서버 로그에만 남긴다.
- 이메일은 소문자로 정규화한다.

## 내 정보

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/api/me` | | 200 `Me` |
| PATCH | `/api/me` | `{ displayName }` | 200 `Me` |
| POST | `/api/me/invite-code` | | 200 `Me` (초대 코드를 새로 발급, 이전 코드는 무효) |
| POST | `/api/me/delete` | 로컬 계정 `{ password }` · 리리플레닛 계정 본문 없음 | 204 계정 삭제 |

- 본인 확인: 로컬(이메일) 계정은 비밀번호. 리리플레닛 계정은 비밀번호가 없으므로 **방금 로그인한 토큰**으로 보낸다. 서버는 `auth_time`(있으면) 또는 `iat` 가 5분 안인지 본다. 아니면 403 `REAUTH_REQUIRED`.
  앱 흐름: 확인 → 리리플레닛 로그인 창(iOS 는 쿠키를 나누지 않는 창이라 비밀번호를 다시 넣는다) → `exchange { reauth: true }` → 같은 계정이면 그 토큰으로 `POST /api/me/delete`. 지금 세션은 바꾸지 않는다.
- 삭제하면 그 토큰은 폐기되고 그 사람의 WebSocket 은 모두 닫힌다. 다른 기기의 예전 토큰도 계정이 없어서 401 이다.
- 계정 삭제: 내 사진(파일 포함) · 반응 · 댓글 · 메시지 · 친구 · 차단 · 기기 토큰을 바로 지운다. 내가 마지막 사람이던 단챗도 지운다. 친구들의 위젯에서도 내 사진이 빠지도록 친구 끊기와 같은 신호를 보낸다. 같은 이메일(로컬) · 같은 리리플레닛 계정으로 다시 시작할 수 있다 (빈 계정).

초대 코드: 8자, 헷갈리는 글자(0 O 1 I L) 제외한 대문자·숫자.

## 친구

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/api/friends` | | 200 `{ friends: [UserSummary], limit: 20 }` (이름순) |
| POST | `/api/friends` | `{ inviteCode }` (대소문자·공백 무시) | 201 `UserSummary` |
| DELETE | `/api/friends/{userId}` | | 204 |
| GET | `/api/friends/links` | | 200 `{ pairs: [[id, id]] }` 내 친구들 중 서로 친구인 쌍 (작은 id 먼저). 단챗 고르기용 |

- 초대 코드로 추가하면 바로 서로 친구가 된다 (코드를 건넨 것 자체가 동의).
- 한 사람당 최대 20명. 나나 상대 중 한쪽이라도 20명이면 `FRIEND_LIMIT_REACHED`.
- 친구를 끊으면 서로의 사진·댓글·반응이 즉시 안 보이고, 메시지도 보낼 수 없다. 기존 대화 기록은 남는다.

## 차단 · 신고

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/api/blocks` | | 200 `{ items: [UserSummary] }` (최근에 차단한 사람 먼저) |
| POST | `/api/blocks` | `{ userId }` | 204 |
| DELETE | `/api/blocks/{userId}` | | 204 (차단 풀기. 친구는 다시 초대 코드로) |
| POST | `/api/reports` | `{ kind, targetId?, userId?, reason, detail?(500자) }` | 201 `{ id }` |

- 차단하면 친구가 끊기고(친구 끊기 신호 포함), 다시 친구가 될 수 없다. 차단한 사람과 나를 차단한 사람 사이에서는 서로의 댓글 · 1:1 대화가 안 보이고, 단챗에서는 그 사람의 말과 안 읽은 수 · 푸시가 빠진다. 상대에게는 알리지 않는다.
- 신고 `kind`: `moment` · `comment` · `message` · `group-message` (이때 `targetId`), `user` (이때 `userId`). `reason`: `spam` · `abuse` · `sexual` · `violence` · `other`.
- 내가 볼 수 있는 것만 신고할 수 있다 (아니면 404). 내 것은 신고할 수 없다 (400). 글은 신고 시점의 내용을 같이 저장해서, 나중에 지워져도 운영자가 볼 수 있다.
- 신고가 들어오면 서버 로그(WARN)에 남기고, `JUNSEO_ADMIN_EMAIL` 이 있으면 메일로 알린다. 처리한 신고는 1년 뒤 지운다.

### 신고 처리 (관리자)

`X-Admin-Token` 헤더 (템플릿 올리기와 같은 토큰). 약관에 「24시간 안에 확인」이라고 적었으니 매일 확인한다.

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/api/admin/reports?all=false` | | 200 `{ items: ReportView[] }` (처리 안 한 것, 오래된 것 먼저. `all=true` 면 전부 최근 것 먼저) |
| POST | `/api/admin/reports/{id}/resolve` | `{ resolution }` (예: "사진 삭제, 계정 삭제") | 204 |
| DELETE | `/api/admin/{moments\|comments\|messages\|group-messages}/{id}` | | 204 내용 지우기 |
| DELETE | `/api/admin/users/{id}?ban=true&reason=` | | 204 계정 삭제 + 다시 못 들어오게 막기 (`ban=false` 면 지우기만) |
| GET | `/api/admin/bans` | | 200 `{ items: [{ id, externalIssuer, externalSubject, email, displayName, reason, createdAt }] }` |
| DELETE | `/api/admin/bans/{id}` | | 204 막은 것 풀기 (다시 오면 빈 새 계정) |

- 내보내기는 리리플레닛 계정이면 `(issuer, sub)`, 로컬 계정이면 이메일로 막는다. 계정만 지우면 같은 사람이 다시 로그인해 새 계정을 받을 수 있어서다.

```jsonc
// ReportView
{ "id": 3, "kind": "moment", "targetId": 301, "reason": "sexual", "detail": null,
  "snapshot": null,                       // 댓글 · 메시지면 신고 시점의 글
  "imageUrl": "/media/301/full.jpg?..." , // 사진이 아직 있으면 (서명 URL)
  "reporter": UserSummary | null, "target": UserSummary | null,
  "createdAt": "...", "resolvedAt": null, "resolution": null }
```

## 약관 · 개인정보처리방침

`GET /legal/terms.html`, `GET /legal/privacy.html` (인증 없음). 앱의 가입 화면 · 내 정보에서 연다. 출시 전에 문서 안의 `[운영자 이름]` · `[문의 이메일]` · `[시행일]` · `[서버 업체]` · `[메일 발송 업체]` · `[보호책임자 이름]` 을 채운다.

## 사진 (moment)

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/moments` | multipart `image` (JPEG/PNG, 10MB 이하), `recipientIds` (선택, 여러 번: 받을 친구 id. 빼면 친구 전체) | 201 `Moment`. 친구가 아닌 id 가 있으면 403 `NOT_FRIENDS`, 비어 있으면 400 |
| GET | `/api/moments?cursor=&limit=30&userId=` | | 200 `{ items: [Moment], nextCursor: string \| null }` 최신순 |
| GET | `/api/moments/{id}` | | 200 `Moment` + `"comments": [Comment]` (전체, 오래된 순) |
| DELETE | `/api/moments/{id}` | | 204 (보낸 사람만) |

- 받는 사람: 올리는 순간의 친구 전원. 나중에 친구가 된 사람은 예전 사진을 못 본다.
- **볼 수 있는 사람**: 보낸 사람 본인, 그리고 받는 사람 중 지금도 보낸 사람과 친구인 사람.
- 목록은 내가 볼 수 있는 사진 전체 (내 사진 포함). `userId`를 주면 그 사람이 보낸 것만.
- 서버는 이미지를 다시 인코딩해 위치정보 등 메타데이터를 지운다. 원본(`full`, 긴 변 1440px)과 위젯용 썸네일(`thumb`, 긴 변 540px) 두 벌을 만든다.

## 이모지 반응

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/moments/{id}/reactions` | `{ emoji, count? }` (emoji: ❤️ 🖕 😂 😢 👍 중 하나, count: 1~20, 기본 1) | 200 `Moment` |
| DELETE | `/api/moments/{id}/reactions` | | 204 (이 사진에 내가 누른 반응 전부 지우기) |

- 고를 수 있는 이모지는 ❤️ 🖕 😂 😢 👍 다섯 개뿐이다. 다른 값은 `VALIDATION_FAILED`. 변형 선택자 없이 온 하트(`❤`)는 ❤️ 로 저장한다.
- **여러 번 누를 수 있다.** 누를 때마다 그 이모지의 내 횟수가 늘고, 여러 이모지를 섞어 누를 수도 있다. 한 사람이 이모지 하나에 최대 99번.
- 앱은 빠르게 연달아 누른 걸 모아 `count` 로 한 번에 보낸다 (마지막 탭 후 0.6초).
- `reactions` 의 `count` 는 사람 수가 아니라 **누른 횟수의 합**이다.
- 내 사진에는 반응할 수 없다 (`NOT_ALLOWED_ON_OWN_MOMENT`).
- 반응은 그 사진을 볼 수 있는 사람 모두에게 보인다.

## 댓글

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/moments/{id}/comments` | `{ text }` (1~100자) | 201 `Comment` |
| DELETE | `/api/comments/{id}` | | 204 (쓴 사람 또는 사진 주인) |

- 댓글은 그 사진을 볼 수 있는 사람 모두에게 보이고, **위젯에서 사진 아래에 뜬다**. 사진 주인도 댓글을 달 수 있다.

## 1:1 답장·메시지

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/moments/{id}/replies` | `{ text, clientId? }` (1~500자) | 201 `Message` (사진 주인에게 가는 1:1 메시지) |
| GET | `/api/conversations` | | 200 `{ items: [{ peer: UserSummary, lastMessage: Message, unreadCount }], groups: [GroupConversation] }` 각각 최근 대화순 (앱이 둘을 섞는다) |
| GET | `/api/conversations/{peerId}/messages?cursor=&limit=50` | | 200 `{ items: [Message], nextCursor }` 최신순 |
| POST | `/api/conversations/{peerId}/messages` | `{ text, clientId? }` (1~500자) | 201 `Message` (친구에게만) |
| POST | `/api/conversations/{peerId}/read` | | 204 (상대가 보낸 메시지를 읽음 처리) |

- 답장은 사진 주인과 나만 본다. 댓글과 다르다.
- 내 사진에는 답장할 수 없다 (`NOT_ALLOWED_ON_OWN_MOMENT`).
- 읽음 표시: 내가 보낸 `Message` 의 `readAt` 이 비어 있으면 상대가 아직 안 읽은 것이다. 앱은 말풍선 옆에 `1` 을 띄운다.
- **같은 메시지가 두 번 저장되지 않게** (`clientId`, 단챗도 같음): 앱이 보낼 때마다 새 ID(`[A-Za-z0-9_-]` 8~64자)를 붙이고, 응답을 못 받아 다시 보낼 때는 같은 ID를 쓴다. 서버는 보낸 사람마다 ID를 한 번만 받는다 — 이미 저장된 ID면 처음 것을 그대로 돌려주고 알림도 다시 보내지 않는다 (동시에 와도 하나만 남는다). 형식이 틀리면 400.
  목록을 다시 받을 때 내 메시지의 `clientId` 로 「보내는 중·실패」 말풍선과 짝을 맞춰 지운다 (응답이 사라졌어도 다시 보내기를 누를 필요가 없다).

## 단챗

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/groups` | `{ memberIds: [id], name? }` (나 말고 2~19명, 이름 30자 이하, 비우면 이름 없음) | 201 `GroupChat` |
| GET | `/api/groups` | | 200 `{ items: [GroupChat] }` 만든 순 (최신 먼저) |
| GET | `/api/groups/{id}` | | 200 `GroupChat` |
| GET | `/api/groups/{id}/messages?cursor=&limit=50` | | 200 `{ items: [GroupMessage], nextCursor }` 최신순 |
| POST | `/api/groups/{id}/messages` | `{ text, clientId? }` (1~500자) | 201 `GroupMessage` |
| POST | `/api/groups/{id}/read` | | 204 (지금까지 온 메시지를 읽음 처리) |
| DELETE | `/api/groups/{id}/members/me` | | 204 나가기. 마지막 사람이 나가면 방과 메시지가 지워진다 |

```jsonc
// GroupChat: members 에 나도 들어 있다(이름순). name 이 null 이면 앱은 나를 뺀 사람들 이름으로 부른다.
{ "id": 7, "name": "한강 크루", "members": [UserSummary], "createdAt": "..." }
// GroupMessage: unreadCount = 보낸 사람을 빼고 아직 안 읽은 사람 수 (앱은 말풍선 옆에 숫자로 띄운다)
{ "id": 91, "groupId": 7, "senderId": 12, "text": "토요일?", "createdAt": "...", "unreadCount": 2, "clientId": null }
// GroupConversation (/api/conversations 의 groups)
{ "group": GroupChat, "lastMessage": GroupMessage | null, "unreadCount": 3 }
```
- 만들 때 **모두가 서로 친구**여야 한다. 내 친구가 아니면 `NOT_FRIENDS`, 내 친구끼리 서로 친구가 아니면 `NOT_MUTUAL_FRIENDS`.
- 멤버가 아니면 방이 없는 것과 같다 (404).
- 읽음은 멤버마다 "마지막으로 본 메시지"로 기억한다. 보내면 내 메시지까지 읽은 것으로 친다.
- 카메라의 「보낼 친구」에서 단챗을 고르면 그 방 사람들(지금도 내 친구인 사람)을 한꺼번에 넣고 뺀다. 사진은 여전히 친구 각각에게 간다.

## 실시간 (WebSocket)

`ws://<host>:8080/ws` — 채팅이 바뀌었다는 **신호만** 보낸다. 내용은 앱이 REST 로 다시 받는다 (권한 검사와 응답 모양이 한 곳에만 있게).

```jsonc
// 앱 → 서버. 연결 직후 첫 프레임 (다른 걸 먼저 보내거나 토큰이 틀리면 1008 로 끊는다)
{ "type": "auth", "token": "<accessToken>" }
// 서버 → 앱
{ "type": "ready" }
// 앱 → 서버 (15초마다, 그리고 메시지를 보낸 직후) / 서버 → 앱. 6초 안에 아무 응답이 없으면 앱이 끊고 다시 잇는다
{ "type": "ping" }  /  { "type": "pong" }

// 신호 (서버 → 앱). 커밋이 끝난 뒤에만 보낸다
{ "type": "message", "peerId": 12 }        // peerId 가 나에게 1:1 메시지를 보냈다
{ "type": "read", "peerId": 12 }           // peerId 가 내 메시지를 읽었다 (1 을 지운다)
{ "type": "group-message", "groupId": 7 }  // 단챗에 새 메시지 (보낸 사람에게는 안 감)
{ "type": "group-read", "groupId": 7 }     // 단챗에서 누군가 읽었다 (숫자를 줄인다)
```

- 브라우저 WebSocket 은 헤더를 못 붙여서 토큰을 첫 프레임으로 받는다 (URL 에 넣지 않아 로그에 안 남는다).
- 신호는 최선 전송이다. 앱은 연결될 때(`ready`)마다, 앱이 앞으로 나올 때마다 한 번 다시 받는다. 열린 대화 화면은 끊겨 있는 동안 5초마다, 연결돼 있어도 30초마다 한 번 더 확인한다.
- 와이파이 ↔ LTE 전환처럼 연결이 소리 없이 죽으면: 가만히 있을 때는 20초 안에, 메시지를 보낸 뒤에는 6초 안에 알아채고 다시 잇는다. 연결 직후 10초 안에 `ready` 가 안 오면 다시 시도한다.
- 지금은 서버 한 대의 메모리에서 연결을 관리한다. 서버를 여러 대로 늘리면 Redis pub/sub 같은 걸로 신호를 나눠야 한다.

## 위젯

| 메서드 | 경로 | 응답 |
|---|---|---|
| GET | `/api/widget/feed?from=` | 200 `WidgetFeed`, 보여줄 사진이 없으면 204 (위젯이 이걸 쓴다) |
| GET | `/api/widget/latest` | 200 `WidgetLatest`, 보여줄 사진이 없으면 204 (넘겨 보기 전 버전용) |

```jsonc
// WidgetLatest
{
  "version": "301-9f2c",            // 사진·반응·댓글이 바뀌면 달라지는 값. ETag로도 내려준다
  "moment": { "id": 301, "sender": UserSummary, "createdAt": "...", "thumbUrl": "/media/..." },
  "reactions": [ReactionCount],     // 많은 순 최대 3개
  "reactionCount": 9,               // 전체 반응 수 (누른 횟수의 합)
  "comments": [ { "author": "지우", "text": "대박" } ],   // 최근 2개, 오래된 것 → 최신 순
  "commentCount": 4
}
// WidgetFeed: 위젯이 양옆을 눌러 넘겨 보는 사진들, 최신 → 오래된 순
{ "version": "301-a1b2", "items": [WidgetLatest] }
```

- 대상 사진: 내가 받은 친구 사진 (내 사진 제외). `feed` 는 **최근 24시간 안의 최대 5장**, 그런 사진이 없으면 가장 최근 1장. `latest` 는 가장 최근 1장.
- `from`: 위젯 편집에서 한 친구만 고른 위젯. 그 친구가 보낸 사진만 같은 규칙으로. 친구가 아니면 204.
- `version`(ETag)은 가장 새 사진 ID 로 시작하고, 어느 장의 사진·댓글·반응이 바뀌어도 달라진다.
- 반응·댓글이 생기면 그 사진이 피드에 들어 있는 사람들(같은 친구의 더 새 사진이 5장 미만이고 24시간 안, 또는 그 친구의 가장 최근 사진)에게만 위젯 푸시를 보낸다.
- `If-None-Match`에 이전 ETag를 보내면 바뀐 게 없을 때 304 (본문 없음).
- `X-Widget-Source: notification | widget` (선택): 누가 받으러 왔는지. 이 폰이 **새 사진을 처음** 받아 갈 때 서버가 사진이 올라온 뒤 걸린 시간을 경로별로 로그에 남긴다 (`Widget got new photo … source=… after=…ms`, 실기기 점검은 `docs/widget-check.md`).

## 템플릿

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/api/templates` | | 200 `{ items: Template[] }` (서버에 올린 템플릿. 앱에 든 기본 템플릿은 앱이 뒤에 붙인다) |

```jsonc
// Template
{ "id": "subway", "name": "지하철 광고", "width": 1344, "height": 1040, "backgroundColor": "#151515",
  "backgroundUrl": "/media/templates/subway/background.jpg?v=2", "overlayUrl": "/media/templates/subway/overlay.png?v=2",
  "slots": [{ "quad": [[207, 264.6], [1180.6, 187.2], [1178, 832], [207, 749.8]], "aspect": 2, "grow": 5,
              "glow": { "grow": 90, "blur": 0.045, "wash": 0.35 } }],
  "requires": ["glow", "overlay", "quad"], "version": 2 }
```

- `slots`: 네모 칸 `{ x, y, size, radius? }` 또는 네 꼭짓점 칸 `{ quad, aspect, grow?, glow? }` (앱 `src/lib/templates.ts` 와 같은 모양, 좌표는 템플릿 px).
- `requires`: 그리려면 앱이 할 줄 알아야 하는 것. 서버가 칸 · 그림을 보고 채운다(`quad`, `glow`, `overlay`). 앱은 모르는 게 하나라도 있으면 그 템플릿을 목록에서 뺀다 → 새 효과가 필요한 템플릿을 올려도 예전 앱이 깨지지 않는다.
- 순서: `sortOrder` 가 큰 것 먼저, 같으면 최근에 올린 것 먼저.
- 그림 `GET /media/templates/{id}/{background.jpg|background.png|overlay.png}?v=` 은 모두에게 같아서 인증 없이 받고 `Cache-Control: public, max-age=31536000, immutable`. 다시 올리면 `version` 이 올라 주소가 바뀐다.

### 템플릿 올리기 (관리자)

`X-Admin-Token: <JUNSEO_ADMIN_TOKEN>` 헤더로 확인한다 (사용자 로그인 토큰이 아님). 서버에 `JUNSEO_ADMIN_TOKEN` 이 없으면 이 API 는 꺼져 있다(404).

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| PUT | `/api/admin/templates/{id}` | multipart: `meta`(JSON `{ name, width, height, backgroundColor, slots, requires?, sortOrder? }`), `background`(JPG/PNG), `overlay`(투명 PNG, 선택) | 200 `Template` (없으면 만들고, 있으면 통째로 바꾼다) |
| DELETE | `/api/admin/templates/{id}` | | 204 (목록에서만 뺀다. 이미 받아 간 폰을 위해 그림은 남긴다) |

- `id`: 영문 소문자 · 숫자 · `-` 40자까지. 그림은 템플릿 크기와 정확히 같아야 하고(다르면 `VALIDATION_FAILED` + 실제 크기), 칸은 1~6개.
- 보통은 `mobile/scripts/templates/upload_template.py` 로 올린다 (README 「출시 후 템플릿 추가」).

## 기기 (푸시 토큰)

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| PUT | `/api/devices` | `{ token, kind: "app" \| "widget", environment: "development" \| "production", platform?: "ios" \| "android" }` | 204 |
| DELETE | `/api/devices/{token}` | | 204 (로그아웃할 때) |

- `platform` 생략 시 `ios`. iOS `token`은 APNs 16진수 문자열, Android는 대소문자 구분 FCM 토큰 (최대 2048자), `kind=app`, `environment=production`만 허용한다. 같은 토큰이 다른 계정으로 오면 새 계정으로 옮긴다.
- 한 사람당 최근 토큰 10개까지만 남긴다 (오래된 것부터 지움). 푸시는 모든 토큰으로 가므로 쓰레기 토큰이 쌓이지 않게 한다.
- FCM 이 `UNREGISTERED`(404) · `SENDER_ID_MISMATCH`(403) · `message.token` 을 가리키는 `INVALID_ARGUMENT`(400) 로 답하면 그 토큰을 지운다. 401 이면 OAuth 토큰을 새로 받아 한 번 다시 보낸다.
- `kind: "app"`: 앱의 알림 토큰. `kind: "widget"`: iOS 26 위젯 푸시 토큰 (위젯 확장이 직접 등록).

## 사진 파일

`GET /media/{momentId}/{full|thumb}.jpg?exp=<epoch초>&sig=<서명>` → `image/jpeg`

- 인증 헤더 없이 서명으로 확인한다. 알림 확장·위젯이 바로 받을 수 있고, 나중에 CDN이나 S3 서명 URL로 바꾸기 쉽다.
- `sig` = HMAC-SHA256(`{momentId}/{variant}:{exp}`)을 base64url(패딩 없음)로. 만료는 발급 시점에서 7일 뒤를 하루 단위로 올림 (같은 날에는 URL이 같아서 캐시가 잘 맞는다).
- 서명이 틀리거나 만료되면 403. `Cache-Control: private, max-age=86400`.

## 푸시 (서버 → APNs / FCM)

Android는 FCM HTTP v1 data 메시지를 사용한다. 앱의 `getDevicePushTokenAsync()` 토큰을 등록하며 Expo Push Service 토큰은 사용하지 않는다. 사진·메시지 알림은 HIGH priority, 별도 위젯 갱신은 NORMAL / TTL 900초 / collapse key `junseo-widget`다. 네이티브 메시징 서비스가 WorkManager와 Expo 알림 처리를 연결한다. Firebase 키 설정과 검증 범위는 [인계 문서](../HANDOFF.md#5-firebase--android-푸시) 참고.

아래 payload와 widget token 설명은 iOS APNs 경로다.

| 사건 | 받는 사람 | 알림 (kind=app 토큰) | 위젯 푸시 (kind=widget 토큰) |
|---|---|---|---|
| 새 사진 | 받는 친구 전원 | 제목 `민지`, 본문 `새 사진을 보냈어요` | 전원 |
| 반응 | 사진 주인 | `민지님이 ❤️ 반응을 남겼어요` (한 번에 여러 개면 `민지님이 ❤️ 3개를 보냈어요`) | **그 사진이 지금 위젯에 떠 있는 사람** 중 반응한 사람 제외 |
| 댓글 | 사진 주인 (본인이 쓴 건 제외) | `민지: 대박` | **그 사진이 지금 위젯에 떠 있는 사람** 중 쓴 사람 제외 |
| 메시지·답장 | 받는 사람 | `민지: ㅋㅋㅋ` | 없음 |
| 단챗 메시지 | 보낸 사람을 뺀 멤버 | 제목 방 이름 (없으면 받는 사람을 뺀 멤버 이름), 본문 `민지: 토요일?` | 없음 |
| 반응 취소·댓글 삭제 | 없음 | 없음 | 그 사진이 지금 위젯에 떠 있는 사람 |
| 사진 삭제 | 없음 | 없음 | 받는 친구 전원 |
| 친구 끊기 | 없음 | 없음 | 두 사람 모두 |

- 반응은 연달아 누르는 경우가 많아서, 같은 사람이 같은 사진에 1분 안에 다시 누른 건 알림·위젯 푸시를 보내지 않는다. 횟수는 그대로 쌓이고, 위젯은 다음 갱신 때 반영된다.
- "그 사진이 지금 위젯에 떠 있는 사람": 그 사진이 자기가 받은 사진 중 가장 최근 것인 사람 (`/api/widget/latest` 와 같은 규칙). 예전 사진에 달린 반응·댓글은 위젯 화면을 바꾸지 않으므로 위젯 푸시를 보내지 않는다. iOS 가 위젯 푸시에도 예산을 매기기 때문이다.
- 반응·댓글·1:1 메시지 알림에는 `title` 없이 `body` 만 있다. 새 사진(보낸 사람 이름)과 단챗 메시지(방 이름)만 제목이 있다.

알림 페이로드:
```jsonc
{
  "aps": { "alert": { "title": "민지", "body": "새 사진을 보냈어요" }, "sound": "default",
           "mutable-content": 1, "thread-id": "moment-301" },   // 메시지는 "message-<보낸 사람 ID>", 단챗은 "group-<방 ID>"
  "type": "moment" | "reaction" | "comment" | "message" | "group-message",
  "momentId": 301,          // moment·reaction·comment
  "peerId": 12,             // message: 보낸 사람 ID
  "groupId": 7,             // group-message
  "thumbUrl": "/media/..."  // moment만
}
```
- `mutable-content: 1`이라서 iOS 알림 서비스 확장이 실행된다. 확장은 `/api/widget/latest`를 받아 위젯 캐시를 갱신하고, 사진을 알림에 첨부한다.
- 위젯 푸시: 헤더 `apns-push-type: widgets`, `apns-topic: <번들ID>.push-type.widgets`, 본문 `{"aps":{"content-changed":true}}`.
- APNs가 410 또는 `BadDeviceToken`·`Unregistered`를 돌려주면 그 토큰을 지운다.
