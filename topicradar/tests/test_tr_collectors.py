import httpx
import pytest
import respx

from studio_core.config import FeedSpec
from topicradar.collectors.base import Collector, CollectorDisabled, collect_all
from topicradar.collectors.naver_news import NaverNewsCollector, parse_news
from topicradar.collectors.overseas import OverseasCollector
from topicradar.collectors.rss import RssCollector, parse_feed
from topicradar.collectors.schedule import (
    AssemblyScheduleCollector,
    ManualScheduleCollector,
    parse_schedule_file,
)
from topicradar.collectors.trends import NaverDataLabTrends
from topicradar.collectors.youtube import YouTubeCollector, uploads_playlist

from trh import kst

RSS = """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>t</title>
<item><title>[속보] 법사위, 법안 의결</title><link>https://n.example/1</link>
<description>&lt;p&gt;법사위가 7일 법안을 의결했다.&lt;/p&gt;</description><pubDate>Wed, 07 Oct 2026 11:20:00 +0900</pubDate></item>
<item><title>옛 기사</title><link>https://n.example/2</link><pubDate>Mon, 05 Oct 2026 09:00:00 +0900</pubDate></item>
<item><title>날짜 없음</title><link>https://n.example/3</link></item>
</channel></rss>"""
ATOM = """<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"><title>a</title>
<entry><title>Senate passes bill</title><link href="https://a.example/1"/><updated>2026-10-06T13:30:00Z</updated>
<summary>The Senate passed it.</summary></entry></feed>"""


def test_parse_feed_rss_and_atom():
    feed = FeedSpec(name="연합", url="u", kind="domestic", category="국회")
    sigs = parse_feed(RSS.encode(), feed, kst("2026-10-07 12:00"))
    assert [s.title for s in sigs] == ["법사위, 법안 의결", "옛 기사"]
    assert sigs[0].summary == "법사위가 7일 법안을 의결했다." and sigs[0].metric["category_hint"] == "국회"
    atom = parse_feed(ATOM.encode(), FeedSpec(name="bbc", url="u", kind="overseas"), kst("2026-10-07 12:00"))
    assert atom[0].published_at == kst("2026-10-06 22:30") and atom[0].kind == "overseas"


@respx.mock
async def test_rss_collector_isolates_feed_failure():
    respx.get("https://ok.example/rss").mock(return_value=httpx.Response(200, content=RSS.encode()))
    respx.get("https://bad.example/rss").mock(return_value=httpx.Response(500))
    feeds = [
        FeedSpec(name="ok", url="https://ok.example/rss"),
        FeedSpec(name="bad", url="https://bad.example/rss"),
    ]
    async with httpx.AsyncClient() as c:
        col = RssCollector(feeds, c)
        sigs = await col.fetch(kst("2026-10-06 00:00"))
        assert [s.title for s in sigs] == ["법사위, 법안 의결"] and "bad" in col.feed_errors
        ov = await OverseasCollector([FeedSpec(name="ok", url="https://ok.example/rss")], c).fetch(
            kst("2026-10-06 00:00")
        )
        assert ov[0].kind == "overseas"


NAVER = {
    "items": [
        {
            "title": "<b>국회</b> 본회의 가결",
            "originallink": "https://o.example/1",
            "link": "https://n.naver/1",
            "description": "국회가 &quot;법안&quot;을 가결했다.",
            "pubDate": "Thu, 08 Oct 2026 14:50:00 +0900",
        },
        {
            "title": "깨진 날짜",
            "originallink": "https://o.example/2",
            "link": "",
            "description": "",
            "pubDate": "x",
        },
    ]
}


def test_parse_news():
    sigs = parse_news(NAVER, "국회", kst("2026-10-08 15:00"))
    assert len(sigs) == 1 and sigs[0].url == "https://o.example/1" and sigs[0].title == "국회 본회의 가결"
    assert '"법안"' in sigs[0].summary and sigs[0].metric["query"] == "국회"


