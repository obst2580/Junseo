# Junseo Azure 서비스화 현황과 인계 문서

다른 세션에서 분석과 배포 준비를 이어가기 위한 기술 근거와 실행 계획

검토 기준  2026년 10월 2일 UTC
저장소  obst2580/Junseo
브랜치  claude/eager-gauss-5935za
고정 커밋  4f471646b8a0db74c7a2fdce2509e689ab346b9f

### 결론

Azure에서 서비스할 수 있습니다. 사진 공유·친구·채팅·위젯이 연결된 iOS 중심 MVP이므로 앱을 다시 만들거나 서버를 여러 서비스로 나눌 필요는 없습니다. 다만 현재 기본 설정을 그대로 공개하기에는 비밀키, 사진 저장, 로그인 수명, 접근 회수, 운영 검증이 부족합니다.

우선 10명 초대형 TestFlight 베타를 만들고 실제 사진량과 사용량을 본 뒤 100명으로 확대하는 방향을 권합니다. 서버 배포와 아이폰 앱의 서명·배포·실기기 검증은 각각 진행해야 합니다.

### 추천하는 시작안

- 전용 Linux App Service B1 1개 + 기존 PostgreSQL의 Junseo 전용 DB·권한 + 비공개 Blob Storage + Key Vault

- 월 추가 예산 약 2.1만~3만원. B1은 이미지 처리 메모리 제한과 부하 시험 통과가 조건입니다. 실패하면 B2로 높여 약 4.05만~4.95만원을 잡습니다.

- 기존 DB의 넓은 방화벽 규칙을 다른 앱에 영향 없이 정리하기 어렵다면 DB도 분리합니다. B1 + 전용 DB의 추가 예산은 약 5.55만~6.45만원입니다.

### 지금 먼저 결정할 다섯 가지

1. 대상: 지인 10명 베타인지, 처음부터 일반 사용자에게 공개할 것인지

2. 비용: 전용 앱 + 기존 DB를 우선 검증할지, DB까지 분리할지

3. 사진 정책: 보관 기간과 친구 해제 후 서버 접근 차단 시점

4. Apple 개발자 계정과 실제 아이폰 2대의 준비 여부

5. 기존 DB 방화벽 영향 분석 및 향후 보안·권한 설정 변경 승인 여부

비용은 월 730시간, USD 1달러당 1,500원의 예산 가정이며 부가세·Apple 가입비·도메인 등은 별도입니다. 이번 분석에서는 코드 실행, 리소스 생성·수정, 비밀 값 조회, 배포를 하지 않았습니다.

## 현재 진행상태와 다음 세션 인계

원래 목표는 아들이 만든 Junseo 앱을 사용자가 이미 운영 중인 Azure에서 서비스하는 방법을 전체적으로 분석하는 것이었습니다. 이후 사용자가 다른 세션에서 계속 확인할 수 있도록 현재 상황을 자세한 문서로 정리해 달라고 요청했습니다. 이 문서는 그 인계를 위한 기준점입니다.

| 구분 | 현재 상태 |
| --- | --- |
| 완료 | 지정 브랜치 고정 커밋의 코드·보안 정적 검토<br>기존 Azure 앱 플랜·DB 읽기 전용 확인<br>배치 대안·공식 단가·예산·출시 전 수정과 검증 목록 정리 |
| 아직 하지 않음 | Junseo 코드 수정·실행·로컬 빌드·서버 테스트 재실행<br>Azure 자원 생성·수정·배포·DB migration<br>비밀 값 조회·생성·입력·교체<br>APNs 실전송·실제 아이폰 TestFlight QA |
| 현재 판단 | Azure 배포는 가능<br>현재 기본 설정의 즉시 공개 운영은 보류<br>전용 B1 + 안전하게 분리한 기존 DB 사용이 첫 후보 |
| 사용자 결정 대기 | 초대 베타 범위 · 비용안 · 데이터 보관과 접근 회수<br>기존 DB 방화벽 영향 조사 후 변경 여부<br>Apple 계정·실기기·개발 담당 준비 |

### 사용자 Mac의 개발 팀과 Junseo는 아직 연결하지 않았다

사용자는 Mac에서 Claude Code와 Codex를 함께 활용하는 개발 팀 구성을 진행 중입니다. 관련 작업 폴더는 /Users/obst/Documents/Codex/2026-10-02/task-2/app-team 입니다. 전달된 진행상태상 샘플 Python·Node 작업만 검증했고 Java·iOS·외부 DB 작업 준비는 아직 확인하지 않았습니다. 별도 대시보드도 개발 중입니다.

Junseo 작업을 이 팀에 실제 할당하거나 해당 폴더에서 실행한 상태는 아닙니다. 다음 세션은 기존 팀의 성공 기록을 Junseo 빌드 성공으로 간주하지 말고, 실제 컴퓨터 연결·도구·저장소 위치·Java 21·Xcode·PostgreSQL 테스트 준비부터 확인해야 합니다.

### 이어서 볼 때의 순서

먼저 기준 SHA와 현재 HEAD의 차이를 읽고, 분석·수정·테스트·배포 중 맡길 범위를 확인합니다. 구체적 착수 순서와 붙여넣기 프롬프트는 문서 마지막에 있습니다. 비용·보안·기존 서비스에 영향을 주는 변경은 제안과 승인 이후로 둡니다.

## 1 검토 범위와 앱의 현재 구조

이 보고서는 지정 브랜치의 고정 커밋과 기존 Azure 계정의 읽기 전용 확인 결과를 합친 서비스화 계획입니다. 소스상 확인된 사실, 운영 계정에서 본 사실, 권고·예산 추정을 구분했습니다.

| 구성 | 확인 내용 | 서비스화 의미 |
| --- | --- | --- |
| 백엔드 | Java 21 · Spring Boot 4.1.1<br>REST · JWT · WebSocket | 현재 실행 JAR을 Azure App Service Java SE 21에 배포 |
| 데이터베이스 | PostgreSQL · JPA<br>Flyway V1~V5 | 독립 DB와 최소 권한 계정, 마이그레이션·복구 절차 필요 |
| 모바일 | Expo 57 · React Native 0.86.3<br>React 19.2.3 · TypeScript | 실제 HTTPS API 주소를 넣어 별도 빌드 |
| iOS 네이티브 | iOS 17 이상 · SwiftUI 위젯<br>알림 확장 · App Group | 서명·권한·APNs 환경을 맞춘 실기기 검증 필요 |
| 사진과 템플릿 | 현재 LocalMediaStorage<br>파일시스템 저장 | Blob 어댑터와 권한·보관·삭제 정책 필요 |
| 실시간과 푸시 | JVM 메모리의 연결 목록<br>서버가 Apple APNs에 직접 전송 | 처음은 단일 인스턴스, 이후 공유 이벤트·재시도 설계 |

