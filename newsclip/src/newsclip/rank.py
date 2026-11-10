"""후보 정렬.

1. 소스 우선순위 점수(장면 성격별 경로에서 앞일수록 높음)
2. preferred_domains / preferred_channels 가점, blocked 제외
3. 해상도 1280 이상, 가로형 가점(세로형·아주 작은 사진 감점) — 관련성을 뒤집지 않게 작게
4. 제목·설명·출처 페이지에 장면 키워드가 들어간 수. 작업자가 쓴 장면 검색어가 있으면 그 말이 사진 제목에
   들어간 수를 크게 보고, 하나도 없으면 감점(대사와 상관없는 사진이 크기만으로 뽑히지 않게)
4-1. 뉴스 사진(기사 사진 서버) 가점, 문서·표·판결문 캡처 감점, 최근 1년 안 사진 가점(주소의 날짜)
5. 직전 장면과 같은 도메인·채널이면 감점(다양성), 같은 장면 안 중복 도메인도 감점,
   앞 장면에서 이미 고른 자료는 크게 감점(같은 사진 되풀이 방지)
6. --clip 시 open_clip 이미지-텍스트 유사도 가산
"""

from __future__ import annotations

import re
from collections.abc import Callable

from newsclip.license_log import KO_SOURCES
from newsclip.models import Asset, Scene
from newsclip.scenes import PROPER_NOUNS
from newsclip.sources.youtube_video import is_blocked, normalize_channel
from studio_core.config import ChannelConfig, now_kst

ROUTES: dict[str, list[str]] = {
    "photo_proper": ["google_image", "wikimedia", "naver_image"],
    "photo_event": ["google_image", "naver_image"],
    "video": ["youtube_video", "google_image"],
    "broll": ["google_image", "naver_image"],
}
_DOMAIN_RE = re.compile(r"^[a-z0-9.-]+\.[a-z]{2,}$")


# Google CSE를 못 쓸 때(키 없음·종료) 경로: 네이버가 1순위(채널 결정).
ROUTES_NO_GOOGLE: dict[str, list[str]] = {
    "photo_proper": ["naver_image", "wikimedia"],
    "photo_event": ["naver_image"],
    "video": ["youtube_video", "naver_image"],
    "broll": ["naver_image"],
}


def google_available() -> bool:
    """Google CSE 사용 가능 여부(키 존재 + 종료일 이전)."""
    from newsclip.sources.base import SourceUnavailable
    from newsclip.sources.google_image import GoogleImageSource

    try:
        GoogleImageSource.check_ready(None)  # type: ignore[arg-type]
    except SourceUnavailable:
        return False
    return True


def route_for(scene: Scene, google: bool | None = None) -> list[str]:
    """장면 성격별 소스 우선순위. Google을 못 쓰면 네이버가 1순위로 올라온다."""
    routes = ROUTES if (google_available() if google is None else google) else ROUTES_NO_GOOGLE
    if scene.kind == "video":
        return routes["video"]
    if scene.kind == "photo":
        proper = any(k in PROPER_NOUNS for k in scene.keywords_ko)
        return routes["photo_proper" if proper else "photo_event"]
    return routes["broll"]


# 미리보기에 워터마크를 박는 유료 사진·그림 사이트(받으면 '크라우드픽' 같은 글자가 화면에 그대로 뜬다) — 채널 설정과 별개로 늘 뺀다
WATERMARK_DOMAINS = [
    "crowdpic.net",
    "gettyimages.com",
    "gettyimages.co.kr",
    "gettyimagesbank.com",
    "gettyimageskorea.com",
    "shutterstock.com",
    "istockphoto.com",
    "123rf.com",
    "dreamstime.com",
    "alamy.com",
    "depositphotos.com",
    "ftcdn.net",
    "stock.adobe.com",
    "imagetoday.co.kr",
    "utoimage.com",
    "clipartkorea.co.kr",
    "pixta.jp",
    "pngtree.com",
    "vectorstock.com",
    "freepik.com",
]


def _host(url: str) -> str:
    from urllib.parse import urlparse

    return (urlparse(url or "").hostname or "").lower()