@respx.mock
async def test_naver_collector_headers(monkeypatch):
    monkeypatch.setenv("NAVER_CLIENT_ID", "id1")
    monkeypatch.setenv("NAVER_CLIENT_SECRET", "sec1")
    route = respx.get("https://openapi.naver.com/v1/search/news.json").mock(
        return_value=httpx.Response(200, json=NAVER)
    )
    async with httpx.AsyncClient() as c:
        sigs = await NaverNewsCollector(["국회"], c).fetch(kst("2026-10-08 00:00"))
    assert len(sigs) == 1
    req = route.calls[0].request
    assert req.headers["X-Naver-Client-Id"] == "id1" and req.url.params["sort"] == "date"


async def test_naver_collector_disabled_without_keys(monkeypatch):
    monkeypatch.delenv("NAVER_CLIENT_ID", raising=False)
    async with httpx.AsyncClient() as c:
        rep = await collect_all([NaverNewsCollector(["국회"], c)], kst("2026-10-08 00:00"))
    assert "naver_news" in rep.skipped and not rep.errors


@respx.mock
async def test_youtube_collector(monkeypatch):
    monkeypatch.setenv("YOUTUBE_API_KEY", "k")
    assert uploads_playlist("UCabc") == "UUabc"
    respx.get("https://www.googleapis.com/youtube/v3/playlistItems").mock(
        return_value=httpx.Response(
            200,
            json={
                "items": [
                    {"contentDetails": {"videoId": "v1", "videoPublishedAt": "2026-10-07T09:00:00Z"}},
                    {"contentDetails": {"videoId": "old", "videoPublishedAt": "2026-09-01T00:00:00Z"}},
                ]
            },
        )
    )
    respx.get("https://www.googleapis.com/youtube/v3/videos").mock(
        return_value=httpx.Response(
            200,
            json={
                "items": [
                    {
                        "id": "v1",
                        "snippet": {
                            "title": "막말 설전 정리",
                            "description": "d",
                            "channelId": "UCabc",
                            "channelTitle": "경쟁A",
                            "publishedAt": "2026-10-07T09:00:00Z",
                        },
                        "statistics": {"viewCount": "100", "likeCount": "3"},
                    }
                ]
            },
        )
    )
    async with httpx.AsyncClient() as c:
        sigs = await YouTubeCollector(["UCabc"], c).fetch(kst("2026-10-07 00:00"))
        assert len(sigs) == 1 and sigs[0].kind == "competitor" and sigs[0].metric["views"] == 100
        with pytest.raises(CollectorDisabled):
            await YouTubeCollector([], c).fetch(kst("2026-10-07 00:00"))


async def test_collect_all_isolation_and_dedupe():
    class Ok(Collector):
        name = "ok"

        async def fetch(self, since):
            from trh import sig

            return [
                sig("a", "2026-10-08 10:00", url="https://same"),
                sig("b", "2026-10-08 10:00", url="https://same"),
            ]

    class Boom(Collector):
        name = "boom"

        async def fetch(self, since):
            raise RuntimeError("x")

    rep = await collect_all([Ok(), Boom()], kst("2026-10-08 00:00"))
    assert len(rep.signals) == 1 and "boom" in rep.errors and rep.counts["ok"] == 2


async def test_schedule_yaml(tmp_path, monkeypatch):
    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T05:00")
    (tmp_path / "2026-10-08.yaml").write_text(
        "- time: '14:00'\n  title: 본회의 표결\n  category: 국회\n  expected: 가결 전망\n- title: 종일 일정\n- note: 제목없음\n",
        encoding="utf-8",
    )
    sigs = parse_schedule_file(tmp_path / "2026-10-08.yaml", kst("2026-10-08 00:00").date())
    assert len(sigs) == 2 and sigs[0].metric["scheduled_at"].startswith("2026-10-08T14:00")
    assert sigs[0].published_at == kst("2026-10-07 18:00") and sigs[1].metric["all_day"] is True
    got = await ManualScheduleCollector(tmp_path).fetch(kst("2026-10-07 00:00"))
    assert len(got) == 2
    with pytest.raises(NotImplementedError):
        await AssemblyScheduleCollector().fetch(kst("2026-10-07 00:00"))
    with pytest.raises(NotImplementedError):
        await NaverDataLabTrends().rise_ratio(
            ["a"], kst("2026-10-07 00:00").date(), kst("2026-10-08 00:00").date()
        )
