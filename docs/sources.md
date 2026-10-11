# 데이터 소스 조사 메모

> 조사일 2026-10-11. 이 개발 환경은 외부 접속이 막혀 있어 **주소를 직접 열어 확인하지 못했다.**
> 아래 '상태' 칸은 검색 결과로 확인한 정도를 적었다. 첫 `topicradar collect` 실행 결과 표의 실패 줄로 최종 확인할 것
> (수집기·피드 하나가 실패해도 나머지는 계속 돈다).

## 1. 결정 사항 요약

| 질문 | 결정 | 이유 |
|---|---|---|
| 아침 "오늘 예정"의 1차 소스 | 기사 속 일정 문장 추출 + 수동 YAML(`data/schedule/날짜.yaml`) | 정부 발표·재판 일정은 공식 API가 사실상 없음. 국회 일정 API는 2차 |
| 미국 증시·환율 | 별도 수치 피드 없이 해외·경제 RSS 기사에 나온 숫자만 인용 | 정치 중심 채널, 절대 규칙 2(지어낸 수치 금지)에 가장 안전 |
| Google Trends | 쓰지 않음, 네이버 데이터랩만(2차) | 공식 API 없음(알파), pytrends는 비공식·차단 잦음. 시청층은 국내 검색 |
| 이미지 1순위 | 네이버 이미지. Google CSE는 기존 키가 있을 때만 앞에 | Custom Search JSON API 신규 가입 차단, 2027-01-01 종료 예정 |
| Claude 모델 | 분석·대본 모두 `claude-opus-5-5`(.env로 교체) | 기본값. 비용을 줄이려면 분석만 `claude-sonnet-5-5`로 |
| 주제 선정 기준 | 진보 성향 커뮤니티 정치 게시판의 반응(글 수·댓글·상승률). 사실은 뉴스 기사에서만 | 시청층이 실제로 반응하는 화제를 고르되, 커뮤니티 글은 검증되지 않은 주장이 섞여 근거로 쓰지 않음(§12) |

## 2. 국내 뉴스 RSS (키 불필요)

