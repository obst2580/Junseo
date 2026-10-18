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
    a: Asset, scene: Scene, route: list[str], cfg: ChannelConfig
) -> tuple[float, list[str]] | None:
    """점수와 근거. 차단 대상이면 None."""
    notes: list[str] = []
    if a.media == "video":
        if is_blocked(a.author_or_channel, cfg.videos.blocked_channels):
            return None
    elif domain_match(a.source_domain, cfg.images.blocked_domains):
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
    if a.media == "image" or stock_video:
        title = a.title.lower()
        if scene.query:  # 작업자가 대사를 읽고 쓴 검색어 — 그 말이 사진 제목에 있어야 그 장면 사진
            words = [w.lower() for w in scene.query.split() if len(w) >= 2]
            qh = sum(1 for w in words if w in title)
            if qh:
                s += min(30, 10 * qh)
                notes.append(f"검색어 {qh}개 제목 일치 +{min(30, 10 * qh)}")
            elif words:
                s -= 20
                notes.append("제목에 검색어 없음 −20")
    if a.media == "image":
        title = a.title.lower()
        if a.source_domain in NEWS_PHOTO_HOSTS:
            s += 10
            notes.append("뉴스 사진 +10")
        if _DOC_RE.search(title) or domain_match(a.source_domain, DOC_HOSTS):
            s -= 25
            notes.append("문서·표 캡처 −25")
        year = photo_year(a)
        if year:
            now = now_kst().year
            if year >= now - 1:
                s += 8
                notes.append(f"{year}년 사진 +8")
            elif year <= now - 6:
                s -= 5
                notes.append(f"{year}년 사진 −5")
    return s, notes


NEWS_PHOTO_HOSTS = {"imgnews.pstatic.net", "imgnews.naver.net", "mimgnews.pstatic.net"}
DOC_HOSTS = ["lfind.kr", "googleusercontent.com", "scribd.com", "docslide.net"]
_DOC_RE = re.compile(
    r"google sheets|google docs|casenote|판결문|판례|서식|양식|\.pdf|\.hwp|\.xlsx?|스프레드시트"
)
_YEAR_RE = re.compile(r"/(20\d\d)/(?:0[1-9]|1[0-2])/")


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
) -> list[Asset]:
    """정렬된 후보(차단 제외). 점수·근거는 Asset.score / score_notes에 기록.
    used: 앞 장면들이 이미 고른 자료 ID와 횟수(같은 사진이 영상 내내 되풀이되지 않게 감점)."""
    route = cfg.images.sources or route_for(scene)  # 소재 사이트만 쓰는 채널은 설정 순서가 곧 순위
    scored: list[Asset] = []
    for a in assets:
        res = score_asset(a, scene, route, cfg)
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
