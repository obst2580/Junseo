# studio — 진보 성향 데일리 정치 해설 채널 제작 도구

"뉴스는 봤는데 뭔지 모르겠다면" — 아침에 예고하고 저녁에 결과를 설명하는 채널의 **주제 선정·대본 초안(topicradar)**과
**자료 수집·프리미어 XML(newsclip)**을 한 저장소에서 돌린다. 내레이션은 사람이 녹음하고, 도구는 대본과 자료까지만 만든다.

```
studio/
  pyproject.toml        uv 워크스페이스(core · topicradar · newsclip)
  channel.yaml          채널 정체성·피드·선호/차단 출처·점수 가중치·API 예산
  .env.example          키 목록
  core/                 studio-core: 설정, Claude 래퍼(토큰 로그), HTTP 재시도, 텍스트 유틸, 시연용 fixture 전송계층
  topicradar/           collect / slot / pick / made / history / daily
  newsclip/             run / review / xml / search
  data/                 studio.db, logs/llm_usage.jsonl, schedule/날짜.yaml, 검색 캐시·API 사용량
  projects/날짜_슬롯/    report.md script.txt narration.wav* narration.srt* scenes.json assets/ assets.json timeline.xml
  docs/sources.md       데이터 소스 조사 메모   docs/demo/ 시연 결과
```
\* 사용자가 만든다. `core/`는 두 패키지가 같이 쓰는 설정·LLM·HTTP 코드를 한 곳에 두려고 추가한 공용 패키지다.

## 설치

```bash
# Python 3.11+, uv, ffmpeg(yt-dlp 구간 자르기용)
uv sync                    # 의존성 설치(워크스페이스 전체)
cp .env.example .env       # 키 입력
uv run topicradar --help
uv run newsclip --help
uv run pytest --cov        # 테스트(시연 fixture 포함, 네트워크 불필요)
```
`--clip`(이미지-키워드 유사도 가산)을 쓰려면 `uv sync --extra clip`(torch 설치, 용량 큼).

## .env 키와 발급처

| 변수 | 용도 | 발급처 |
|---|---|---|
| `ANTHROPIC_API_KEY` | 군집·판정·편성 문장·대본·장면 분할 | Claude Console(console.anthropic.com) → API Keys |
| `STUDIO_MODEL_ANALYSIS` / `STUDIO_MODEL_SCRIPT` | 역할별 모델(기본 `claude-opus-5-5`) | 비용을 줄이려면 분석만 `claude-sonnet-5-5` |
| `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` | 뉴스 검색, 이미지 검색(1순위), 데이터랩(2차) | 네이버 개발자센터 → 애플리케이션 등록(검색·데이터랩). **신규 키는 네이버 클라우드 콘솔의 NAVER API HUB**에서 발급 — 호스트·헤더가 다르면 `NAVER_API_BASE`·`NAVER_HEADER_*` |
| `YOUTUBE_API_KEY` | 경쟁 채널·내 채널 통계, 영상 검색 | Google Cloud Console → YouTube Data API v3 사용 설정 → 사용자 인증 정보 → API 키 |
| `GOOGLE_API_KEY` / `GOOGLE_CSE_ID` | Google 이미지 검색(키가 있을 때만 1순위) | 아래 "Google Custom Search" 참고 |
| `DATA_GO_KR_KEY` | 공공데이터포털(2차: 국회 일정, 정책방송원) | data.go.kr 회원가입 → 활용신청 → 일반 인증키 |
| `ASSEMBLY_API_KEY` | 열린국회정보(2차) | open.assembly.go.kr → 인증키 신청 |
| `STUDIO_CONTACT` | Wikimedia User-Agent 정책용 연락처 | — |
| `STUDIO_FONT` | 출처 자막 PNG 한글 폰트(자동 탐색 실패 시) | — |

키가 없는 수집기·소스는 **경고하고 건너뛴다**(전체는 계속). `ANTHROPIC_API_KEY`가 없으면 결정적 휴리스틱(오프라인 모드)으로 돈다.