def domain_match(domain: str, patterns: list[str]) -> bool:
    """도메인 접미사 일치(go.kr ⊃ moef.go.kr). 도메인 형식이 아닌 항목은 무시."""
    d = domain.lower()
    for p in patterns:
        p = p.lower().strip()
        if _DOMAIN_RE.match(p) and (d == p or d.endswith("." + p)):
            return True
    return False


def asset_key(a: Asset) -> str:
    """다양성 판정 키(영상은 채널, 이미지는 도메인)."""
    return f"ch:{normalize_channel(a.author_or_channel)}" if a.media == "video" else f"dom:{a.source_domain}"


def score_asset(
    a: Asset, scene: Scene, route: list[str], cfg: ChannelConfig, story: str = "", drop: tuple[str, ...] = ()
) -> tuple[float, list[str]] | None:
    """점수와 근거. 차단 대상이면 None. story는 대본 전체(장면 문장을 이은 것) — 사진 제목의 인물이 대본 어디에도
    없으면 깎는다. drop은 작업자가 사진을 보고 뺀 것(사진 주소 그대로, 또는 사진 제목 일부 4자 이상)."""
    notes: list[str] = []
    if drop and any(
        d in (a.url, a.download_url) or (len(d) >= 4 and d.lower() in a.title.lower()) for d in drop
    ):
        return None
    if a.media == "video":
        if is_blocked(a.author_or_channel, cfg.videos.blocked_channels):
            return None
    elif domain_match(a.source_domain, [*cfg.images.blocked_domains, *WATERMARK_DOMAINS]) or domain_match(
        _host(a.download_url or a.url), WATERMARK_DOMAINS
    ):
        return None
    s = 0.0
    if a.source in route:
        pr = 40.0 - 10.0 * route.index(a.source)
        s += pr
        notes.append(f"소스순위 +{pr:.0f}")
    if a.media == "video":
        pref = any(
            normalize_channel(p) in normalize_channel(a.author_or_channel)
            for p in cfg.videos.preferred_channels
            if p
        )
    else:
        pref = domain_match(a.source_domain, cfg.images.preferred_domains)
    if pref:
        s += 15
        notes.append("선호 출처 +15")
    if a.width and a.height:
        if a.width >= 1280:
            s += 5
            notes.append("1280+ +5")
        portrait = cfg.images.orientation == "portrait"  # 세로 쇼츠 채널은 세로 소재가 낫다
        if (a.height > a.width) if portrait else (a.width > a.height):
            s += 8
            notes.append(f"{'세로형' if portrait else '가로형'} +8")
        elif (a.width > a.height) if portrait else (a.height > a.width):
            s -= 8
            notes.append(f"{'가로형' if portrait else '세로형'} −8")
        if a.width < 480:
            s -= 10
            notes.append("작은 사진 −10")
    hay = f"{a.title} {a.source_page_url} {a.source_domain} {a.author_or_channel}".lower()
    hits = sum(1 for k in [*scene.keywords_ko, *scene.keywords_en] if k and k.lower() in hay)
    if hits:
        s += min(20, 5 * hits)
        notes.append(f"키워드 {hits}개 +{min(20, 5 * hits)}")
    stock_video = (
        a.media == "video" and a.source in cfg.images.sources
    )  # 소재 사이트 영상도 제목(설명)이 있다
    news = not cfg.images.sources  # 뉴스 사진을 찾는 채널(소재 사이트만 쓰는 널스윤서와 따로 간다)
    if a.media == "image" or stock_video:
        title = a.title.lower()
        # 공유마당은 한국어 제목이라 작업자가 적은 한국어 검색어와 견준다
        query = scene.query_ko if a.source in KO_SOURCES else scene.query
        if query:  # 작업자가 대사를 읽고 쓴 검색어 — 그 말이 사진 제목에 있어야 그 장면 사진
            # 뉴스 채널: SNS·페이스북 같은 말은 사진에 담기지 않는다 — 제목에 글자로만 맞으면 'SNS 중독 좌담회' 같은
            # 딴 행사 사진이 뽑힌다
            skip = MEDIA_WORDS if news else set()
            words = [w.lower() for w in query.split() if len(w) >= 2 and w.lower() not in skip]
            qh = sum(1 for w in words if w in title)
            if qh:
                s += min(30, 10 * qh)
                notes.append(f"검색어 {qh}개 제목 일치 +{min(30, 10 * qh)}")
                if not news and qh * 2 < len(
                    words
                ):  # 소재 사이트: 검색어 낱말이 절반도 안 맞으면 엉뚱한 장면
                    s -= 15
                    notes.append("검색어 절반도 안 맞음 −15")
            elif words:
                s -= 20
                notes.append("제목에 검색어 없음 −20")
            literal = [w for w in query.lower().split() if w in skip and w in title]
            if literal:  # 제목이 SNS를 다룬 행사·기사(그 사람이 SNS에 쓴 일과 상관없다)
                s -= 10
                notes.append(f"'{literal[0]}' 글자만 맞음 −10")
    if a.media == "image":
        title = a.title.lower()
        if a.source_domain in NEWS_PHOTO_HOSTS:
            s += 10
            notes.append("뉴스 사진 +10")
        if _DOC_RE.search(title) or domain_match(a.source_domain, DOC_HOSTS):
            s -= 25
            notes.append("문서·표 캡처 −25")
        elif news and _GRAPHIC_RE.search(
            title
        ):  # 녹취 공개·그래픽·방송 갈무리: 얼굴 합성·글자판이라 사진이 아니다
            s -= 25
            notes.append("그래픽·녹취·캡처 −25")
        if a.source in KO_SOURCES and _OLD_FOREIGN_RE.search(
            title
        ):  # 공유마당 태그 검색에 섞이는 옛날·해외 사진
            s -= 30
            notes.append("옛날·해외 사진 −30")
        if a.source in KO_SOURCES and _DRAWING_RE.search(a.title):
            s -= 25
            notes.append("그림(아이콘·일러스트) −25")
        year = photo_year(a)
        if year:
            now = now_kst().year
            if year >= now - 1:
                s += 8
                notes.append(f"{year}년 사진 +8")
            elif year <= now - 6:
                s -= 5
                notes.append(f"{year}년 사진 −5")
    if a.media == "image" and news:
        scene_hay = f"{scene.text} {scene.query} {scene.topic}"
        people = title_people(a.title)
        others = [
            p for p in people if p not in scene_hay and not any(a in scene_hay for a in _ALIASES.get(p, ()))
        ]
        if people and people[0] in others:  # 사진 주인공이 다른 사람 — 그 사람이 한 말·일처럼 보인다
            s -= 25
            notes.append(f"제목 주인공 {people[0]}이(가) 장면에 없음 −25")
        extra = [p for p in others if p != people[0]]
        if extra:  # 지지도 기사 합성 사진처럼 장면과 상관없는 얼굴이 섞인 사진
            s -= 8 * len(extra)
            notes.append(f"장면에 없는 인물 {len(extra)}명 −{8 * len(extra)}")
        # 대본 어디에도 없는 인물: 검색어에 그 이름이 든 기관·사건 이름('김건희 특검')이 섞여 그 사람 사진이 잡힌 것
        stranger = [
            p for p in people if story and p not in story and not any(x in story for x in _ALIASES.get(p, ()))
        ]
        if stranger:
            s -= 30
            notes.append(f"대본에 없는 인물 {stranger[0]} −30")
        party = _PARTY_END_RE.search(a.title)
        if (
            party and party.group(1) not in scene_hay
        ):  # '…기자회견 갖는 민주당': 사진에 나오는 건 그 당 사람들
            s -= 20
            notes.append(f"사진 주인공이 {party.group(1)} −20")
        abroad = [m for m in _FOREIGN_RE.findall(a.title) if m not in scene_hay]
        if abroad:
            s -= 30
            notes.append(f"외국 사진({abroad[0]}) −30")
    return s, notes


