"""stories.yaml → 실제 API 응답 형식의 fixture 파일 생성.

생성물: rss/*.xml(RSS 2.0), naver/news_*.json(네이버 뉴스 검색 형식),
youtube/playlist_*.json · youtube/stats.json(YouTube Data API 형식),
schedule/*.yaml(수동 일정), routes.yaml(fixture 전송계층 규칙).

실행: uv run python fixtures/demo/build.py
"""

from __future__ import annotations

import hashlib
import json
import re
from datetime import UTC, datetime, timedelta, timezone
from email.utils import format_datetime
from pathlib import Path
from xml.sax.saxutils import escape

import yaml

KST = timezone(timedelta(hours=9), "KST")  # 윈도우에서도 시간대 DB 없이 돌게(한국은 서머타임 없음)
UTC = UTC
HERE = Path(__file__).parent

FEEDS = {
    "yna_politics": ("연합뉴스 정치", "https://www.yna.co.kr/rss/politics.xml", "news.example-yna.kr"),
    "yna_economy": ("연합뉴스 경제", "https://www.yna.co.kr/rss/economy.xml", "news.example-yna.kr"),
    "yna_intl": ("연합뉴스 국제", "https://www.yna.co.kr/rss/international.xml", "news.example-yna.kr"),
    "bbc": ("BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", "www.example-bbc.co.uk"),
    "press": ("정부 보도자료(가상)", "https://press.example.go.kr/rss/press.xml", "press.example.go.kr"),
}
NAVER_QUERIES = [
    "국회",
    "플랫폼노동자 기본법",
    "국정감사",
    "지역의료",
    "정치자금 재판",
    "새한당",
    "환율",
    "택배노조",
]


def _dt(s: str) -> datetime:
    return datetime.strptime(s, "%Y-%m-%d %H:%M").replace(tzinfo=KST)


def _url(host: str, a: dict) -> str:
    h = hashlib.sha1((a["at"] + a["title"]).encode()).hexdigest()[:10]
    return f"https://{host}/article/{_dt(a['at']):%Y%m%d}/{h}"


def build_rss(articles: list[dict]) -> None:
    out = HERE / "rss"
    out.mkdir(exist_ok=True)
    for key, (name, _, host) in FEEDS.items():
        items = []
        for a in sorted((a for a in articles if a["src"] == key), key=lambda a: a["at"], reverse=True):
            pub = _dt(a["at"])
            if key == "bbc":
                pub = pub.astimezone(UTC)
            items.append(
                "    <item>\n"
                f"      <title>{escape(a['title'])}</title>\n"
                f"      <link>{_url(host, a)}</link>\n"
                f"      <description>{escape(a['desc'])}</description>\n"
                f"      <pubDate>{format_datetime(pub)}</pubDate>\n"
                "    </item>"
            )
        xml = (
            '<?xml version="1.0" encoding="UTF-8"?>\n<rss version="2.0">\n  <channel>\n'
            f"    <title>{escape(name)} (시연용 가상 피드)</title>\n    <link>https://{host}/</link>\n"
            "    <description>studio 시연 fixture — 실존 기사 아님</description>\n"
            + "\n".join(items)
            + "\n  </channel>\n</rss>\n"
        )
        (out / f"{key}.xml").write_text(xml, encoding="utf-8")