### 이미 갖춘 기능

이메일 가입·로그인, 초대코드 친구 연결, 사진 전송과 수신자 지정, 댓글·반응, 1대1·단체 채팅, 홈 위젯, 알림 확장, 서버 배포형 사진 템플릿이 구현돼 있습니다. 친구 수는 양쪽 모두 20명 상한을 검사합니다.

DB에는 사용자·친구관계·사진·수신자·댓글·반응·기기 토큰·채팅·그룹·템플릿 정보가 저장됩니다. 메시지 client_id 고유 제약으로 재전송 중복 저장을 줄이고, 사진은 수신자 스냅샷과 현재 친구관계를 함께 확인합니다.

### 이번에 확인한 범위와 한계

- 재귀 트리 257개 파일, 텍스트 220개를 취득해 주요 서버·모바일·보안·테스트·CI 경로를 정적으로 검토했습니다.

- 소스의 서버 테스트 선언은 96개입니다. 이번에 재실행해 통과한 결과는 아닙니다.

- 동일 HEAD의 GitHub Actions iOS Release 시뮬레이터 빌드 성공은 확인했습니다. 실제 APNs·위젯, 서버 테스트, Azure 배포 성공까지 의미하지 않습니다.

- Git 과거 이력, 전체 의존성 취약점, 바이너리, 실제 운영 데이터·비밀키, 실기기 성능은 별도 검증이 필요합니다.