| 소스 | 주소 | 상태 | 비고 |
|---|---|---|---|
| 연합뉴스 정치/북한/경제/사회/국제 | `https://www.yna.co.kr/rss/{politics,northkorea,economy,society,international}.xml` | 미확인(널리 쓰이는 형식) | yna.co.kr RSS 안내 페이지에서 섹션 목록 확인 |
| 정책브리핑(korea.kr) RSS | — | **제공 중단** | 정책브리핑 공지 "RSS 서비스 제공 중단 안내"(2026-07-01) |
| 정책브리핑 대체 | 공공데이터포털 문화체육관광부 한국정책방송원 게시글 정보 조회 서비스 | 데이터셋 확인, 엔드포인트 미확인 | [data.go.kr/data/15136860](https://www.data.go.kr/data/15136860/openapi.do), REST·JSON, 키 `DATA_GO_KR_KEY` (2차) |
| 부처별 보도자료 RSS | 각 부처 누리집 | 미확인 | 있는 부처만 `kind: press`로 channel.yaml에 추가 |

## 3. 해외 뉴스 RSS (간밤 해외 주요 뉴스)

| 소스 | 주소 | 비고 |
|---|---|---|
| 연합뉴스 국제 | `https://www.yna.co.kr/rss/international.xml` | 한국어 요약이라 오프라인 모드에서도 대본에 쓸 수 있음 |
| BBC World | `https://feeds.bbci.co.uk/news/world/rss.xml` | 영문 — 오프라인 모드는 번역 불가로 편성에서 제외, Claude 경로에선 포함 |
| NYT World | `https://rss.nytimes.com/services/xml/rss/nyt/World.xml` | 영문 |
| Guardian World | `https://www.theguardian.com/world/rss` | 영문 |
| Al Jazeera | `https://www.aljazeera.com/xml/rss/all.xml` | 영문 |
| NPR World | `https://feeds.npr.org/1004/rss.xml` | 영문 |
| Reuters / AP | — | 공식 공개 RSS 없음(Reuters는 2020년 중단) |

## 4. 증시·환율 (2차 후보, 현재는 쓰지 않음)

`topicradar/collectors/overseas.py`의 `MarketProvider` 인터페이스로 붙인다.

| 후보 | 키 | 장단점 |
|---|---|---|
| FRED API (SP500, NASDAQCOM, DJIA, DEXKOUS) | 무료 키 | 공식·안정, 하루 지연 가능 |
| 한국수출입은행 환율 API | 무료 키 | 원화 환율 공식, 영업일 고시 시각 이후 |
| 한국은행 ECOS | 무료 키 | 통계 코드 체계가 복잡 |
| Stooq CSV | 불필요 | 전일 종가 즉시, 비공식이라 차단·형식 변경 위험 |

## 5. 국회 일정 (2차)

| 소스 | 형식 | 비고 |
|---|---|---|
| 공공데이터포털 국회사무처 본회의 일정 | XML, 무료 | [data.go.kr/data/15126001](https://data.go.kr/data/15126001/openapi.do). 같은 기관에 의사일정공지·본회의 처리안건·전원위원회 일정 API |
| 열린국회정보 OpenAPI | JSON/XML | open.assembly.go.kr 인증키(`ASSEMBLY_API_KEY`). 서비스 코드는 포털 Open API 목록에서 확인 후 고정 |

국회 일정만 커버된다. 정부 발표·재판 일정은 공식 일정 API를 찾지 못했다 → 수동 YAML + 기사 추출 유지.

## 6. 네이버

| API | 내용 |
|---|---|
| 뉴스 검색 `GET /v1/search/news.json` | `query, display(≤100), start, sort=date`. 응답 `title, originallink, link, description, pubDate` |
| 이미지 검색 `GET /v1/search/image` | `query, display, sort, filter=large`. 응답 `title, link, thumbnail, sizewidth, sizeheight` — **게시 페이지 URL이 없다** → 출처는 이미지 호스트 도메인으로 기록 |
| 데이터랩 검색어 트렌드 `POST /v1/datalab/search` (2차) | `startDate, endDate, timeUnit, keywordGroups(≤5)`. 응답 ratio는 구간 최대=100 상대값 → 상승률 계산용 |

- 인증 헤더 `X-Naver-Client-Id`, `X-Naver-Client-Secret`.
- **키 발급처 변경**: 신규 키는 네이버 클라우드 콘솔의 NAVER API HUB에서 발급된다([NAVER API HUB 개요](https://api.ncloud-docs.com/docs/naver-api-hub-overview.md)). 호스트·헤더 이름이 다르면 `.env`의 `NAVER_API_BASE`, `NAVER_HEADER_ID`, `NAVER_HEADER_SECRET`으로 바꾼다.
- 일일 한도는 발급처 대시보드에서 확인(검색 API는 통상 하루 25,000회로 알려져 있으나 이미지 검색 한도는 확인 못 함).

## 7. Google

### Custom Search JSON API (이미지 검색)
- **신규 고객 가입 차단, 2027-01-01 서비스 종료 예정**(기존 고객만 그때까지 사용). 이후 엔드포인트는 오류를 돌려준다.
  ([Brave 정리](https://brave.com/learn/google-api-shutdown/), [Search Engine Journal](https://www.searchenginejournal.com/google-documents-partner-only-api-for-full-web-search-results/), [dev.to](https://dev.to/markhuang-ai/googles-custom-search-api-dies-in-2027-a-drop-in-isnt-a-migration-9b7))
- 2026-01-20 이후 만든 Programmable Search Engine은 '사이트 지정(최대 50개)'만 가능.
- 요금: **무료 하루 100회, 초과 1,000회당 $5, 하루 최대 10,000회.**
- 파라미터: `searchType=image, imgSize=xlarge, imgType=photo, num(≤10), gl=kr, lr=lang_ko`. `rights`는 쓰지 않는다(채널 결정).
- 구현: 키가 없거나 종료일 이후면 자동으로 건너뛰고 네이버가 1순위가 된다. 계속 Google 결과가 필요하면 유료 SERP API 어댑터를 `sources/`에 추가.

### Google Trends — 쓰지 않음
공식 API는 제한적 알파, pytrends는 비공식 스크래퍼로 429 차단이 잦다.

## 8. YouTube Data API v3

| 호출 | 비용(units) | 용도 |
|---|---|---|
| `playlistItems.list` (업로드 재생목록 `UU…`) | 1 | 경쟁 채널 최근 업로드 |
| `videos.list` (statistics / contentDetails) | 1 | 조회수, 영상 길이 |
| `search.list` | **100** | newsclip 영상 검색 |

하루 10,000 units 기준: 경쟁 채널 10개 × 하루 3회 수집 ≈ 60 units, 나머지로 영상 검색 약 90회.
저녁 영상(10~12분) 장면이 100개 안팎이라 **검색어 7일 캐시 + 동시 동일 요청 합류 + 일일 예산(`budget.youtube_search_per_day`)** 으로 보호한다.
영상 장면은 같은 주제 안에서 키워드가 겹쳐 캐시 적중이 많다.

## 9. Wikimedia Commons

`action=query&generator=search&gsrnamespace=6&prop=imageinfo&iiprop=url|size|mime|extmetadata&iiurlwidth=1920`.
`extmetadata.Artist`, `LicenseShortName`으로 작가·라이선스를 표기한다. User-Agent에 연락처가 필요하다(`STUDIO_CONTACT`).

## 10. 공공누리·KTV·국회방송 (2차)

- 공공누리 제1유형은 출처 표시 조건으로 상업적 이용·변경 허용(2유형: 상업 금지, 3유형: 변경 금지, 4유형: 둘 다 금지).
- KTV 국민방송·국회방송 영상의 유형과 API 제공 여부는 확인하지 못했다 → newsclip은 현재 이 채널들을 유튜브 `preferred_channels` 가점으로 우대한다.

## 11. 저작권 메모 (법률 자문 아님)

- 출처 표기는 **이용 허락이 아니다.** 저작권법 제28조(공표된 저작물의 인용: 보도·비평 등을 위한 정당한 범위, 공정한 관행)와
  제35조의5(공정한 이용)가 근거가 될 수 있지만 사용 목적·분량·대체 가능성에 따라 판단이 갈린다.
- 방송사 뉴스 영상은 Content ID에 등록돼 있어 수익 차단·저작권 경고 위험이 크다 → `videos.blocked_channels`로 기본 제외.
- newsclip은 모든 자료의 출처 URL·도메인·채널을 `assets.json`에 남기고 화면에 출처 자막을 넣는다. 업로드 전 `review`에서 사진·영상을 사람이 고른다.

## 12. 커뮤니티 정치 게시판 (주제 선정용)

> 2026-10-11 조사. 이 환경에선 사이트 DNS가 막혀 목록 페이지를 직접 열지 못했다. 아래 주소는 검색 결과로 본 글 주소 형식에서 추정했다.
> **처음 운영 전에 `topicradar probe`로 확인**하고 `channel.yaml`의 `list_url`·`post_pattern`을 맞출 것.

| 사이트 | 게시판 | 설정한 목록 주소 | 글 주소 형식 | 상태 |
|---|---|---|---|---|
| 다모앙 | 자유게시판(별도 정치 게시판 확인 못 함) | `damoang.net/free?page={page}` | `damoang.net/free/숫자` — 검색 결과의 글 주소로 확인 | 형식 확인, 정치 필터 사용 |
| 보배드림 | 정치게시판 | `bobaedream.co.kr/list?code=politic&page={page}` | `view?code=politic&No=숫자` | 메뉴에 독립 정치게시판 있음(검색 결과). 댓글 수 표기(`totreply`)는 미확인 |
| 뽐뿌 | 정치 게시판 | `ppomppu.co.kr/zboard/zboard.php?id=politics&page={page}` | `view.php?id=게시판&no=숫자`(zboard 형식은 확인) | **게시판 id 미확인**. 한 커뮤니티 댓글(2022)은 "보수·진보 게시판으로 분리"했다고 주장 — 미검증 |
| 더쿠 | HOT(정치 카테고리) | `theqoo.net/hot?page={page}` | `theqoo.net/hot/숫자` | 2025-07 공지로 스퀘어에 '정치' 카테고리·정치 제외 기능 추가, HOT에도 적용. 카테고리 주소 미확인 → HOT 전체 + 정치 필터 |
| 클리앙 | 모두의공원 | `clien.net/service/board/park?po={page0}` | `/service/board/park/숫자` — 검색 결과로 확인 | 페이지 파라미터(`po`, 0부터)는 미확인 |
| 오늘의유머 | 시사게시판 | `todayhumor.co.kr/board/list.php?table=sisa&page={page}` | `view.php?table=sisa&no=숫자` | 미확인 → `enabled: false` |

참고한 검색 결과: [더쿠 스퀘어 정치 카테고리 공지](https://theqoo.net/square/3836786021), [더쿠 HOT 정치 카테고리 글 예](https://theqoo.net/hot/4154411011),
[더쿠 이용 규칙](https://theqoo.net/notice/1383792790), [보배드림 메뉴](https://m.bobaedream.co.kr/board/new_writing/bb_talk/186),
[클리앙 모두의공원 글 예](https://www.clien.net/service/board/park/19248246), [다모앙 자유게시판 글 예](https://damoang.net/free/4123081).

**성향은 고정돼 있지 않다.** 나무위키 '더쿠' 항목은 2026년 HOT 등에서 반민주당 정서가 강하다고 적고 있다(제3자 요약, 미검증).
사이트별 반응이 채널 시청층과 어긋나면 그 사이트를 `enabled: false`로 끄거나 `pages`를 줄인다. 리포트 `커뮤니티 반응` 열이 사이트별로 나뉘어 있어 비교할 수 있다.

**수집 원칙(이용약관은 운영자가 직접 확인 — 법률 자문 아님)**
- robots.txt가 막으면 그 게시판은 건너뛴다(수집 표에 표시). 사이트당 목록 1쪽, 요청 사이 1초, 하루 3회.
- 저장하는 것: 제목, 글 주소, 댓글 수, 작성 시각. **본문·작성자 닉네임은 저장하지 않는다.**
- 커뮤니티 제목·내용은 대본·화면·출처 자막에 쓰지 않는다. 사실 문장은 같은 주제의 뉴스 기사에서만 가져온다(절대 규칙 2).
- 커뮤니티에만 있고 기사가 없는 화제는 편성하지 않는다(리포트 메모에만 남김).