def build_youtube(spec: dict) -> dict:
    out = HERE / "youtube"
    out.mkdir(exist_ok=True)
    stats: dict = {}
    for ch in spec["competitors"]:
        items = []
        for v in ch["videos"]:
            utc = _dt(v["at"]).astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
            items.append({"contentDetails": {"videoId": v["id"], "videoPublishedAt": utc}})
            stats[v["id"]] = {
                "title": v["title"],
                "description": f"{v['title']} — 가상 경쟁 채널 영상",
                "channelId": ch["channel_id"],
                "channelTitle": ch["channel_title"],
                "publishedAt": utc,
                "viewCount": v["views"],
                "likeCount": v["views"] // 40,
                "commentCount": v["views"] // 200,
            }
        (out / f"playlist_{ch['channel_id']}.json").write_text(
            json.dumps({"items": items}, ensure_ascii=False), encoding="utf-8"
        )
    for v in spec["own_videos"]:
        stats[v["id"]] = {
            "title": v["title"],
            "description": "",
            "channelId": "UCdemoOWNCHANNEL0000000000",
            "channelTitle": "내 채널(시연)",
            "publishedAt": "2026-10-08T10:00:00Z",
            "viewCount": v["views"],
            "likeCount": v["views"] // 30,
            "commentCount": v["views"] // 150,
        }
    (out / "stats.json").write_text(json.dumps(stats, ensure_ascii=False, indent=1), encoding="utf-8")
    return stats


def build_schedule(spec: dict) -> None:
    out = HERE / "schedule"
    out.mkdir(exist_ok=True)
    for day, entries in spec["schedule"].items():
        (out / f"{day}.yaml").write_text(
            yaml.safe_dump(entries, allow_unicode=True, sort_keys=False), encoding="utf-8"
        )


COMMUNITY = {
    # 이름: (목록 URL, 글 주소 템플릿, 행 HTML 템플릿) — 사이트마다 마크업·시각 표기를 다르게 해 범용 파서를 시험한다
    "다모앙": (
        "https://damoang.net/free?page=1",
        "/free/{no}",
        '<li class="list-group-item"><a href="{href}" class="da-link">{title}</a>'
        ' <span class="count-plus">[{comments}]</span> <span class="da-list-date">{when}</span></li>',
    ),
    "보배드림": (
        "https://www.bobaedream.co.kr/list?code=politic&page=1",
        "/view?code=politic&amp;No={no}&amp;bm=1",
        '<tr><td class="pl14"><a class="bsubject" href="{href}">{title}</a>'
        ' <strong class="totreply">{comments}</strong></td><td class="date">{when}</td></tr>',
    ),
    "뽐뿌": (
        "https://www.ppomppu.co.kr/zboard/zboard.php?id=politics&page=1",
        "view.php?id=politics&amp;page=1&amp;no={no}",
        '<tr class="baseList"><td class="title"><a href="{href}"><span class="baseList-title">{title}</span></a>'
        ' <span class="baseList-c">{comments}</span></td><td class="baseList-space"><time>{when}</time></td></tr>',
    ),
    "더쿠": (
        "https://theqoo.net/hot?page=1",
        "/hot/{no}",
        '<tr><td class="title"><a href="{href}">{title}</a><a href="{href}#comment" class="replyNum">{comments}</a></td>'
        '<td class="time">{when}</td></tr>',
    ),
}
FETCH_DAY = "2026-10-10"


def _when(site: str, at: str) -> str:
    dt = _dt(at)
    if at.startswith(FETCH_DAY):
        return dt.strftime("%H:%M")
    if site == "뽐뿌":
        return dt.strftime("%y/%m/%d %H:%M:%S")
    return dt.strftime("%m.%d %H:%M")


def build_community(spec: dict) -> None:
    out = HERE / "community"
    out.mkdir(exist_ok=True)
    for site, posts in spec.get("community_posts", {}).items():
        _url, href_tpl, row = COMMUNITY[site]
        rows = []
        for p in sorted(posts, key=lambda p: p["at"], reverse=True):
            no = 7000000 + int(hashlib.sha1((site + p["at"] + p["title"]).encode()).hexdigest()[:5], 16)
            rows.append(
                row.format(
                    href=href_tpl.format(no=no),
                    title=escape(p["title"]),
                    comments=p["comments"],
                    when=_when(site, p["at"]),
                )
            )
        notice = row.format(
            href=href_tpl.format(no=1), title="[공지] 게시판 이용 규칙", comments=0, when="01.01"
        )
        html = (
            f"<!doctype html><html><head><meta charset='utf-8'><title>{site} (시연용 가상 게시판)</title></head><body>\n"
            "<p>studio 시연 fixture — 실존 게시글 아님</p>\n<table>\n"
            + notice
            + "\n"
            + "\n".join(rows)
            + "\n</table><a href='/login'>로그인</a></body></html>\n"
        )
        (out / f"{site}.html").write_text(html, encoding="utf-8")


