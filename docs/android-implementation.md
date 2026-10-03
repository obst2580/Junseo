> 최신 인계·빌드·서명키·FCM 활성화 상태와 검증 제한은 [HANDOFF.md](../HANDOFF.md)를 기준으로 합니다. 아래 초기 구현 기록에는 이후 완료된 Firebase 설정도 미완료로 남아 있습니다.

# Android 구현 및 검증

2026-10-03 구현. Android 앱은 기존 Azure API와 리리플레닛 인증을 공유한다.

## 구현

- 패키지 `com.junseo.app`, 앱 링크 `junseo://auth`, Android 8.0(API 26) 이상. 현재 로컬 설치 APK는 ARM64 기기용이다.
- 기존 사진 촬영·전송, 친구, 댓글·반응, 개인·그룹 채팅, 템플릿 화면을 공통 React Native 코드로 사용한다. Android AdMob 앱 ID와 보상형 광고 단위를 iOS와 분리했으며 현재는 Google 테스트 광고를 사용한다.
- Android 홈 위젯: 작은 크기와 큰 크기로 조절, 모든 친구 또는 한 친구 선택, 최근 사진 최대 5장 넘기기, 댓글 표시, 사진 상세 열기, 수동 새로고침, 앱 복귀 시 갱신, WorkManager의 15분 주기 갱신. 최근 하루 사진이 없으면 서버가 반환하는 가장 최근 사진 한 장을 표시한다. 캐시는 마지막 확인 후 24시간이 지나면 표시하지 않는다.
- 위젯과 앱은 Android Keystore로 암호화한 인증 정보 한 개를 공유한다. 앱 백업을 비활성화하고 로그아웃·계정 변경·토큰 만료·서버 401에 인증 정보와 사진 캐시를 삭제한다. 이전 계정의 진행 중 네트워크 작업은 새 계정의 캐시를 덮어쓸 수 없다.
- Expo 네이티브 로컬 모듈과 config plugin으로 구성해 `expo prebuild` 후에도 구현이 유지된다. 생성되는 `mobile/android` 폴더는 소스 수정 대상으로 사용하지 않는다.
- 서버의 V7 마이그레이션은 기기 플랫폼을 추가하고 FCM 토큰의 대소문자를 보존한다. 기존 iOS 등록 요청은 그대로 지원한다. APNs와 FCM HTTP v1 발송을 플랫폼으로 분기한다. Android는 앱 토큰 하나로 알림과 무음 위젯 갱신을 받는다. FCM 수신 서비스는 Expo의 알림 탭 처리를 유지하며 JS 프로세스 없이 위젯 작업을 예약한다.

## 실제 확인한 결과

- 서버 전체 테스트: 113개, 실패·오류·누락 0. 기기 계정 전환, FCM 토큰 대소문자, APNs 호환성, 알림·위젯 발송, 만료 토큰 삭제 조건을 포함한다.
- Android 네이티브 테스트: API 36 에뮬레이터에서 4개 통과. 실제 Keystore 암호화 저장, 토큰 만료 시 사진·친구 캐시 삭제, 계정 전환 시 진행 중 작업 무효화, 로그아웃 및 손상된 인증 정보 처리를 검증했다.
- Android release APK 빌드와 서명 검증 통과. APK 설치 및 로그인 화면 표시, 실제 홈 화면 위젯 추가와 로그아웃 안내 표시, 위젯 친구 선택 설정 열기를 확인했다. 앱의 로그인 버튼으로 Android Chrome에서 리리플레닛 로그인 페이지가 열리는 동작도 확인했다. 실제 계정의 로그인 완료는 아직 확인하지 않았다. Firebase 설정이 없는 빌드도 로그인 화면까지 실행된다.
- 모바일 lint·TypeScript 검사·웹 export·iOS prebuild 통과. Expo Doctor 20/21 통과; 남은 항목은 이 컴퓨터의 CocoaPods 설치 확인이며 Android 빌드에는 영향을 주지 않았다.
- Firebase 프로젝트 `junseo-cbca7`와 Android 앱 등록 완료. 공개 앱 구성 정보를 넣은 APK를 같은 preview 키로 다시 빌드·서명·설치했고, API 36 에뮬레이터에서 Firebase SDK 초기화 성공과 로그인 화면을 확인했다. Firebase HTTP v1 API가 활성화된 것도 확인했다. 이번 설정 변경에서 lint·TypeScript 검사를 다시 통과했다. 기존 기능 검사와 전체 테스트 기록은 소스 fingerprint가 모두 같음을 확인해 이전 기록으로 유지한다.
- Azure에 새 JAR 배포 및 원격 SHA-256 일치 확인. 운영 DB에 V1–V7 적용. 배포 후 운영 점검 12개 통과. 새 런타임으로 교체 중 한 번 발생한 기존 비밀번호 로그인 경로의 500 응답은 교체 완료 후 404로 확인했으며 전체 점검을 다시 통과했다.

