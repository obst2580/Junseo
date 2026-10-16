import json
from datetime import date

import httpx
import pytest
import respx

from newsclip.rank import asset_key, domain_match, rank, route_for
from newsclip.sources.base import Budget, SearchCache, SourceUnavailable
from newsclip.sources.google_image import GoogleImageSource
from newsclip.sources.google_image import parse_items as g_parse
from newsclip.sources.naver_image import NaverImageSource
from newsclip.sources.naver_image import parse_items as n_parse
from newsclip.sources.wikimedia import WikimediaSource, parse_pages
from newsclip.sources.youtube_video import YouTubeVideoSource, default_clip_start, is_blocked, parse_duration
from studio_core.config import ChannelConfig

from nch import asset, scene

CFG = ChannelConfig.model_validate(
    {
        "images": {"preferred_domains": ["go.kr", "정당 공식 도메인"], "blocked_domains": ["bad.example"]},
        "videos": {"preferred_channels": ["KTV 국민방송"], "blocked_channels": ["YTN", "KBS News"]},
    }
)


def test_parsers():
    g = g_parse(
        {
            "items": [
                {
                    "title": "국회",
                    "link": "https://i/1.jpg",
                    "displayLink": "www.assembly.go.kr",
                    "image": {"contextLink": "https://www.assembly.go.kr/a", "width": 1920, "height": 1080},
                },
                {"link": ""},
            ]
        }
    )
    assert len(g) == 1 and g[0].source_domain == "assembly.go.kr" and g[0].source_page_url.endswith("/a")
    n = n_parse(
        {
            "items": [
                {
                    "title": "<b>국회</b>",
                    "link": "https://img.news.example/1.jpg",
                    "sizewidth": "1280",
                    "sizeheight": "720",
                },
                {"title": "x", "link": "https://i/2", "sizewidth": "n/a"},
            ]
        }
    )
    assert n[0].title == "국회" and n[0].width == 1280 and n[1].width is None
    w = parse_pages(
        {
            "query": {
                "pages": {
                    "1": {
                        "title": "File:A.jpg",
                        "imageinfo": [
                            {
                                "url": "https://u/a.jpg",
                                "mime": "image/jpeg",
                                "width": 100,
                                "height": 50,
                                "descriptionurl": "https://c/A",
                                "extmetadata": {
                                    "Artist": {"value": "<a>홍길동</a>"},
                                    "LicenseShortName": {"value": "CC BY-SA 4.0"},
                                },
                            }
                        ],
                    },
                    "2": {"title": "File:B.pdf", "imageinfo": [{"mime": "application/pdf"}]},
                }
            }
        }
    )
    assert len(w) == 1 and w[0].author_or_channel == "홍길동" and w[0].license == "CC BY-SA 4.0"


def test_youtube_helpers():
    assert (
        parse_duration("PT1H2M3S") == 3723 and parse_duration("PT45S") == 45 and parse_duration("bad") is None
    )
    assert (
        is_blocked("YTN", ["YTN"])
        and is_blocked("KBS  news", ["KBS News"])
        and not is_blocked("KTV 국민방송", ["YTN"])
    )
    assert (
        default_clip_start(None) == 0.0
        and default_clip_start(100) == 10.0
        and default_clip_start(3600) == 60.0
    )


@respx.mock
async def test_youtube_source_excludes_blocked(monkeypatch, tmp_path):
    monkeypatch.setenv("YOUTUBE_API_KEY", "k")
    respx.get("https://www.googleapis.com/youtube/v3/search").mock(
        return_value=httpx.Response(
            200,
            json={
                "items": [
                    {"id": {"videoId": "a1"}, "snippet": {"title": "본회의", "channelTitle": "YTN"}},
                    {
                        "id": {"videoId": "b2"},
                        "snippet": {"title": "본회의 &quot;표결&quot;", "channelTitle": "KTV 국민방송"},
                    },
                ]
            },
        )
    )
    videos = respx.get("https://www.googleapis.com/youtube/v3/videos").mock(
        return_value=httpx.Response(
            200, json={"items": [{"id": "b2", "contentDetails": {"duration": "PT10M", "definition": "hd"}}]}
        )
    )
    async with httpx.AsyncClient() as c:
        src = YouTubeVideoSource(c, blocked=["YTN"])
        got = await src.search("본회의", "video")
    assert (
        [a.author_or_channel for a in got] == ["KTV 국민방송"]
        and got[0].duration_sec == 600
        and got[0].clip_start_sec == 60
    )
    assert videos.calls[0].request.url.params["id"] == "b2"
    assert got[0].title == '본회의 "표결"'  # API 제목의 HTML 엔티티는 풀어 둔다