def build_naver_index(articles: list[dict]) -> None:
    """네이버 뉴스 검색 생성기가 쓰는 전체 기사 색인(검색어 토큰이 모두 들어간 기사를 돌려줌)."""
    items = []
    for a in sorted((a for a in articles if a["src"] == "naver"), key=lambda a: a["at"], reverse=True):
        link = _url("news.example-portal.com", a)
        items.append(
            {
                "title": a["title"],
                "originallink": link.replace("news.example-portal.com", "www.example-daily.co.kr"),
                "link": link,
                "description": a["desc"],
                "pubDate": format_datetime(_dt(a["at"])),
                "queries": [a["q"]] if a.get("q") else [],
            }
        )
    (HERE / "naver" / "index.json").write_text(
        json.dumps(items, ensure_ascii=False, indent=1), encoding="utf-8"
    )


def build_routes(spec: dict) -> None:
    routes: list[dict] = []
    for key, (_n, url, _h) in FEEDS.items():
        routes.append({"match": url.replace(".", r"\.").replace("?", r"\?"), "file": f"rss/{key}.xml"})
    # 네이버 뉴스: 설정 검색어든 커뮤니티 화제에서 뽑은 검색어든 같은 색인에서 찾는다
    routes.append(
        {
            "match": r"(openapi\.naver\.com/v1/search/news\.json|naverapihub\.apigw\.ntruss\.com/search/v1/news)",
            "generate": "naver_news",
            "data": "naver/index.json",
        }
    )
    for site, (url, _h, _r) in COMMUNITY.items():
        routes.append(
            {
                "match": re.escape(url),
                "file": f"community/{site}.html",
                "content_type": "text/html; charset=utf-8",
            }
        )
    routes.append({"match": r"/robots\.txt$", "generate": "robots_allow"})
    for ch in spec["competitors"]:
        routes.append(
            {
                "match": r"youtube/v3/playlistItems",
                "params": {"playlistId": "UU" + ch["channel_id"][2:]},
                "file": f"youtube/playlist_{ch['channel_id']}.json",
            }
        )
    routes += [
        {
            "match": r"youtube/v3/videos",
            "params": {"part": "snippet,statistics"},
            "generate": "youtube_stats",
            "data": "youtube/stats.json",
        },
        {"match": r"youtube/v3/videos", "generate": "youtube_videos"},
        {"match": r"youtube/v3/search", "generate": "youtube_search"},
        {"match": r"googleapis\.com/customsearch/v1", "generate": "google_image"},
        {
            "match": r"(openapi\.naver\.com/v1/search/image|naverapihub\.apigw\.ntruss\.com/search/v1/image)",
            "generate": "naver_image",
        },
        {"match": r"commons\.wikimedia\.org/w/api\.php", "generate": "wikimedia"},
        {"match": r"img\.example\.com/", "generate": "image"},
        {"match": r"hooks\.slack\.com/", "file": "slack_ok.txt", "content_type": "text/plain"},
    ]
    header = "# build.py가 생성 — 직접 수정하지 말 것\n"
    (HERE / "routes.yaml").write_text(
        header + yaml.safe_dump({"routes": routes}, allow_unicode=True, sort_keys=False), encoding="utf-8"
    )
    (HERE / "slack_ok.txt").write_text("ok", encoding="utf-8")


def main() -> None:
    spec = yaml.safe_load((HERE / "stories.yaml").read_text(encoding="utf-8"))
    arts = spec["articles"]
    build_rss(arts)
    build_youtube(spec)
    build_schedule(spec)
    build_community(spec)
    build_naver_index(arts)
    build_routes(spec)
    print(f"articles={len(arts)} feeds={len(FEEDS)} naver_queries={len(NAVER_QUERIES)} → {HERE}")


if __name__ == "__main__":
    main()