NEWS_PHOTO_HOSTS = {"imgnews.pstatic.net", "imgnews.naver.net", "mimgnews.pstatic.net"}
DOC_HOSTS = ["lfind.kr", "googleusercontent.com", "scribd.com", "docslide.net"]
_DOC_RE = re.compile(
    r"google sheets|google docs|casenote|판결문|판례|서식|양식|\.pdf|\.hwp|\.xlsx?|스프레드시트"
)
# 뉴스 사진 제목인데 사진이 아닌 것: 녹취 공개(얼굴 합성 썸네일)·그래픽·카드뉴스·타임라인·방송 화면 갈무리
# 공유마당: 태그로 찾아서 '약국' 검색에 1971년 종로 흑백사진·스페인 골목이 섞인다(오늘 건강 장면과 안 맞음)
_OLD_FOREIGN_RE = re.compile(r"옛날사진|흑백|19[0-9]{2}년|해외_|E-영상역사관|문화재|불상|유물")
# 공유마당 그림(아이콘·일러스트·POP 서식) — 실사 영상·사진 사이에 끼면 튄다
_DRAWING_RE = re.compile(r"아이콘|일러스트|POP|템플릿|캐릭터|패턴|서식")
_GRAPHIC_RE = re.compile(r"녹취|그래픽|카드뉴스|인포그래픽|타임라인|도표|한눈에|갈무리|캡처|캡쳐|썸네일|합성")
# 제목 끝이 '…하는/갖는 민주당'이면 사진에 나오는 건 그 당 사람들(제목 앞 인물은 그들이 말한 대상)
_PARTY_END_RE = re.compile(
    r"(?:는|은|한|던)\s+(더불어민주당|민주당|국민의힘|조국혁신당|개혁신당|진보당)(?:\s*(?:의원들|지도부|의원단|관계자들))?\s*$"
)
_YEAR_RE = re.compile(r"/(20\d\d)/(?:0[1-9]|1[0-2])/")
MEDIA_WORDS = {"sns", "페이스북", "유튜브", "인스타그램", "트위터", "텔레그램", "블로그", "게시글", "댓글"}
# 사진 제목의 인물: '추미애 경기도지사', '장동혁 국민의힘 대표', '추미애 27.0%'(지지도), 맨 앞 '李'(대통령 줄임)
_ROLE = r"(?:대통령|원내대표|대표|의원|지사|시장|장관|총리|위원장|원장|후보|최고위원|대변인|비서실장|수석|청장|총장|당선인)"
_PERSON_RE = re.compile(
    rf"(?<![가-힣])([가-힣]{{2,3}})\s+(?:[가-힣]{{1,8}}(?:당|힘|부|청|원|도|시|회|실|처)\s+)?[가-힣]{{0,6}}?{_ROLE}(?![가-힣])"
    r"|(?<![가-힣])([가-힣]{2,3})\s?\d+(?:\.\d+)?%"
    r"|(?<![가-힣])([가-힣]{2,3})\s+(?:여사|씨)(?![가-힣])"  # '김건희 여사', '명태균 씨'(띄어 쓴 것만 — '아저씨' 아님)
)
_LEADER_RE = re.compile(r"^(?:\[[^\]]*\]\s*)?(李|尹|文)(?=[\s“\"‘'…,])")
_LEADERS = {"李": "이재명", "尹": "윤석열", "文": "문재인"}
_ALIASES = {"이재명": ("대통령", "李")}  # 장면에 '대통령'만 있어도 그 사람이 나오는 장면
_NOT_PEOPLE = {
    "경기도",
    "서울시",
    "정부",
    "국회",
    "여당",
    "야당",
    "당정",
    "신임",
    "차기",
    "전직",
    "현직",
    "前",
}
_FOREIGN_RE = re.compile(
    "미국|美|워싱턴|백악관|뉴욕|일본|도쿄|중국|베이징|러시아|모스크바|영국|런던|프랑스|독일|베를린|유럽|우크라이나|이스라엘"
)


