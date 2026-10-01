# API 계약

서버(`backend/`), 앱(`mobile/`), iOS 위젯·알림 확장(`mobile/targets/`)이 모두 이 문서를 기준으로 맞춘다.

- 기본 주소: `http://<host>:8080`
- 인증: `Authorization: Bearer <accessToken>` (가입·로그인·`/media` 제외 전부 필요)
- 요청·응답: JSON, 시각은 ISO-8601 UTC 문자열 (`2026-09-30T12:34:56Z`)
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
| 401 | `UNAUTHORIZED` | 토큰 없음·만료·잘못됨 |
| 401 | `INVALID_CREDENTIALS` | 로그인 실패 |
| 403 | `FORBIDDEN` | 권한 없음 (남의 댓글 삭제 등) |
| 403 | `NOT_FRIENDS` | 친구가 아닌 사람에게 메시지 |
| 404 | `NOT_FOUND` | 대상 없음, 또는 볼 권한이 없는 사진 (존재 여부를 숨기려고 404로 통일) |
| 404 | `INVITE_CODE_NOT_FOUND` | 초대 코드 없음 |
| 409 | `EMAIL_TAKEN` | 이미 가입된 이메일 |
| 409 | `ALREADY_FRIENDS` | 이미 친구 |
| 409 | `FRIEND_LIMIT_REACHED` | 나 또는 상대가 친구 20명 |

## 공통 객체

```jsonc
// UserSummary
{ "id": 12, "displayName": "민지" }

// Me
{ "id": 12, "email": "minji@example.com", "displayName": "민지", "inviteCode": "K7Q2MX9A", "friendCount": 3, "friendLimit": 20 }

// ReactionCount
{ "emoji": "🔥", "count": 2 }

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
  "myReaction": "🔥" | null,
  "commentCount": 4,
  "recentComments": [Comment]         // 최근 2개, 오래된 것 → 최신 순
}

// Message
{ "id": 77, "senderId": 12, "receiverId": 15, "text": "ㅋㅋㅋ", "createdAt": "...", "readAt": null,
  "moment": { "id": 301, "thumbUrl": "/media/..." } | null }   // 사진에 답장한 메시지면 원본 사진
```

## 인증

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/auth/signup` | `{ email, password(8~72자), displayName(1~20자) }` | 201 `{ accessToken, expiresAt, user: Me }` |
| POST | `/api/auth/login` | `{ email, password }` | 200 `{ accessToken, expiresAt, user: Me }` |

- 토큰: JWT HS256, `sub` = 사용자 ID, 유효기간 30일. 위젯과 알림 확장도 같은 토큰을 쓴다 (MVP에서는 갱신 토큰 없음).
- 이메일은 소문자로 정규화한다.

## 내 정보

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/api/me` | | 200 `Me` |
| PATCH | `/api/me` | `{ displayName }` | 200 `Me` |
| POST | `/api/me/invite-code` | | 200 `Me` (초대 코드를 새로 발급, 이전 코드는 무효) |

초대 코드: 8자, 헷갈리는 글자(0 O 1 I L) 제외한 대문자·숫자.