@respx.mock
async def test_google_naver_wikimedia_sources(monkeypatch, tmp_path):
    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T10:00")
    monkeypatch.delenv("GOOGLE_API_KEY", raising=False)
    async with httpx.AsyncClient() as c:
        with pytest.raises(SourceUnavailable):
            await GoogleImageSource(c).search("국회", "photo")
        monkeypatch.setenv("GOOGLE_API_KEY", "k")
        monkeypatch.setenv("GOOGLE_CSE_ID", "cx")
        route = respx.get("https://www.googleapis.com/customsearch/v1").mock(
            return_value=httpx.Response(200, json={"items": []})
        )
        assert await GoogleImageSource(c).search("국회", "photo") == []
        params = route.calls[0].request.url.params
        assert (
            params["searchType"] == "image"
            and params["gl"] == "kr"
            and params["lr"] == "lang_ko"
            and "rights" not in params
        )
        monkeypatch.setenv("STUDIO_NOW", "2027-01-01T00:00")
        with pytest.raises(SourceUnavailable):
            await GoogleImageSource(c).search("국회", "photo")
        monkeypatch.setenv("NAVER_CLIENT_ID", "i")
        monkeypatch.setenv("NAVER_CLIENT_SECRET", "s")
        nr = respx.get("https://naverapihub.apigw.ntruss.com/search/v1/image").mock(
            return_value=httpx.Response(200, json={"items": []})
        )
        await NaverImageSource(c).search("국회", "photo")
        assert nr.calls[0].request.url.params["filter"] == "large"
        wr = respx.get("https://commons.wikimedia.org/w/api.php").mock(
            return_value=httpx.Response(200, json={})
        )
        await WikimediaSource(c).search("National Assembly", "photo")
        assert wr.calls[0].request.url.params["gsrnamespace"] == "6"
        assert "studio/" in wr.calls[0].request.headers["user-agent"]


async def test_budget_and_cache(tmp_path, monkeypatch):
    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T10:00")
    b = Budget(tmp_path / "u.json", {"google_image": 1})
    b.take("google_image")
    with pytest.raises(SourceUnavailable):
        b.take("google_image")
    assert (
        b.used("google_image") == 1
        and json.loads((tmp_path / "u.json").read_text())["2026-10-08"]["google_image"] == 1
    )
    cache = SearchCache(tmp_path / "c.json")
    cache.put("k", [asset()])
    assert cache.get("k")[0].url == "https://img.example.com/a.jpg" and cache.get("none") is None

    class Counting(WikimediaSource):
        calls = 0

        async def _search(self, query, kind):
            Counting.calls += 1
            return [asset("wikimedia")]

    async with httpx.AsyncClient() as c:
        src = Counting(c, Budget(tmp_path / "u2.json", {}), SearchCache(tmp_path / "c2.json"))
        import asyncio

        await asyncio.gather(src.search("q", "photo"), src.search("q", "photo"))
        await src.search("q", "photo")
    assert Counting.calls == 1  # 동시 요청 합류 + 캐시


def test_rank_rules():
    sc = scene("s001", 0, 5, kind="photo")
    land = asset(
        "naver_image",
        "https://i/land.jpg",
        width=1920,
        height=1080,
        title="국회 본회의",
        source_domain="news.example",
    )
    port = asset("naver_image", "https://i/port.jpg", width=800, height=1200, source_domain="other.example")
    pref = asset("naver_image", "https://i/pref.jpg", width=1920, height=1080, source_domain="moef.go.kr")
    blocked = asset("naver_image", "https://i/b.jpg", width=1920, height=1080, source_domain="bad.example")
    out = rank(sc, [port, land, pref, blocked], CFG)
    assert [a.url for a in out][:2] == ["https://i/pref.jpg", "https://i/land.jpg"] and blocked.url not in [
        a.url for a in out
    ]
    assert out[-1].url == "https://i/port.jpg"
    # 직전 장면과 같은 출처 감점
    out2 = rank(sc, [pref, land], CFG, prev_key=asset_key(pref))
    assert "직전 장면과 같은 출처 −10" in out2[[a.url for a in out2].index(pref.url)].score_notes
    vid_block = asset("youtube_video", "https://y/1", media="video", author_or_channel="YTN")
    vid_ok = asset(
        "youtube_video",
        "https://y/2",
        media="video",
        author_or_channel="KTV 국민방송",
        width=1920,
        height=1080,
    )
    vs = rank(scene("s002", 0, 5, kind="video"), [vid_block, vid_ok], CFG)
    assert [a.author_or_channel for a in vs] == ["KTV 국민방송"] and "선호 출처 +15" in vs[0].score_notes
    assert domain_match("moef.go.kr", ["go.kr"]) and not domain_match("go.kr.evil.com", ["go.kr"])
    assert route_for(scene("x", 0, 1, kind="video"), google=True) == ["youtube_video", "google_image"]
    assert route_for(scene("x", 0, 1, kind="photo", keywords_ko=["국회"]), google=True) == [
        "google_image",
        "wikimedia",
        "naver_image",
    ]
    assert route_for(scene("x", 0, 1, kind="photo", keywords_ko=["현장"]), google=True) == [
        "google_image",
        "naver_image",
    ]
    # 결정: Google 키가 없으면 네이버가 1순위
    assert route_for(scene("x", 0, 1, kind="photo", keywords_ko=["국회"]), google=False) == [
        "naver_image",
        "wikimedia",
    ]
    assert route_for(scene("x", 0, 1, kind="video"), google=False) == ["youtube_video", "naver_image"]
    _ = date