## 설치 APK

`mobile/artifacts/junseo-0.1.2-android-arm64.apk`

약 75 MiB, 내부 테스트용 release 빌드. 독립 Junseo preview 키로 서명했으며 Play Store 제출은 하지 않았다. 휴대폰에 파일을 옮겨 실행하고, Android의 해당 파일 제공 앱에 대한 설치 허용을 선택한다. 기존 기본 debug 키로 설치한 Junseo가 있으면 서명이 다르므로 그 테스트 앱을 먼저 삭제해야 한다. 같은 preview 키로 만드는 후속 APK는 업데이트할 수 있다.

폰에서 받는 [0.1.2 APK 링크](https://drive.google.com/file/d/1NyGx2KvxXjtOq9pxuoC35ylEJlH8LW5_/view?usp=drivesdk). 연결한 Google 계정의 비공개 Drive 파일이며 공개 공유 권한은 추가하지 않았다. 이전 preview APK와 서명이 같고 versionCode를 3으로 올려 앱 삭제 없이 업데이트 설치할 수 있다. 카카오톡 파일 전송용으로 APK 한 개만 담은 `mobile/artifacts/Junseo-0.1.2-Android.zip`도 생성했다.

## 0.1.2 사진 전송 수정

- 친구 추가 후 보내기에서 네트워크 오류가 표시된다는 보고를 조사했다. Expo 57은 기본 `fetch`를 `expo/fetch`로 교체한다. 기존 사진 첨부는 React Native의 `{ uri, name, type }` 객체를 사용했지만, 현재 SDK의 multipart 인코더는 이 객체를 지원하지 않아 HTTP 요청 전 `Unsupported FormDataPart implementation`으로 실패했다. 앱이 이를 네트워크 오류로 표시했다.
- 사진 첨부를 `expo-file-system`의 `File`로 바꿔 SDK가 파일 내용을 읽어 전송하도록 수정했다. 선택한 친구 ID와 전체 친구 전송, 웹 Blob 전송 방식은 유지한다. [SDK 57 파일 업로드 문서](https://docs.expo.dev/versions/v57.0.0/sdk/filesystem/#uploading-files-using-expofetch)의 방식을 적용했다.
- 설치된 SDK의 실제 multipart 인코더로 테스트했다. 수정 전 5개 중 4개가 같은 오류로 실패했고, 수정 후 사진 전송 5개와 로그인 8개가 모두 통과했다. lint, TypeScript 검사, 웹 export도 통과했다.
- API 36 ARM64 에뮬레이터의 별도 검사 번들에서 기존 방식의 오류를 재현했다. 수정된 실제 앱 API와 네이티브 File은 Azure의 인증 확인 단계까지 도달해 HTTP 401을 받았다. 사용자 토큰을 사용하지 않았으므로 사진이나 알림은 생성하지 않았다. 이는 파일 인코딩과 HTTPS 전송 확인이며 인증된 두 기기 사이의 사진 수신 검사는 별도로 필요하다.
- 검사 번들을 제외한 운영 앱을 다시 빌드했다. APK와 소스맵에서 운영 진입점, 수정된 첨부 코드, Azure 주소와 Firebase 구성을 확인했다. 같은 preview 서명으로 업데이트 설치했고 로그인 화면, Firebase 초기화, 앱 오류 0건을 확인했다. 새 APK의 Drive 파일 이름과 크기를 다시 읽어 확인했다. 상세 기록은 [android-upload-verification.json](android-upload-verification.json)에 남겼다.

## 0.1.1 로그인 콜백 수정

- S26에서 첫 로그인 후 로그인 화면으로 돌아오고, 두 번째 시도 후 앱이 검게 표시된다는 보고를 받았다. 기존 `auth` 화면은 무조건 `/login`으로 이동해, 인증 결과 저장과 겹치거나 로그인 후 접근할 수 없는 보호 경로로 이동할 수 있었다.
- 콜백에서 인증 코드 교환과 토큰 저장을 완료한 뒤 사용자 상태에 맞게 이름 설정 또는 홈으로 이동한다. 브라우저 완료 이벤트와 Router 콜백은 코드 교환과 토큰 저장을 공유해 한 번만 수행한다.
- Android가 브라우저 사용 중 앱을 재생성해도 이어갈 수 있도록 PKCE verifier와 state를 SecureStore에 10분 유효기간으로 보관한다. 잘못된 state·반환 경로는 활성 로그인 정보를 소비하지 않는다. 만료되거나 실패한 콜백에는 다시 로그인하기 버튼을 표시한다.
- 로그인 회귀 테스트 8개, lint, TypeScript 검사 통과. 테스트에는 지연된 교환 중 화면 이동 방지, 홈·이름 설정 분기, 중복 콜백, 앱 재생성, 오류 복구, 잘못된 state·URI, 만료와 이전 요청의 늦은 응답을 포함한다.
- release 빌드·서명·기존 앱 위 업데이트 설치 통과. API 36 에뮬레이터에서 Firebase 초기화, 만료 콜백의 오류 화면과 다시 로그인하기 버튼으로 로그인 화면에 복귀하는 동작을 확인했다. 실제 리리플레닛 계정의 로그인 완료는 S26에서 추가 확인해야 한다.

재생성:

```sh
GOOGLE_SERVICES_JSON="$PWD/mobile/.credentials/firebase/google-services.json" python3 ops/android/build_preview.py
```

서명 키와 암호는 Git에서 제외한 `mobile/.credentials`에 파일 권한 0600으로 저장했다. 키를 잃으면 같은 서명의 업데이트를 만들 수 없으므로 비공개로 보관한다. 앱에 서버 서비스 계정 키를 포함하지 않는다.

## Firebase 연결 상태

- 프로젝트: `junseo-cbca7`, 프로젝트 번호: `321160323457`.
- Android 앱: `com.junseo.app`, Firebase 앱 ID: `1:321160323457:android:977f115ceb66aadc528880`.
- Firebase Cloud Messaging API(V1)는 활성화됐다. 최신 APK에는 Android 푸시 등록 설정을 넣었다.
- 구성 파일 다운로드가 자동화에 전달되지 않아, 콘솔에 표시된 Android 앱 식별자와 Firebase가 자동으로 만든 Android 구성 키로 FCM에 필요한 클라이언트 설정을 구성했다. 키의 API 제한 목록을 확인했으며, 이 파일은 Git에서 제외한 `mobile/.credentials/firebase/google-services.json`에 0600으로 저장했다.
- 사용자 승인 후 `junseo-fcm-sender@junseo-cbca7.iam.gserviceaccount.com`에 `roles/firebasecloudmessaging.admin`을 적용하고 서버 JSON 키 한 개를 생성했다. 비밀 키는 Git에서 제외한 로컬 경로에 0600으로 보관하고 Azure Key Vault `junseo-kv-f14e91`의 `fcm-service-account`에 저장했다. Azure 서버의 FCM 설정을 활성화했고 Key Vault 참조 상태 `Resolved`를 확인했다.
- 같은 전용 키로 로컬에서 OAuth 인증과 FCM HTTP v1 `validate_only` 요청이 HTTP 200으로 통과했다. 이 검사는 메시지를 실제 발송하지 않는다. 설정 후 Azure 운영 점검 12개와 서버 상태 `UP`를 확인했다. 실기기의 원격 알림과 푸시에 의한 위젯 갱신은 로그인한 기기에서 확인해야 한다. 검사 기록은 [firebase-server-verification.json](firebase-server-verification.json)에 남겼다.
- 수동·앱 복귀·주기 위젯 갱신은 기존대로 구현되어 있다. 자세한 기록은 [firebase-setup.json](firebase-setup.json), [android-release.json](android-release.json)에 남겼다.

서버 연결과 Firebase 설정을 포함한 APK 빌드는 완료됐다. 서버 연결만 변경했으므로 이번에는 APK를 다시 빌드하지 않았다. 비밀 키는 채팅이나 Git에 넣지 않는다.

연결 명령:

```sh
python3 ops/azure/configure_fcm.py \
  --google-services "$PWD/mobile/.credentials/firebase/google-services.json" \
  --service-account /absolute/path/fcm-service-account.json
GOOGLE_SERVICES_JSON="$PWD/mobile/.credentials/firebase/google-services.json" python3 ops/android/build_preview.py
```

스크립트는 프로젝트·패키지 일치 여부를 확인하고 서비스 계정 JSON을 Azure Key Vault에 저장한 뒤 서버는 Key Vault 참조만 사용한다. Firebase Cloud Messaging API 활성화 및 해당 계정 권한은 Firebase 프로젝트에서 확인해야 한다. 운영 서명·Google Play 제출은 별도 배포 단계다.

## 아직 필요한 사용자 검증

실제 리리플레닛 계정으로 로그인 왕복, 카메라 촬영과 사진 전송, 두 기기 사이의 친구·개인/그룹 채팅, 로그인한 계정의 사진 위젯 표시·친구 선택·페이지 넘기기, Android 실기기의 절전 상태에서 FCM 수신·위젯 갱신. Firebase가 연결된 APK로 마지막 항목을 확인한다.