## 친구

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/api/friends` | | 200 `{ friends: [UserSummary], limit: 20 }` (이름순) |
| POST | `/api/friends` | `{ inviteCode }` (대소문자·공백 무시) | 201 `UserSummary` |
| DELETE | `/api/friends/{userId}` | | 204 |

- 초대 코드로 추가하면 바로 서로 친구가 된다 (코드를 건넨 것 자체가 동의).
- 한 사람당 최대 20명. 나나 상대 중 한쪽이라도 20명이면 `FRIEND_LIMIT_REACHED`.
- 친구를 끊으면 서로의 사진·댓글·반응이 즉시 안 보이고, 메시지도 보낼 수 없다. 기존 대화 기록은 남는다.

## 사진 (moment)

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| POST | `/api/moments` | multipart `image` (JPEG/PNG, 10MB 이하) | 201 `Moment` |
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
| PUT | `/api/moments/{id}/reaction` | `{ emoji }` (1~16자, 공백 불가) | 200 `Moment` |
| DELETE | `/api/moments/{id}/reaction` | | 204 |

- 한 사람이 사진 하나에 반응 하나. 다시 보내면 바뀐다.
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
| POST | `/api/moments/{id}/replies` | `{ text }` (1~500자) | 201 `Message` (사진 주인에게 가는 1:1 메시지) |
| GET | `/api/conversations` | | 200 `{ items: [{ peer: UserSummary, lastMessage: Message, unreadCount }] }` 최근 대화순 |
| GET | `/api/conversations/{peerId}/messages?cursor=&limit=50` | | 200 `{ items: [Message], nextCursor }` 최신순 |
| POST | `/api/conversations/{peerId}/messages` | `{ text }` (1~500자) | 201 `Message` (친구에게만) |
| POST | `/api/conversations/{peerId}/read` | | 204 (상대가 보낸 메시지를 읽음 처리) |

- 답장은 사진 주인과 나만 본다. 댓글과 다르다.
- 내 사진에는 답장할 수 없다 (`NOT_ALLOWED_ON_OWN_MOMENT`).

## 위젯

| 메서드 | 경로 | 응답 |
|---|---|---|
| GET | `/api/widget/latest` | 200 `WidgetLatest`, 보여줄 사진이 없으면 204 |

```jsonc
// WidgetLatest
{
  "version": "301-9f2c",            // 사진·반응·댓글이 바뀌면 달라지는 값. ETag로도 내려준다
  "moment": { "id": 301, "sender": UserSummary, "createdAt": "...", "thumbUrl": "/media/..." },
  "reactions": [ReactionCount],     // 많은 순 최대 3개
  "reactionCount": 5,               // 전체 반응 수
  "comments": [ { "author": "지우", "text": "대박" } ],   // 최근 2개, 오래된 것 → 최신 순
  "commentCount": 4
}
```

- 대상 사진: 내가 받은 친구 사진 중 가장 최근 것 (내 사진 제외).
- `If-None-Match`에 이전 ETag를 보내면 바뀐 게 없을 때 304 (본문 없음).

## 기기 (푸시 토큰)

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| PUT | `/api/devices` | `{ token, kind: "app" \| "widget", environment: "development" \| "production" }` | 204 |
| DELETE | `/api/devices/{token}` | | 204 (로그아웃할 때) |

- `token`: APNs 토큰 16진수 문자열. 같은 토큰이 다른 계정으로 오면 새 계정으로 옮긴다.
- `kind: "app"`: 앱의 알림 토큰. `kind: "widget"`: iOS 26 위젯 푸시 토큰 (위젯 확장이 직접 등록).

## 사진 파일

`GET /media/{momentId}/{full|thumb}.jpg?exp=<epoch초>&sig=<서명>` → `image/jpeg`

- 인증 헤더 없이 서명으로 확인한다. 알림 확장·위젯이 바로 받을 수 있고, 나중에 CDN이나 S3 서명 URL로 바꾸기 쉽다.
- `sig` = HMAC-SHA256(`{momentId}/{variant}:{exp}`)을 base64url(패딩 없음)로. 만료는 발급 시점에서 7일 뒤를 하루 단위로 올림 (같은 날에는 URL이 같아서 캐시가 잘 맞는다).
- 서명이 틀리거나 만료되면 403. `Cache-Control: private, max-age=86400`.

## 푸시 (서버 → APNs)

| 사건 | 받는 사람 | 알림 (kind=app 토큰) | 위젯 푸시 (kind=widget 토큰) |
|---|---|---|---|
| 새 사진 | 받는 친구 전원 | 제목 `민지`, 본문 `새 사진을 보냈어요` | 전원 |
| 반응 | 사진 주인 | `민지님이 🔥 반응을 남겼어요` | 사진을 볼 수 있는 사람 중 반응한 사람 제외 |
| 댓글 | 사진 주인 (본인이 쓴 건 제외) | `민지: 대박` | 사진을 볼 수 있는 사람 중 쓴 사람 제외 |
| 메시지·답장 | 받는 사람 | `민지: ㅋㅋㅋ` | 없음 |

알림 페이로드:
```jsonc
{
  "aps": { "alert": { "title": "민지", "body": "새 사진을 보냈어요" }, "sound": "default",
           "mutable-content": 1, "thread-id": "moment-301" },
  "type": "moment" | "reaction" | "comment" | "message",
  "momentId": 301,          // moment·reaction·comment
  "peerId": 12,             // message: 보낸 사람 ID
  "thumbUrl": "/media/..."  // moment만
}
```
- `mutable-content: 1`이라서 iOS 알림 서비스 확장이 실행된다. 확장은 `/api/widget/latest`를 받아 위젯 캐시를 갱신하고, 사진을 알림에 첨부한다.
- 위젯 푸시: 헤더 `apns-push-type: widgets`, `apns-topic: <번들ID>.push-type.widgets`, 본문 `{"aps":{"content-changed":true}}`.
- APNs가 410 또는 `BadDeviceToken`·`Unregistered`를 돌려주면 그 토큰을 지운다.