def title_people(title: str) -> list[str]:
    """사진 제목에 나온 인물 이름(나온 순서, 중복 없이). 맨 앞 '李' 같은 줄임은 이름으로 바꾼다."""
    found: list[tuple[int, str]] = []
    m = _LEADER_RE.search(title)
    if m:
        found.append((m.start(1), _LEADERS[m.group(1)]))
    for m in _PERSON_RE.finditer(title):
        name = m.group(1) or m.group(2) or m.group(3)
        if name not in _NOT_PEOPLE and not name.endswith("당"):
            found.append((m.start(), name))
    out: list[str] = []
    for _pos, name in sorted(found):
        if name not in out:
            out.append(name)
    return out


def photo_year(a: Asset) -> int | None:
    """주소에 든 날짜(뉴스 사진 서버는 /image/003/2023/10/23/...)로 찍은 해를 어림."""
    m = _YEAR_RE.search(a.download_url or a.url or "")
    return int(m.group(1)) if m else None


def rank(
    scene: Scene,
    assets: list[Asset],
    cfg: ChannelConfig,
    prev_key: str | None = None,
    clip_scorer: Callable[[Asset, Scene], float] | None = None,
    used: dict[str, int] | None = None,
    story: str = "",
    drop: tuple[str, ...] = (),
) -> list[Asset]:
    """정렬된 후보(차단 제외). 점수·근거는 Asset.score / score_notes에 기록.
    used: 앞 장면들이 이미 고른 자료 ID와 횟수(같은 사진이 영상 내내 되풀이되지 않게 감점).
    story·drop은 score_asset 참고(대본 전체, 작업자가 뺀 사진)."""
    route = cfg.images.sources or route_for(scene)  # 소재 사이트만 쓰는 채널은 설정 순서가 곧 순위
    scored: list[Asset] = []
    for a in assets:
        res = score_asset(a, scene, route, cfg, story, drop)
        if res is None:
            continue
        s, notes = res
        if prev_key and asset_key(a) == prev_key:
            s -= 10
            notes.append("직전 장면과 같은 출처 −10")
        if used and used.get(
            a.id
        ):  # 관련 없는 사진보다는 되풀이가 낫다 — 키워드 일치 점수를 뒤집지 않을 만큼만
            s -= 15 * used[a.id]
            notes.append(f"앞 장면에서 이미 씀 −{15 * used[a.id]}")
        if clip_scorer is not None:
            sim = clip_scorer(a, scene)
            s += 20 * sim
            notes.append(f"CLIP {sim:.2f} +{20 * sim:.1f}")
        scored.append(a.model_copy(update={"score": round(s, 1), "score_notes": notes}))
    scored.sort(key=lambda a: -a.score)
    # 같은 장면 안 같은 출처 반복 감점 후 재정렬(안정 정렬)
    seen: dict[str, int] = {}
    for a in scored:
        k = asset_key(a)
        if seen.get(k):
            a.score = round(a.score - 5 * seen[k], 1)
            a.score_notes.append(f"장면 내 중복 출처 −{5 * seen[k]}")
        seen[k] = seen.get(k, 0) + 1
    scored.sort(key=lambda a: -a.score)
    return scored


def clip_scorer_or_none() -> Callable[[Asset, Scene], float] | None:
    """--clip: open_clip이 설치돼 있으면 로컬 이미지-키워드 유사도 함수, 없으면 None."""
    try:
        import open_clip  # type: ignore[import-not-found]
        import torch  # type: ignore[import-not-found]
        from PIL import Image
    except ImportError:
        return None
    model, _, preprocess = open_clip.create_model_and_transforms("ViT-B-32", pretrained="laion2b_s34b_b79k")
    tokenizer = open_clip.get_tokenizer("ViT-B-32")
    model.eval()

    def score(a: Asset, scene: Scene) -> float:
        if not a.local_path or a.media != "image":
            return 0.0
        with torch.no_grad():
            img = preprocess(Image.open(a.local_path)).unsqueeze(0)
            txt = tokenizer([" ".join(scene.keywords_en)])
            fi, ft = model.encode_image(img), model.encode_text(txt)
            fi /= fi.norm(dim=-1, keepdim=True)
            ft /= ft.norm(dim=-1, keepdim=True)
            return float(max(0.0, (fi @ ft.T).item()))

    return score