근거  [고정 커밋](https://github.com/obst2580/Junseo/commit/4f471646b8a0db74c7a2fdce2509e689ab346b9f)  ·  [서버 의존성](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/build.gradle.kts)  ·  [모바일 설정](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/mobile/package.json)  ·  [iOS CI 실행](https://github.com/obst2580/Junseo/actions/runs/36961376763)

## 2 로그인과 사진 전송은 어떻게 동작하는가

### 현재 로그인 흐름

1. 사용자가 이메일·비밀번호를 앱에 입력하면 Azure에 배포할 서버의 가입 또는 로그인 API가 받습니다.

2. 서버는 비밀번호를 BCrypt로 저장·검증하고 사용자 ID를 담은 HS256 JWT를 발급합니다. 기본 유효기간은 30일입니다.

3. 앱은 토큰을 네이티브 Keychain에 저장합니다. 위젯·알림 확장에 전달할 때는 App Group UserDefaults에도 사본을 저장합니다.

4. REST API는 서명과 만료, 사용자 존재를 확인합니다. WebSocket은 첫 인증 프레임에서 JWT를 확인한 뒤 변경 신호를 보냅니다.

5. 현재 로그아웃은 푸시 등록 해제 시도와 기기 토큰·위젯 상태 삭제입니다. 서버가 이미 발급한 JWT를 개별 폐기하는 흐름은 없습니다.

Azure에 배포한다고 앱 사용자가 Microsoft 계정으로 로그인해야 하는 것은 아닙니다. 기존 이메일·비밀번호 인증을 강화하면 됩니다. Managed Identity는 서버가 Blob·Key Vault 등에 접근하기 위한 인프라 신원입니다.

### 출시 전 바꿀 인증 흐름

- 짧은 access token + 회전 refresh token 또는 세션 저장소를 두고, 로그아웃·비밀번호 변경·기기 분실 시 서버에서 폐기합니다.

- 위젯과 확장도 공유 Keychain·토큰 갱신·재로그인 정책을 따르게 합니다. WebSocket은 토큰 만료·세션 폐기 때 종료하거나 재인증합니다.

- 공개 서비스라면 이메일 소유 확인, 비밀번호 복구, 본인 재인증을 거치는 계정 삭제를 갖춥니다. 베타에서는 가입 허용 대상과 지원 절차를 명확히 합니다.

### 현재 사진 흐름과 중요한 정책 차이

JPEG·PNG 10MB 이하를 받아 실제 이미지 형식과 픽셀 수를 검사하고, 메타데이터 제거와 EXIF 회전을 거쳐 최장 1440px 이미지·540px 썸네일을 만듭니다. DB에 수신자 스냅샷을 저장한 뒤 APNs와 실시간 신호를 보냅니다.

사진 REST 조회는 현재 친구관계를 확인하지만, 이미 발급한 미디어 URL은 서명·만료만 확인해 약 7~8일 동안 사용할 수 있습니다. 친구 해제 후에도 그 URL로 새 다운로드가 가능할 수 있습니다. 즉시 차단을 원하면 수신자·권한 버전 또는 폐기 가능한 grant를 검증해야 합니다.

24시간은 위젯 피드의 조회 범위입니다. 서버 사진이 24시간 뒤 자동 삭제된다는 뜻은 아닙니다. 이미 내려받은 사진이나 스크린샷까지 회수할 수 있다고 약속해서도 안 됩니다.

근거  [인증 서비스](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/main/java/com/junseo/auth/AuthService.java#L44-L76)  ·  [토큰 저장](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/mobile/src/lib/tokenStore.ts)  ·  [위젯 공유 상태](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/mobile/targets/_shared/WidgetShared.swift#L22-L32)  ·  [미디어 서명](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/main/java/com/junseo/media/MediaUrlSigner.java#L24-L66)

## 3 기존 Azure에서 확인한 상태

2026년 10월 2일 05시 19분~24분 UTC에 포털을 읽기 전용으로 확인했습니다. 아래 수치는 관측 시점의 상태이며 장기 피크나 새 앱 수용 능력을 보장하지 않습니다.

| 항목 | 실제 확인 | 판단 |
| --- | --- | --- |
| App Service | studylog-plan · Korea Central<br>Linux B2 · 1개 인스턴스<br>앱 21개 · 슬롯 0개 | 기존 앱들과 CPU·메모리 공유 |
| 앱 플랜 관측 | CPU 평균 12.97%<br>메모리 평균 75.28% | 차트 기간·7일 최대값 미확인<br>추가 Java 앱 즉시 투입 보류 |
| PostgreSQL | studylog-db · PostgreSQL 16.15<br>B1ms 1vCore · 2GiB<br>32GiB P4 · 120 IOPS | HA 없음 · 저장 자동 증가 꺼짐<br>백업 보존 7일 |
| DB 1시간 관측 | CPU 11.65% · 메모리 59.88%<br>저장 13.68% 약 4.38GiB<br>실패 연결 0건 | 애플리케이션 DB 12개<br>max_connections 100으로 재정의 |
| DB 네트워크 | 공용 접근 켜짐<br>AllowAll 전체 IPv4 범위<br>모든 Azure 서비스 허용 켜짐 | 인증은 필요하지만 인터넷 전체가<br>5432 연결을 시도할 수 있음 |

### 최우선 운영 점검은 DB 방화벽

AllowAll 0.0.0.0–255.255.255.255와 모든 Azure 서비스 허용은 연결 대상을 매우 넓힙니다. 실제 데이터 유출이 확인된 것은 아닙니다. 특히 Azure 서비스 허용은 다른 고객 구독까지 포함하므로 자기 구독만 허용한다고 생각하면 안 됩니다.

기존 12개 앱 DB가 있으므로 먼저 규칙을 지우면 정상 서비스가 끊길 수 있습니다. 의존 앱별 송신 IP·관리자 접속 경로를 확인하고 좁은 규칙을 추가한 뒤 앱별 smoke test를 거쳐 넓은 규칙을 제거해야 합니다. 변경안과 복구 절차를 승인받은 후 수행할 작업입니다.

### 공유 자원을 재사용할 조건

- 앱 플랜: 7일 이상 피크와 재시작 이력, 새 JVM의 최대 RSS·이미지 동시 처리·다른 앱 영향까지 확인

- DB: 전용 DB·최소 권한 역할, 실제 활성·유휴 연결과 기존 pool 총합, TLS, 넓은 방화벽 개선

- 현재 DB max_connections=100입니다. 문서의 B1ms 기본값을 현재 실설정으로 잘못 적용하면 안 됩니다. Junseo pool은 3~5부터 검증하는 안입니다.

포털에 다음 DB 유지 관리 시각은 2026년 10월 5일 14:32 UTC, 한국시간 23:32로 표시됐습니다. 배포 시 다시 확인하고 전후 재접속·재동기화를 시험합니다.

근거  [플랜 자원 공유](https://learn.microsoft.com/en-us/azure/app-service/overview-hosting-plans)  ·  [PostgreSQL 공용 방화벽](https://learn.microsoft.com/en-us/azure/postgresql/network/concepts-networking-public)

## 4 가장 단순한 서비스 구성

초기에는 전용 App Service 1개에 Java API를 올리고 DB·사진·비밀 관리를 분리합니다. Kubernetes, Redis, Front Door, API Management, 새 NAT Gateway는 기본 구성에 넣지 않습니다. 실제 필요가 확인될 때 추가합니다.

| 연결 | 구성 | 구현 또는 운영 조건 |
| --- | --- | --- |
| 아이폰 → API | HTTPS 및 WSS<br>App Service Java SE 21 | HTTPS only · TLS 1.2 이상<br>Always On · 프록시 헤더 처리 |
| API → DB | 기존 PostgreSQL의 junseo DB<br>또는 Junseo 전용 서버 | 전용 역할 · JDBC TLS 검증<br>migration과 runtime 권한 분리 |
| API → 사진 | Private Blob Storage<br>사용자 사진·공용 템플릿 분리 | MediaStorage Blob 어댑터 구현<br>익명 접근 끔 · 최소 권한 |
| API → 비밀 | Key Vault Standard<br>Managed Identity | JWT·미디어 키·DB 암호·APNs<br>키 원문을 Git·앱·로그에 두지 않음 |
| API → 아이폰 | Apple APNs 직접 전송 | bundle · team · key · 환경 일치<br>전송 실패와 무효 토큰 관측 |
| 개발 → 배포 | GitHub Actions<br>동일 JAR 아티팩트 배포 | 격리 테스트 DB · 배포 승인<br>OIDC는 승인 후 제한 범위 구성 |

### 사진 저장 전환은 설정만으로 끝나지 않는다

현재 LocalMediaStorage만 빈으로 만들어집니다. Blob 계정을 만들고 환경변수만 넣어도 사진이 자동으로 Blob에 저장되지는 않습니다. 어댑터를 구현하고 업로드·읽기·삭제·실패 재시도·복구를 검증해야 합니다.

처음에는 기존 미디어 API 경로를 유지하며 서버가 DB 상태와 열람 권한을 확인하고 private Blob을 읽어 주는 구성이 이해하기 쉽습니다. 직접 SAS 다운로드를 선택하면 만료와 접근 회수 정책을 별도로 설계합니다.

짧은 내부 베타에서 로컬 파일을 유지하려면 /home/junseo/media 같은 배포 폴더 밖 영속 경로를 명시하고 재시작·재배포·복원 시험을 통과해야 합니다. 플랜의 10GB 저장 공간과 운영 한계를 공유하므로 최종 사진 저장안으로 권하지 않습니다.

### 단일 인스턴스와 송신 네트워크

현재 WebSocket 연결은 한 JVM의 메모리에만 있습니다. 서버를 두 개로 늘리기 전에 공유 이벤트 계층과 재접속 후 REST 동기화를 설계해야 합니다. 외부에서 단일 고정 송신 IP를 요구하지 않는 한 NAT은 기본 필수가 아닙니다. 기존 송신 IP 목록을 DB 방화벽에 허용하고 변경 시 갱신하는 방식부터 검토합니다.

근거  [Java 배포](https://learn.microsoft.com/en-us/azure/app-service/configure-language-java-deploy-run?pivots=platform-linux)  ·  [Key Vault 참조](https://learn.microsoft.com/azure/app-service/app-service-key-vault-references)  ·  [송신 IP](https://learn.microsoft.com/en-us/azure/app-service/overview-inbound-outbound-ips)  ·  [현재 MediaConfig](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/main/java/com/junseo/media/MediaConfig.java)

## 5 세 가지 배치안과 월 비용

아래는 기존 비용에 더해지는 Junseo의 월 증분 추정입니다. Korea Central 종량제, 월 730시간, 단일 서버, 사진 10GB 이내, 월 읽기 5만 회·쓰기 1만 회, 소량 로그를 가정합니다. 10~100명의 등록 사용자는 동시접속 100명과 다릅니다.

| 배치안 | USD 월 추가 | 원화 예산 | 선택 조건 |
| --- | --- | --- | --- |
| A 모두 공유<br>기존 B2 + 기존 DB | $1~7 | 1,500~10,500원 | 가장 저렴<br>메모리·DB 보안·연결 여유 확인 전 보류 |
| B 앱 분리 추천<br>전용 B1 + 기존 DB | $14~20 | 21,000~30,000원 | B1 이미지 부하 시험 통과<br>기존 DB 안전한 공유 가능 |
| C 앱과 DB 분리<br>전용 B1 + 신규 B1ms | $37~43 | 55,500~64,500원 | 기존 DB 변경 위험이 크거나<br>독립 복구·장애 범위가 중요 |

B1 메모리 검증이 실패하면 전용 B2로 높입니다. B안은 $27~33, 약 40,500~49,500원이고 C안은 $50~56, 약 75,000~84,000원입니다. 이것은 과금 상한이나 성능 보장이 아닙니다.

### 공식 단가와 계산 근거

| 항목 | 2026년 10월 2일 조회 단가 | 월 계산 |
| --- | --- | --- |
| Linux App Service B1 | $0.018 / 시간 | $13.14 |
| Linux App Service B2 | $0.036 / 시간 | $26.28 |
| PostgreSQL B1ms | $0.026 / 시간 | $18.98 |
| PostgreSQL 저장 32GB | $0.131 / GB 월 | $4.192 · 신규 DB 합계 $23.172 |
| Blob Hot LRS 참고 | 27.295원 / GB 월<br>읽기 5.459원 · 쓰기 68.2375원 / 1만 회 | 10GB + 읽기 5만 + 쓰기 1만<br>약 368.49원 · 네트워크 별도 |

App Service·DB의 USD 단가는 Azure Retail Prices API에서 Korea Central·Consumption을 필터한 공식 소매가격입니다. 원화 예산은 별도 가정환율 1USD=1,500원입니다. Blob의 원화값은 API가 반환한 참고값으로 두 계산 기준을 혼동하지 않아야 합니다.

### 빠뜨리기 쉬운 비용

- 부가세·도메인·Apple Developer Program 표준 $99/년은 별도입니다. 이미 유효한 가입이면 다시 가입할 필요는 없습니다.

- 인터넷 송신의 첫 100GB 무료 적용·기존 소비량을 확인해야 합니다. 앱마다 별도 100GB라고 계산하지 않습니다. 추가 유료 100GB는 아시아 표 기준 약 $12입니다.

- DB 무료 750시간·32GB 안내만으로 이 구독의 잔여 혜택·만료를 확정할 수 없습니다. 최신 전체 청구서는 이번에 재계산하지 않았습니다.

과거 전달된 기반비용 38,765.82원/월이 그대로라는 조건이면 B안 총합은 약 59,800~68,800원/월, 부가세 전입니다. 최신 실청구 총액을 확인한 숫자는 아닙니다.

근거  [Retail Prices API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices)  ·  [App Service 요금](https://azure.microsoft.com/en-us/pricing/details/app-service/linux/)  ·  [PostgreSQL 요금](https://azure.microsoft.com/en-us/pricing/details/postgresql/flexible-server/)  ·  [Blob 요금](https://azure.microsoft.com/en-us/pricing/details/storage/blobs/)  ·  [송신 요금](https://azure.microsoft.com/en-us/pricing/details/bandwidth/)

## 6 공개 배포 전에 반드시 해결할 항목

P0는 그 조건이 남아 있으면 공개 운영을 막아야 하는 항목입니다. 소스의 기본값이나 누락을 확인한 것이며, 실제 운영이 이미 잘못 설정됐거나 침해됐다고 판단한 것은 아닙니다.

### P0 1 운영 비밀키의 기본값 제거

환경변수가 빠지면 저장소의 개발용 JWT·미디어 HMAC 키로 기동할 수 있습니다. Secrets는 길이가 32바이트보다 짧으면 막지만 개발용 값은 경고 후 계속 사용합니다. 그 상태로 공개하면 사용자 사칭 토큰이나 사진 서명을 위조할 위험이 있습니다.

- 운영 프로필에서 개발 기본값을 제거하고 누락·개발용 값·서명키 재사용 때 기동 실패

- JWT 키와 사진 키는 서로 다른 충분히 무작위인 값으로 주입하고 원문 출력 금지

- 운영에 dev 프로필 금지. 이미 기본키로 공개했다면 교체·토큰 무효화와 기존 URL 영향 점검

### P0 2 실제 HTTPS API 주소를 넣은 앱 빌드

EXPO_PUBLIC_API_URL의 코드 기본값은 http://localhost:8080입니다. 아이폰의 localhost는 Azure 서버가 아닙니다. production·preview에 실제 HTTPS URL을 지정하고 http·localhost·개발 주소를 운영 빌드에서 거부해야 합니다.

앱 fetch, WSS, 위젯 App Group에 전달되는 주소까지 검사합니다. EXPO_PUBLIC 값은 앱에서 보이므로 비밀키를 넣지 않습니다. 현재 EAS 파일에 API URL은 없으며 외부 EAS 환경 설정 여부는 미확인입니다.

### P0 3 사진의 영속 저장과 복구

기본 ./data/media는 배포 환경에 따라 임시 디스크일 수 있습니다. private Blob 어댑터를 구현하거나 검증된 영속 경로를 쓰고, 재시작·재배포 뒤 사진 유지와 별도 환경 복원을 실제 시험합니다. DB 백업만으로 사진이 복구되지는 않습니다.

### P0 4 실사용 환경의 개인정보 로그와 설정 검증

APNs가 꺼졌거나 설정이 누락되면 LoggingPushSender가 이름·댓글·채팅 일부·서명된 사진 URL을 INFO 로그에 기록할 수 있습니다. 실사용자 환경에서는 본문·서명 URL을 제거하고 운영 설정을 명시적으로 검증합니다. 실제 로그 유출이 확인된 것은 아닙니다.

### P0에 준해 배포를 보류할 운영 조건

기존 B2 메모리 여유를 검증하지 않은 공유 배포, DB 전체 허용 규칙을 그대로 둔 무계획 확장, 백업·복원 시험 없는 실제 사용자 데이터 투입은 피합니다. 기존 방화벽을 수정할 때는 다른 서비스의 연결을 먼저 보존해야 합니다.

근거  [운영 설정](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/main/resources/application.yml#L26-L46)  ·  [키 검증](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/main/java/com/junseo/common/security/Secrets.java#L16-L25)  ·  [앱 API 주소](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/mobile/src/lib/config.ts)  ·  [사진 저장](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/main/java/com/junseo/media/LocalMediaStorage.java)  ·  [푸시 로그](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/main/java/com/junseo/push/LoggingPushSender.java)

## 7 베타와 공개 출시를 위한 보안 보완

| 우선순위 | 확인한 문제 | 권고와 통과 기준 |
| --- | --- | --- |
| P1 | 30일 bearer 토큰<br>개별 서버 폐기 없음 | 짧은 access + 회전 refresh/session<br>로그아웃·분실·변경 후 재사용 차단 |
| P1 | 친구 해제 후 기존 사진 URL<br>약 7~8일 재사용 가능 | 즉시 회수 또는 기간 한정 정책 결정<br>같은 URL로 재조회하는 회귀 시험 |
| P1 | 가입·로그인·업로드·기기 등록<br>앱 차원 남용 제한 부재 | IP·계정 기준 한도와 지연<br>저장량·기기 수·동시 이미지 처리 제한 |
| P1 | 이메일 확인·복구·탈퇴 없음 | 공개 출시 전 정상 지원 흐름 마련<br>탈퇴 시 DB·사진·기기·캐시 처리 |
| P1 | APNs 실기기 전달과 복구<br>영속 큐·재시도 없음 | 두 아이폰으로 실제 검증<br>실패 기록·무효 토큰 제거·재동기화 |
| P2 | WS 최초 인증 후 만료·계정 상태<br>재확인과 인증 대기 상한 없음 | 미인증 연결 제한·만료 종료<br>IP·계정별 연결 수 제한 |
| P2 | 템플릿 파일 덮어쓰기<br>버전 URL과 불변 캐시 불일치 | 버전별 객체 키에 저장<br>성공 후 DB 포인터 전환·고아 파일 정리 |

### 사진 삭제와 접근 회수는 별도로 검증

현재 파일 삭제 실패는 로그만 남습니다. DB에서 지운 사진이라도 파일이 남고 서명 URL이 유효하면 미디어 경로에서 제공될 수 있습니다. 미디어 요청 때 DB 상태·폐기 권한을 확인하고 삭제 재시도를 구현해야 합니다. TTL만 줄이는 것은 즉시 회수를 보장하지 않습니다.

### 좋은 보안 기반도 유지

BCrypt, 비밀번호 UTF-8 72바이트 검사, 로그인 실패의 공통 응답, 사용자 존재 확인, 사진 수신자·현재 친구 검사, 댓글 삭제 권한, 그룹 멤버 검사, 이미지 실제 형식 확인·재인코딩이 있습니다. 확인한 SQL은 매개변수 바인딩을 사용합니다.

템플릿 관리자 API는 별도 헤더 토큰을 constant-time 비교하고 미설정 시 404를 반환합니다. SecurityConfig의 permitAll만 보고 무인증 관리자라고 결론내리면 안 됩니다. 다만 강한 키·회전·감사·운영 접근 제한은 보강하는 편이 좋습니다.

### 개인정보와 사용자 생성 콘텐츠

서비스 대상 국가·연령·공개 범위를 정하고 사진·메시지 보관기간, 계정 삭제, 신고·차단, 알림 미리보기 정책을 정해야 합니다. 일반 공개에 앞서 적용 법률과 스토어 정책을 최신 공식 기준으로 별도 확인해야 하며, 이 보고서는 법적 준수 판정을 내리지 않습니다.

근거  [JWT 구현](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/main/java/com/junseo/common/security/JwtService.java)  ·  [사진 권한](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/main/java/com/junseo/moment/MomentRepository.java)  ·  [WS 인증](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/main/java/com/junseo/realtime/RealtimeHandler.java)  ·  [관리자 토큰](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/main/java/com/junseo/template/TemplateAdminController.java)

## 8 작은 서버에서 안전하게 운영하는 방법

### B1을 선택하려면 이미지 메모리부터 측정

현재 입력은 최대 5천만 픽셀을 허용합니다. 파일이 10MB보다 작아도 디코딩한 버퍼와 회전·축소용 래스터는 훨씬 클 수 있습니다. 정확한 RAM 수요는 아직 측정하지 않았습니다. 1.75GB급 B1에서 안전하다고 단정할 수 없습니다.

- 디코딩 픽셀 상한을 낮추고 필요하면 읽기 단계 축소, 이미지 동시 처리 수 제한, 사용자 일 업로드·총 저장량 제한을 적용합니다.

- JVM 힙 외 네이티브 메모리·스레드·메타스페이스·이미지 버퍼를 포함한 최대 RSS를 봅니다. 한도 초과 때 429·명확한 오류를 주고 프로세스가 죽지 않게 합니다.

- 실제 최대 사진을 순차 1개와 동시 2~3개, API 동시 10요청, WebSocket 20연결부터 시험합니다. 이 숫자는 초기 시험안이지 지원 용량 약속이 아닙니다.

제안 통과 기준은 지속 메모리 80% 미만, OOM 0건, API 오류율 1% 미만, 기존 앱 영향 없음입니다. p95 응답시간과 사진 처리 목표는 실제 사용자 경험에 맞춰 별도로 합의합니다. 실패하면 처리 경로를 줄이거나 B2로 높입니다.

### 백업은 복원할 수 있어야 의미가 있다

현재 DB 백업 보존은 7일입니다. 필요한 복구 기간과 데이터 손실 허용치를 먼저 정하고 DB PITR과 Blob 삭제 보존·버전 정책을 맞춥니다. 공식 DB 백업의 최대 35일 보존·일반적 RPO 설명이 이 서비스의 실제 복구시간을 보장하지는 않습니다.

공유 PostgreSQL의 PITR은 서버 단위로 새 서버를 복원합니다. Junseo만 되돌리려면 별도 서버 복원 후 해당 DB를 선택적으로 가져오는 절차와 임시 비용이 필요합니다. App Service 백업이 Flexible Server를 함께 백업한다고 가정해서는 안 됩니다.

새 환경에 DB와 사진을 함께 복원해 누락 사진·고아 객체를 검사합니다. 삭제 정책에는 현재 데이터뿐 아니라 백업·버전 보관 기간과 사용자 안내도 포함합니다.

### 최소 관측과 비용 제어

- 오류율·지연·메모리·재시작·DB 연결·CPU 크레딧·저장량·APNs 실패·백업 실패를 관측합니다. DB 저장 70%·85% 같은 경보 기준부터 검토합니다.

- Authorization, 사진 본문, 메시지 본문, 푸시 토큰, 서명 URL을 수집하지 않습니다. 로그 샘플링·보존기간·예산을 정합니다.

- 예산 50%·80%·100% 및 예측 초과 알림을 둡니다. Azure Budget은 자동 차단이 아니며 로그 daily cap도 절대적인 청구 상한이 아닙니다.

### 100명 이후 확장은 사용량을 보고 결정

단일 인스턴스 장애나 배포 중 짧은 중단을 허용할지 먼저 결정합니다. 다중 인스턴스로 가려면 공유 실시간 이벤트, 멱등 처리, 비동기 이미지 작업, 푸시 outbox·재시도를 검토하고 HA 비용을 다시 계산합니다.

근거  [이미지 처리](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/main/java/com/junseo/media/ImageProcessor.java)  ·  [DB 백업](https://learn.microsoft.com/en-us/azure/postgresql/backup-restore/concepts-backup-restore)  ·  [App Service 백업](https://learn.microsoft.com/en-us/azure/app-service/manage-backup)  ·  [예산 알림](https://learn.microsoft.com/en-us/azure/cost-management-billing/costs/tutorial-acm-create-budgets)

## 9 서버와 아이폰 앱의 배포 절차

### 서버 배포

1. release 커밋을 고정하고 Java 21에서 서버 테스트와 JAR 빌드, 별도 PostgreSQL에서 Flyway 검증을 실행합니다. 의존성 취약점 점검도 추가합니다.

2. 운영 프로필, health·liveness·readiness, 비밀 필수 검증, Blob 어댑터, 제한·로그 마스킹을 준비합니다. 테스트 프로필은 Flyway clean·truncate를 사용하므로 운영 DB에 연결하면 안 됩니다.

3. Bicep 또는 동등한 배포 명세에 신규·기존 리소스를 명확히 나누고 변경 미리보기로 공유 플랜·DB 삭제나 교체가 없는지 확인합니다.

4. 비용·보안 변경을 승인받은 후 앱·스토리지·DB 역할·비밀 접근을 구성합니다. APNs 코드는 .p8 파일 경로를 읽으므로 Key Vault 메모리 로딩 또는 안전한 파일 배치 경로가 추가로 필요합니다.

5. 격리된 테스트 환경에서 migration과 smoke test를 통과한 동일 JAR을 배포합니다. 처음에는 인스턴스 1개, HTTPS only, Always On으로 검증합니다.

6. 배포 뒤 가입·사진·DB·Blob·WSS·APNs 경로와 로그를 확인합니다. 실패하면 이전 JAR로 복구하고 DB 스키마 호환성을 점검합니다.

Basic 플랜에는 배포 슬롯이 없습니다. 무중단 swap을 약속할 수 없으므로 초기에는 정비창과 이전 JAR 재배포 방식, 이전 코드와 호환되는 migration을 사용합니다. 슬롯이 필요하면 Standard 이상 비용을 다시 산정합니다.

### CI 성공을 해석하는 범위

현재 iOS 워크플로는 Release 시뮬레이터 빌드, 앱·위젯·알림 확장 포함 확인, 시뮬레이터 실행, 로그인 화면 OCR을 포함합니다. 같은 커밋에서 성공한 기록은 있지만 backend CI나 실기기 푸시 성공의 증거는 아닙니다. backend 테스트·mobile lint·typecheck와 배포 workflow를 추가합니다.

### 아이폰 배포

1. Apple 개발자 계정, Bundle ID, App Group, team, entitlement, APNs key·환경을 맞춥니다.

2. 실제 HTTPS API URL을 넣고 서명된 iOS 빌드를 만듭니다. TestFlight·App Store는 APNs production 환경 정합성을 확인합니다.

3. TestFlight로 실제 아이폰 2대에서 검증하고 초대형 베타를 시작합니다. 서버 URL이 생기는 것만으로 앱 설치·위젯·알림이 완성되지는 않습니다.

TestFlight 빌드는 90일 유효하며 첫 외부 테스터 배포에 Beta App Review가 필요할 수 있습니다. Expo Free 한도 안에서는 유료 EAS가 필수는 아니지만 네이티브 타깃 호환과 실제 빌드 한도를 확인해야 합니다. 웹 미리보기와 Android는 현재 iOS 핵심 기능과 동등한 출시 범위로 보지 않습니다.

근거  [현재 iOS workflow](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/.github/workflows/ios.yml)  ·  [테스트 설정](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/test/resources/application-test.yml)  ·  [배포 슬롯](https://learn.microsoft.com/en-us/azure/app-service/deploy-staging-slots)  ·  [TestFlight](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview/)  ·  [Expo 과금](https://docs.expo.dev/billing/faq/)

## 10 베타까지 예상 일정과 담당 작업

아래는 저장소를 이해하는 개발자 1명이 집중 작업하고, Azure·Apple 접근과 실기기 준비가 되어 있다는 가정의 5~10영업일 추정입니다. 완성된 공개 출시 일정이나 고정 견적은 아닙니다. 인증·스토리지 재설계, 기기 이슈, Apple 검토가 길어지면 추가 기간이 필요합니다.

| 기간 | 핵심 작업 | 완료 판단 |
| --- | --- | --- |
| 1~2일 | 운영 설정·키 검증<br>API 주소·로그 마스킹<br>베타 가입 제한 | 누락 설정이 안전하게 실패<br>개발 기본값으로 운영 불가 |
| 3~4일 | Blob 어댑터·삭제 재시도<br>권한 회수 정책<br>인증·업로드 한도 | 사진 영속성·권한 회귀 시험<br>메모리 제한 동작 |
| 5~6일 | backend CI·격리 DB<br>배포 명세·health·rollback<br>승인 후 Azure 구성 | 동일 release 아티팩트 배포<br>기존 앱 무영향 확인 |
| 7~8일 | 서명 빌드·TestFlight<br>APNs·위젯·만료·오프라인<br>두 실기기 점검 | 두 사용자 사이 전체 흐름 성공<br>실패·복구 시나리오 통과 |
| 9~10일 | 부하·백업 복원<br>관측·예산·지원 절차<br>10명 초대형 베타 | 출시 차단 결함 없음<br>측정치에 따라 B1 또는 B2 선택 |

### 작업 역할

- 앱 개발 담당: 인증·Blob·권한·제한 구현, 자동 테스트, iOS 서명 설정과 실기기 동작 수정

- Azure 운영 담당: 기존 앱 의존 관계 파악, 비용·방화벽 변경안, 최소 권한·배포·복구·경보 검증

- 서비스 소유자: 대상 사용자·데이터 정책·비용·중단 허용치 결정, 계정·보안 변경 승인, 초대 베타 운영

### 일정에서 분리해서 봐야 하는 작업

일반 공개에 필요한 비밀번호 복구·이메일 확인·탈퇴·신고·차단, 약관·개인정보 안내, App Store 심사 준비는 베타 범위를 넘어설 수 있습니다. 1~2주 안에 무조건 끝난다고 합산해서 약속하지 않습니다.

현재 코드를 단순히 인터넷에 올리는 작업은 더 짧을 수 있지만, 그것만으로 안전한 서비스가 되지는 않습니다. 반대로 기능을 대폭 추가하거나 처음부터 고가용성 구조를 만들 필요도 없습니다. 베타 통과 기준으로 작업 범위를 고정하는 편이 현실적입니다.

### 베타에서 100명으로 늘리는 시점

10명에게 사진·푸시·위젯의 핵심 흐름과 복구가 안정적으로 동작하고, 실제 일활성·일 사진 수·p95 지연·최대 RSS·DB 연결·월 예상비용이 목표 안에 들어온 뒤 확대합니다. 문제가 남으면 사용자 수를 늘리는 대신 해당 경로를 먼저 수정합니다.

## 11 배포 전 검증 체크리스트

이 목록은 합격 기록을 남길 인수 기준입니다. 현재 전부 통과했다는 뜻이 아닙니다. 각 항목에 커밋·환경·기기·시간·결과를 기록합니다.

| 검증 분야 | 필수 시험 | 통과 판단 |
| --- | --- | --- |
| 설정 | 키 누락·개발키·잘못된 API 주소<br>운영 dev 프로필·APNs 누락 | 운영 시작 또는 빌드가 안전하게 실패<br>비밀 원문이 출력되지 않음 |
| 인증 | 가입·로그인·잘못된 비밀번호<br>만료·로그아웃·기기 분실·재설정 | 정상 로그인 성공<br>폐기 세션·탈퇴 계정 재사용 차단 |
| 친구와 사진 | 두 계정 연결·발송·조회<br>다른 사용자 ID·친구 해제<br>사진 삭제 후 같은 URL 재요청 | 허용 대상만 접근<br>선택한 회수 정책과 일치 |
| 영속성과 삭제 | 재시작·재배포<br>파일 삭제 실패·DB 실패<br>DB와 Blob 별도 환경 복원 | 사진 유지·고아 객체 처리<br>삭제 데이터 재제공 차단 |
| APNs와 위젯 | 실제 아이폰 2대<br>전경·배경·잠금·강제종료<br>저전력·알림 거부·오프라인 복구 | 서버 전송과 기기 표시를 구분 기록<br>iOS 지연을 포함해 기대 동작 확인 |
| 실시간 | WS 연결·재연결·토큰 만료<br>DB 점검·서버 재시작 | 만료·폐기 연결 종료<br>재접속 뒤 REST 동기화 |
| 부하와 제한 | 큰 사진 동시 업로드<br>로그인·가입 폭주·기기 등록<br>DB pool·연결 상한 | OOM 없이 제한 응답<br>5xx·지연·메모리 기준 통과 |
| 배포와 복구 | 동일 커밋 backend·mobile CI<br>이전 JAR 복구·migration 호환<br>기존 21개 앱 영향 | 실행 기록 확보<br>공유 앱·DB 기능 정상 |
| 운영과 비용 | 로그 마스킹·경보 전달<br>백업 복원·예산 알림<br>배포 후 1일·1주 비용 확인 | 개인정보 최소 수집<br>실제 복구·경보·비용 검증 |

### 위젯 품질은 서버 성공과 다르다

코드가 15분 뒤 위젯 재조회 요청을 예약해도 실제 실행 시점은 iOS가 결정합니다. 최대 15분 안에 항상 갱신된다고 보장할 수 없습니다. 사진 생성 → APNs 전송 결과 → 확장 수신 → 위젯 표시를 따로 측정해야 원인을 찾을 수 있습니다.

### 남은 미확인

실서버 기동, 서버 96개 테스트의 실제 통과, 신규 JVM 최대 RSS, 기존 플랜 7일 피크, DB 활성 연결·권한·실데이터, APNs 유효 키·실기기 결과, 최신 총 청구서와 무료 혜택 잔여·만료는 아직 확인하지 않았습니다.

근거  [실기기 점검 문서](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/docs/widget-check.md)  ·  [위젯 타임라인](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/mobile/targets/widget/MomentWidget.swift#L57-L65)  ·  [통합 테스트 기반](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/test/java/com/junseo/support/IntegrationTest.java)

## 12 설정 목록과 실행 승인 범위

| 위치 | 설정 | 주의점 |
| --- | --- | --- |
| 서버 | JUNSEO_DB_URL<br>JUNSEO_DB_USER<br>JUNSEO_DB_PASSWORD | 운영 DB TLS · 전용 역할<br>테스트 DB와 분리 |
| 서버 | JUNSEO_JWT_SECRET<br>JUNSEO_MEDIA_SECRET | 서로 다른 키 · 개발 기본값 금지<br>누락 시 운영 시작 실패 |
| 서버 | JUNSEO_STORAGE_DIR<br>향후 Blob 연결 설정 | 현재는 로컬 저장 경로<br>Blob용 코드·구성 추가 필요 |
| 서버 | JUNSEO_ADMIN_TOKEN<br>JUNSEO_CORS_EXTRA_ORIGINS | 관리자 기능 필요 시만 활성화<br>웹 origin은 정확한 HTTPS 주소 |
| 서버 | JUNSEO_APNS_ENABLED<br>JUNSEO_APNS_KEY_ID<br>JUNSEO_APNS_TEAM_ID<br>JUNSEO_APNS_BUNDLE_ID<br>JUNSEO_APNS_KEY_PATH | 현재 .p8 파일 경로를 실제 읽음<br>Key Vault 저장만으로 호환되지 않음 |
| 앱 빌드 | EXPO_PUBLIC_API_URL | 공개 HTTPS API URL<br>앱에 보이므로 비밀 입력 금지 |
| 앱 빌드 | IOS_BUNDLE_ID · APPLE_TEAM_ID<br>APNS_ENV | 앱·위젯·확장 서명 일치<br>sandbox와 production 구분 |
| 앱 빌드 | ADMOB_IOS_APP_ID<br>ADMOB_IOS_REWARDED_ID | 현재 미설정이면 테스트 광고<br>실광고·동의·연령 정책 별도 검증 |

### 이번 보고서 이후 실행 전에 필요한 승인

1. 비용과 배치: A·B·C 중 선택, B1 검증 실패 시 B2 승격 허용 여부, 월 예산 한도

2. 네트워크: 기존 DB 방화벽을 좁힐 대상 규칙·접속 경로·영향받는 앱과 복구 절차

3. 접근권한: 새 Managed Identity·RBAC·GitHub OIDC·자격증명 생성과 지속 접근 범위

4. 비밀 입력: DB·서명·APNs 관련 비밀은 안전한 입력 경로에서 처리하고 채팅·Git·앱 번들에 남기지 않기

5. 공개 범위: TestFlight 초대 대상, 실제 데이터 사용 시작 시점, 사진·계정 삭제·보관 정책

보안 민감 설정과 지속 접근을 새로 만들거나 확대하는 변경은 실제 작업 시 별도 확인이 필요합니다. 기존 앱의 자원·권한·방화벽을 일괄 수정하거나 서비스 배포를 이 보고서만으로 실행하지 않습니다.

### 권고하는 다음 한 단계

전용 B1 + 기존 DB 분리 사용을 첫 후보로 정한 뒤, 기존 DB 연결·방화벽 영향 분석과 코드 P0 수정 목록을 먼저 확정하십시오. 기존 DB를 안전하게 공유할 수 없다면 전용 DB안으로 바꾸고, 10명 베타의 위 검증 기준을 통과한 뒤 확대하는 것이 가장 합리적입니다.

근거  [서버 환경설정](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/backend/src/main/resources/application.yml)  ·  [앱 설정](https://github.com/obst2580/Junseo/blob/4f471646b8a0db74c7a2fdce2509e689ab346b9f/mobile/app.config.js)  ·  [Apple 가입비](https://developer.apple.com/programs/enroll/)  ·  [GitHub Actions 배포](https://learn.microsoft.com/en-us/azure/app-service/deploy-github-actions)

## 13 다음 세션이 바로 시작할 작업

### 먼저 사실관계를 다시 고정

1. 저장소 https://github.com/obst2580/Junseo 의 claude/eager-gauss-5935za 브랜치를 확인합니다. 기준 SHA는 4f471646b8a0db74c7a2fdce2509e689ab346b9f 입니다. HEAD가 달라졌으면 변경 파일과 위험부터 재평가합니다.

2. 코드 수정 전 사용자가 원하는 작업 환경을 확인합니다. Mac 팀을 쓰려면 연결 상태와 /Users/obst/Documents/Codex/2026-10-02/task-2/app-team 의 실제 구성·역할·변경사항을 읽습니다. 기존 샘플 성공은 Junseo 실행 근거가 아닙니다.

3. Java 21, 프로젝트 Gradle wrapper, Node·패키지 관리자, Xcode·서명·Apple 계정, 실기기 2대, 격리 PostgreSQL 테스트 환경의 준비 여부를 확인합니다. 운영 DB를 테스트에 사용하지 않습니다.

### 그 다음 작성할 구체적 산출물

- 변경 파일별 P0 수정 계획: 운영 기본키 제거·prod 검증·HTTPS API 검증·Blob 저장·민감 로그 제거

- 인증·사진 회수 정책 결정안과 회귀 테스트: 로그아웃 토큰, 친구 해제 URL, 사진 삭제 실패, 위젯 만료·재로그인

- 기존 Azure 공유 안전성 확인표: 앱별 송신 IP, 7일 피크, DB 실제 연결·pool·역할, 방화벽 변경과 rollback

- 선택한 비용안의 리소스 명세와 배포 미리보기: 공유 리소스의 삭제·교체 금지, 새 권한·비밀 처리 범위 표시

- 동일 커밋 CI 실행 결과와 두 실기기 인수 기록. 실행하지 않은 검증은 성공으로 쓰지 않기

### 다음 세션에 붙여넣을 요청문

아들이 만든 Junseo 앱을 내 기존 Azure에서 서비스하려고 한다. 첨부한 Junseo Azure 서비스화 현황과 인계 문서를 먼저 읽고 이어서 도와줘. 저장소는 https://github.com/obst2580/Junseo/tree/claude/eager-gauss-5935za 이고 분석 기준 커밋은 4f471646b8a0db74c7a2fdce2509e689ab346b9f 이다. 코드·보안·Azure 현황과 비용 분석은 했지만 Junseo 코드 수정·실행·배포·리소스 변경은 아직 하지 않았다.

내 Mac의 Claude Code·Codex 팀 관련 폴더는 /Users/obst/Documents/Codex/2026-10-02/task-2/app-team 이다. 이 팀은 샘플 Python·Node만 검증했고 Junseo는 아직 할당하지 않았다. Java·iOS·외부 DB 준비를 따로 확인해야 한다. 별도 대시보드 작업과 Junseo 배포 완료를 혼동하지 말아줘.

먼저 현재 HEAD 차이와 실제 개발 환경, 기존 Azure 자원 상태를 확인하고, 내가 분석·코드 수정·테스트·배포 중 어디까지 진행할지 선택할 수 있게 다음 작업을 구체적으로 정리해줘. 우선안은 전용 Linux App Service B1 1개 + 기존 PostgreSQL의 독립 DB·권한 + private Blob + Key Vault지만 이미지 부하와 DB 공유 안전성을 통과해야 한다. 비용·리소스 생성, 방화벽·권한·지속 접근·비밀 변경과 실제 배포는 내 확인을 받은 뒤 진행해줘. 비밀 원문은 문서나 채팅에 쓰지 말고 테스트는 운영 DB와 분리해줘.

인계 시점 이후 Azure 상태·가격·브랜치가 달라질 수 있습니다. 이 문서의 수치를 현재 상태로 자동 채택하지 말고 바뀐 부분을 확인한 뒤 진행하십시오.