### Google Custom Search — 무료 한도와 요금, 그리고 종료 예정
- **신규 고객 가입이 막혔고 2027-01-01 종료 예정**이다(기존 고객만 그때까지 사용). 그래서 기본 1순위는 네이버 이미지이고, Google은 기존 키가 있을 때만 앞에 끼운다. 종료일 이후엔 자동으로 빠진다.
- 무료 **하루 100회**, 초과 **1,000회당 $5**, 하루 최대 10,000회. `channel.yaml budget.google_cse_per_day`(기본 90)를 넘으면 그날은 건너뛴다.
- 기존 키가 있다면: Programmable Search Engine에서 검색엔진을 만들고 이미지 검색을 켠 뒤 검색엔진 ID(cx)를, Google Cloud Console에서 Custom Search API를 켜고 API 키를 받는다.
  2026-01-20 이후 만든 검색엔진은 '사이트 지정(최대 50개)'만 된다.

## 하루 운영 순서

1. `topicradar daily` — cron이 시각에 맞춰 수집·편성(05:30 아침 / 12:00 수집만 / 17:00 저녁 / 토 17:00 저녁+주간)
2. `projects/날짜_슬롯/report.md`를 보고 필요하면 `topicradar pick --project … --replace <순서> <topic_id>`
3. `script.txt` 수정 — `#TODO`·`#확인` 처리, `#출처:` 줄은 녹음 때 읽지 않는다(리포트 하단 '대본 점검' 참고)
4. 녹음 → `narration.wav`
5. 프리미어 Pro: 전사(Speech to Text) → 캡션 → **SRT 내보내기** → `narration.srt`
6. `newsclip run --project projects/날짜_슬롯`
7. `newsclip review --project …` (http://localhost:8765) — 후보 교체·건너뛰기·영상 시작 초 조정 → 저장 시 XML 재생성
8. 프리미어 **파일 → 가져오기 → `timeline.xml`** (V1 화면, V2 출처 자막, A1 내레이션)
9. 업로드
10. `topicradar made --project … --video-id <유튜브ID> [--title "실제 제목"]` — covered_in·past_titles·성과 추적
11. 주 1회 `topicradar history --weeks 4` — 가중치 조정 제안 확인 후 `channel.yaml scoring.weights` 수정

### cron 예시 (KST)

```cron
CRON_TZ=Asia/Seoul
30 5  * * *  cd /path/to/studio && uv run topicradar daily --notify >> data/logs/daily.log 2>&1
0  12 * * *  cd /path/to/studio && uv run topicradar daily          >> data/logs/daily.log 2>&1
0  17 * * *  cd /path/to/studio && uv run topicradar daily --notify >> data/logs/daily.log 2>&1
```
`daily`가 현재 시각을 보고 할 일을 정한다(±30분 여유). 토요일 17시엔 저녁 + 다음 날(일) 주간까지 만든다.
macOS는 launchd, 서버는 systemd timer로 같은 세 시각을 걸어도 된다.

## 명령

### topicradar
| 명령 | 설명 |
|---|---|
| `collect [--since ISO]` | RSS(국내·보도자료) · 해외 RSS · 네이버 뉴스 · 유튜브 경쟁 채널 · 수동 일정 수집. 기본 since = 마지막 수집 1시간 전 |
| `slot morning\|evening\|weekly [--date] [--notify]` | 군집 → 점수 → 슬롯 규칙 → 문장 요소 → 대본 → `report.md`·`script.txt`·`plan.json` |
| `pick --project <폴더> --replace <order> <topic_id>` | 항목 교체(역할 유지) 후 대본 재생성 |
| `made --project <폴더> --video-id <ID> [--title]` | 업로드 기록, covered_in 갱신, past_titles 추가(주석 보존) |
| `history [--weeks 4]` | 예측 총점 vs 조회수 상관, 제목 패턴·카테고리별 성과, 주차별 포인트 적중률, 가중치 ±5 제안 |
| `daily [--at ISO] [--notify]` | 시각에 따라 collect → slot |

### newsclip
| 명령 | 설명 |
|---|---|
| `run --project <폴더> [--per-scene 3] [--video-max-sec 20] [--clip]` | SRT·대본 정렬 → 장면 분할 → 소스 검색 → 순위 → 다운로드 → `scenes.json`·`assets.json`·`timeline.xml` |
| `review --project <폴더>` | 검토 UI(localhost:8765) |
| `xml --project <폴더> [--fps 30] [--size 1920x1080] [--zoom 1.04] [--audio narration.wav]` | XML 재생성 + 재파싱 검사 |
| `search "<검색어>" --kind photo\|video\|broll` | 소스별 상위 5개 표(디버그) |

## 설계 요점

**점수(0~100)** = 관심도 40 + 공백도 20 + 지속성 10 + 적합도 30, 세부마다 근거 한 문장을 리포트에 싣는다.
- 관심도: 0.4 × 기사 수 점수(로그, 25건 포화) + 0.6 × 상승률 점수(직전 동일 길이 윈도우 대비, −100%~+300%). 추세 ↑→↓도 여기서.
- 공백도: 경쟁 채널이 최근 24시간에 다룬 편수 c ≥ 1이면 60−20(c−1). 아무도 안 다뤘으면 관심도 ≥ 40일 때 100, 아니면 30+관심도.
- 지속성·적합도: Claude가 한 번의 배치 호출로 판정(오프라인은 어휘 휴리스틱). 적합도에는 코드 보정 — 최근 7일 내 다룬 주제는 새 전개(이후 기사 2건 이상 또는 오늘 일정)가 없으면 −25, 있으면 시리즈 연속 +10.
- 카테고리 균형: 같은 슬롯에서 경제가 절반을 넘으면 최저점 경제 항목을 비경제 후보로 교체(없으면 제외). 본문(헤드라인·서브) 기준과 슬롯 전체 기준을 모두 적용. 주간 사슬도 경제 주제는 정치 사슬이 없을 때만 축이 된다.
- 수익화 안전도는 점수에 넣지 않고 `risk_flags`로만 표시(노란딱지 표현, 수사·재판 중 사안, 여론조사 공표 요건).

**아침 → 저녁 회수**: 아침 `오늘의포인트`마다 `expected`(예상 결과)와 `impact`(어떻게 되면 뭐가 달라지는지)를 남기고,
저녁에 같은 주제의 결과 기사를 찾아 `actual`과 `hit/miss/pending`을 기록한다. 대본은 hit이면 "아침에 말씀드린 대로", miss면
"아침 예상과 달리 … 이유"로 시작하고 각각 "그래서 다음은"(결과 이후 기사에서만)을 붙인다. 적중률은 `history`가 주차별로 집계.

**중복 방지(절대 규칙 1)**: 전날 저녁에 다룬 주제는 아침에 다시 정리하지 않는다(오늘 후속 일정이 있으면 포인트로만).
아침에 다룬 비포인트 주제는 저녁에 새 신호가 2건 이상일 때만 다시 다룬다.

**대본 점검(절대 규칙 2)**: 생성 경로와 상관없이 `validate_script`가 글자 수, 25자 초과 줄, `## 헤더`, 수집 기사에 없는
수치·인용, `#출처:` 누락·수집 밖 URL, 금지 표현(충격·경악·난리…), 회수 문구를 검사해 리포트에 싣는다.

**Claude 호출**: 슬롯당 군집·판정·편성 문장·대본 4회 내외, newsclip 장면 1회. structured outputs로 JSON 스키마를 강제하고,
시스템 프롬프트(채널 정체성·규칙)는 고정해 프롬프트 캐시를 맞춘다. 안전 분류기 거절 시 서버 측 대체 모델(`fallbacks: "default"`)로 재시도한다(끄려면 `STUDIO_LLM_FALLBACKS=0`).
토큰 사용량은 호출마다 `data/logs/llm_usage.jsonl`에 남는다.

**newsclip 소스 우선순위**(Google 키가 없으면 네이버가 그 자리를 대신해 1순위):
| 장면 | 순서 |
|---|---|
| 사진(인물·기관·장소) | google_image → wikimedia → naver_image (Google 없으면 naver → wikimedia) |
| 사진(사건·현장)·broll | google_image → naver_image |
| 영상(발언·회의·현장) | youtube_video(방송사 채널 제외, KTV·국회방송·정당 채널 가점) → 정지 이미지 |

YouTube `search.list`는 1회 100 units라 검색어 7일 캐시, 동시 동일 요청 합류, 일일 예산(`budget.youtube_search_per_day`)으로 쿼터를 지킨다.
사진은 1920×1080으로 커버 크롭해 저장하므로 프리미어 Scale 100이 화면을 꽉 채우고, 줌(100→104, ease-out)은 선형 키프레임 5개로 근사한다(홀수 장면 줌인·짝수 줌아웃).

## 출처 표기와 저작권 (법률 자문 아님)

- 라이선스 필터는 걸지 않는다(채널 결정). 대신 모든 자료의 출처 URL·도메인·채널·작가를 `assets.json`에 남기고 **V2 트랙에 출처 자막**을 넣는다.
  표기: `출처: {도메인 또는 채널명}`, Wikimedia는 `사진: {작가} / Wikimedia Commons / {라이선스}`.
- **출처 표기는 이용 허락이 아니다.** 저작권법 제28조(공표된 저작물의 인용)·제35조의5(공정한 이용)의 범위인지는 용도·분량·대체 가능성에 따라 달라진다.
- 방송사 뉴스 채널은 Content ID 위험 때문에 기본 차단(`videos.blocked_channels`). 업로드 전 `review`에서 사람이 확인한다.
- 여론조사 인용 시 공직선거법 공표 요건(조사의뢰자·기관·기간·방법·표본·오차범위, 중앙선거여론조사심의위원회 참조 고지)을 리포트가 상기시킨다.

## 시연 (네트워크·키 없이)

```bash
uv run python scripts/demo.py
```
`fixtures/demo/stories.yaml`의 **가상 뉴스**(가상 정당 한빛당·새한당, 익명 인물, example.com URL — 실존 사건 아님)를
실제 RSS·네이버·유튜브 응답 형식으로 만들어(`fixtures/demo/build.py`) 다음을 차례로 실행한다:
collect → 10/7 아침·저녁 → 10/8 아침·저녁(+Slack) → 10/11 주간 → 각 `made` → `history` → 저녁 대본으로 샘플 SRT·무음 WAV 생성 → `newsclip run` → `newsclip xml --audio`.
결과는 `demo_root/`(전체)와 `docs/demo/`(리포트·대본·SRT·scenes/assets·timeline.xml·실행 로그)에 남는다.

- `STUDIO_FIXTURES`: 모든 HTTP를 fixture로 대체(파서는 실제 응답 형식을 그대로 읽음), 이미지는 생성기가, 영상은 ffmpeg 테스트 패턴이 대신한다.
- `STUDIO_LLM=offline`: 이 환경엔 Claude 키가 없어 휴리스틱으로 돈다. 오프라인 대본은 **기사 문장을 사실→원인→다음 순서로 뽑아 존댓말·자막 단위로 바꾼 골격**이라
  분량이 목표보다 짧고 근거가 없으면 `#TODO`를 남긴다(지어내지 않음). 키를 넣고 같은 명령을 돌리면 Claude 경로로 간다.
- `STUDIO_NOW`: 단계마다 '현재 시각'을 고정해 실제 운영 순서를 재현.

## 2차 범위

열린국회정보/공공데이터포털 국회 일정 수집기(`AssemblyScheduleCollector`), 네이버 데이터랩 검색 추이(`NaverDataLabTrends`),
증시·환율 `MarketProvider`, KTV·국회방송 공공누리 영상 소스(`kogl`), 정책브리핑 대체(한국정책방송원 API). 인터페이스와 조사 메모는 들어 있다.
