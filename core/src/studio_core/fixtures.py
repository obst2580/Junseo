"""시연·테스트용 fixture 전송계층.

`STUDIO_FIXTURES=<dir>`를 주면 모든 HTTP 요청이 `<dir>/routes.yaml` 규칙에 따라
파일 또는 생성기 응답으로 대체된다. 네트워크 없이 수집기·소스 파서를
그대로 통과시키는 것이 목적이다(파서는 실제 응답 형식을 그대로 읽는다).

routes.yaml 예:
    routes:
      - match: "yna.co.kr/rss/politics.xml"      # URL 정규식(re.search)
        file: rss/yna_politics.xml
      - match: "openapi.naver.com/v1/search/news.json"
        params: {query: "국회"}                    # 쿼리 파라미터 일치 조건(선택)
        file: naver/news_국회.json
      - match: "googleapis.com/customsearch/v1"
        generate: google_image                    # 검색어로 결정적 응답 생성
"""

from __future__ import annotations

import hashlib
import io
import json
import re
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, quote, urlencode, urlparse

import httpx
import yaml
from PIL import Image, ImageDraw

from studio_core.fonts import load_font

_CT = {
    ".xml": "application/rss+xml; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".jpg": "image/jpeg",
    ".png": "image/png",
    ".html": "text/html; charset=utf-8",
}


def _h(text: str, n: int = 8) -> str:
    return hashlib.sha1(text.encode("utf-8")).hexdigest()[:n]


class FixtureTransport(httpx.AsyncBaseTransport):
    """routes.yaml 기반 가짜 전송계층."""

    def __init__(self, root: Path) -> None:
        self.root = root
        spec = yaml.safe_load((root / "routes.yaml").read_text(encoding="utf-8")) or {}
        self.routes: list[dict[str, Any]] = spec.get("routes", [])
        self.log: list[str] = []

    async def handle_async_request(self, request: httpx.Request) -> httpx.Response:
        url = str(request.url)
        params = {k: v[0] for k, v in parse_qs(urlparse(url).query).items()}
        if request.method == "POST" and request.content:
            try:
                params.update(
                    {
                        k: str(v)
                        for k, v in json.loads(request.content).items()
                        if not isinstance(v, dict | list)
                    }
                )
            except ValueError:
                pass
        self.log.append(url)
        for route in self.routes:
            if not re.search(route["match"], url):
                continue
            want = route.get("params") or {}
            if any(params.get(k) != str(v) for k, v in want.items()):
                continue
            status = int(route.get("status", 200))
            if "file" in route:
                path = self.root / route["file"]
                ctype = route.get("content_type") or _CT.get(path.suffix, "application/octet-stream")
                return httpx.Response(
                    status, content=path.read_bytes(), headers={"content-type": ctype}, request=request
                )
            gen = GENERATORS[route["generate"]]
            body, ctype = gen(url, params, {"root": self.root, "route": route})
            return httpx.Response(status, content=body, headers={"content-type": ctype}, request=request)
        return httpx.Response(404, content=b"fixture route not found", request=request)


# ---- 생성기 -----------------------------------------------------------------

_DOMAINS = [
    ("www.assembly.go.kr", "국회"),
    ("www.korea.kr", "대한민국 정책브리핑"),
    ("news.example-daily.co.kr", "가상일보"),
    ("www.example-times.kr", "가상타임스"),
    ("commons.wikimedia.org", "Wikimedia Commons"),
    ("blog.example-portal.com", "가상 블로그"),
    ("www.moef.go.kr", "기획재정부"),
]
_SIZES = [(1920, 1080), (2400, 1600), (1280, 720), (800, 1200), (1024, 768), (3000, 2000), (640, 480)]
_CHANNELS = [
    ("UC_KTV_FAKE00000000000000", "KTV 국민방송"),
    ("UC_NATV_FAKE0000000000000", "국회방송 NATV"),
    ("UC_YTN_FAKE00000000000000", "YTN"),
    ("UC_KBS_FAKE00000000000000", "KBS News"),
    ("UC_PARTY_FAKE000000000000", "가상정당 공식채널"),
    ("UC_INDIE_FAKE000000000000", "시사 독립채널"),
]


def _img_url(prefix: str, key: str, i: int, w: int, h: int, label: str) -> str:
    return f"https://img.example.com/{prefix}/{_h(key)}_{i}.jpg?" + urlencode(
        {"w": w, "h": h, "label": label[:30]}
    )


def gen_google_image(url: str, params: dict[str, str], ctx: dict[str, Any]) -> tuple[bytes, str]:
    """Custom Search JSON API(searchType=image) 형식."""
    q = params.get("q", "")
    items = []
    for i in range(int(params.get("num", 10))):
        dom, site = _DOMAINS[(int(_h(q + str(i)), 16) + i) % len(_DOMAINS)]
        w, h = _SIZES[(int(_h(q), 16) + i) % len(_SIZES)]
        items.append(
            {
                "title": f"{q} - {site} 사진 {i + 1}",
                "link": _img_url("g", q, i, w, h, q),
                "displayLink": dom,
                "mime": "image/jpeg",
                "image": {
                    "contextLink": f"https://{dom}/article/{_h(q + str(i))}",
                    "height": h,
                    "width": w,
                    "thumbnailLink": _img_url("gt", q, i, 160, 90, q),
                },
            }
        )
    return json.dumps({"items": items}, ensure_ascii=False).encode(), "application/json"


def gen_naver_image(url: str, params: dict[str, str], ctx: dict[str, Any]) -> tuple[bytes, str]:
    """네이버 이미지 검색 API 형식."""
    q = params.get("query", "")
    items = []
    for i in range(int(params.get("display", 10))):
        w, h = _SIZES[(int(_h(q), 16) + 2 * i) % len(_SIZES)]
        dom = _DOMAINS[(i + 2) % len(_DOMAINS)][0]
        link = f"https://img.example.com/n/{_h(q)}_{i}.jpg?" + urlencode(
            {"w": w, "h": h, "label": q[:30], "host": dom}
        )
        items.append(
            {
                "title": f"<b>{q}</b> 현장 {i + 1}",
                "link": link,
                "thumbnail": link,
                "sizeheight": str(h),
                "sizewidth": str(w),
            }
        )
    return json.dumps({"total": 100, "items": items}, ensure_ascii=False).encode(), "application/json"


def gen_youtube_search(url: str, params: dict[str, str], ctx: dict[str, Any]) -> tuple[bytes, str]:
    """YouTube Data API search.list 형식."""
    q = params.get("q", "")
    items = []
    for i in range(int(params.get("maxResults", 10))):
        cid, ctitle = _CHANNELS[(int(_h(q), 16) + i) % len(_CHANNELS)]
        vid = ("v" + _h(q + str(i), 10)).ljust(11, "x")[:11]
        items.append(
            {
                "id": {"kind": "youtube#video", "videoId": vid},
                "snippet": {
                    "title": f"{q} 관련 {ctitle} 영상 {i + 1}",
                    "description": f"{q} 현장",
                    "channelId": cid,
                    "channelTitle": ctitle,
                    "publishedAt": "2026-10-08T03:00:00Z",
                },
            }
        )
    return json.dumps({"items": items}, ensure_ascii=False).encode(), "application/json"


def gen_youtube_videos(url: str, params: dict[str, str], ctx: dict[str, Any]) -> tuple[bytes, str]:
    """YouTube Data API videos.list(contentDetails) 형식."""
    ids = [i for i in params.get("id", "").split(",") if i]
    items = []
    for vid in ids:
        secs = 30 + int(_h(vid), 16) % 900
        items.append(
            {
                "id": vid,
                "contentDetails": {"duration": f"PT{secs // 60}M{secs % 60}S", "definition": "hd"},
                "snippet": {"title": f"영상 {vid}", "channelTitle": "", "description": ""},
            }
        )
    return json.dumps({"items": items}, ensure_ascii=False).encode(), "application/json"


def gen_wikimedia(url: str, params: dict[str, str], ctx: dict[str, Any]) -> tuple[bytes, str]:
    """MediaWiki API(generator=search, prop=imageinfo) 형식."""
    q = params.get("gsrsearch", "")
    pages: dict[str, Any] = {}
    for i in range(int(params.get("gsrlimit", 5))):
        w, h = _SIZES[(int(_h(q), 16) + i) % 3]
        title = f"File:{q.replace(' ', '_')}_{i + 1}.jpg"
        pages[str(1000 + i)] = {
            "pageid": 1000 + i,
            "title": title,
            "imageinfo": [
                {
                    "url": _img_url("w", q, i, w, h, q),
                    "descriptionurl": f"https://commons.wikimedia.org/wiki/{quote(title)}",
                    "width": w,
                    "height": h,
                    "mime": "image/jpeg",
                    "extmetadata": {
                        "Artist": {"value": f"<a href='#'>가상작가{i + 1}</a>"},
                        "LicenseShortName": {"value": "CC BY-SA 4.0" if i % 2 == 0 else "CC BY 4.0"},
                        "ImageDescription": {"value": f"{q} 사진"},
                    },
                }
            ],
        }
    return json.dumps({"query": {"pages": pages}}, ensure_ascii=False).encode(), "application/json"


def gen_image(url: str, params: dict[str, str], ctx: dict[str, Any]) -> tuple[bytes, str]:
    """요청한 크기의 단색 JPEG + 라벨."""
    w = max(16, min(int(params.get("w", 1280)), 4000))
    h = max(16, min(int(params.get("h", 720)), 4000))
    key = _h(url, 6)
    color = tuple(int(key[i : i + 2], 16) // 2 + 40 for i in (0, 2, 4))
    img = Image.new("RGB", (w, h), color)
    draw = ImageDraw.Draw(img)
    label = params.get("label", "")
    if label and w >= 200:
        font = load_font(max(14, w // 30))
        draw.text((w // 20, h // 2), f"[시연용 가상 이미지] {label}", fill=(255, 255, 255), font=font)
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=70)
    return buf.getvalue(), "image/jpeg"


def gen_youtube_stats(url: str, params: dict[str, str], ctx: dict[str, Any]) -> tuple[bytes, str]:
    """videos.list(snippet,statistics): route.data JSON({id: {...}})에서 찾아 응답."""
    data = json.loads((ctx["root"] / ctx["route"]["data"]).read_text(encoding="utf-8"))
    items = []
    for vid in [i for i in params.get("id", "").split(",") if i]:
        v = data.get(vid)
        if v is None:
            continue
        items.append(
            {
                "id": vid,
                "snippet": {
                    k: v[k] for k in ("title", "description", "channelId", "channelTitle", "publishedAt")
                },
                "statistics": {k: str(v[k]) for k in ("viewCount", "likeCount", "commentCount")},
            }
        )
    return json.dumps({"items": items}, ensure_ascii=False).encode(), "application/json"


def gen_naver_news(url: str, params: dict[str, str], ctx: dict[str, Any]) -> tuple[bytes, str]:
    """네이버 뉴스 검색: 색인(route.data) 중 검색어 토큰이 모두 들어간 기사.

    실제 검색은 본문까지 보므로, 색인 항목의 `queries`(fixture 전용)에 지정한 검색어로도 찾아지게 한다.
    """
    index = json.loads((ctx["root"] / ctx["route"]["data"]).read_text(encoding="utf-8"))
    query = params.get("query", "").strip()
    toks = [t for t in query.split() if t]

    def hit(it: dict[str, Any]) -> bool:
        return bool(toks) and (
            query in it.get("queries", []) or all(t in it["title"] + it["description"] for t in toks)
        )

    hits = [{k: v for k, v in it.items() if k != "queries"} for it in index if hit(it)]
    hits = hits[: int(params.get("display", 100))]
    body = {"total": len(hits), "start": 1, "display": len(hits), "items": hits}
    return json.dumps(body, ensure_ascii=False).encode(), "application/json"


def gen_robots_allow(url: str, params: dict[str, str], ctx: dict[str, Any]) -> tuple[bytes, str]:
    """모든 경로 허용 robots.txt."""
    return b"User-agent: *\nAllow: /\n", "text/plain"


GENERATORS = {
    "naver_news": gen_naver_news,
    "robots_allow": gen_robots_allow,
    "youtube_stats": gen_youtube_stats,
    "google_image": gen_google_image,
    "naver_image": gen_naver_image,
    "youtube_search": gen_youtube_search,
    "youtube_videos": gen_youtube_videos,
    "wikimedia": gen_wikimedia,
    "image": gen_image,
}
