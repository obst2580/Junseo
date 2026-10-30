import json

import httpx
import pytest
import respx

from topicradar import pipeline
from topicradar.collectors.google_news import (
    BATCH_URL,
    GoogleNewsCollector,
    decode_google_link,
    is_google_link,
    split_press,
)
from topicradar.collectors.naver_news import NaverNewsCollector

from trh import kst

GN = """<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel><title>g</title>
<item><title>강아지 심장병, 진단이 먼저 - 바이오타임즈</title><link>https://news.google.com/rss/articles/CBMiAAA?oc=5</link>
<pubDate>Tue, 20 Oct 2026 05:45:07 GMT</pubDate><description>&lt;a href="x"&gt;t&lt;/a&gt;</description>
<source url="https://www.biotimes.co.kr">바이오타임즈</source></item>
<item><title>강아지 심장병, 진단이 먼저 - 다른매체</title><link>https://news.google.com/rss/articles/CBMiBBB?oc=5</link>
<pubDate>Tue, 20 Oct 2026 06:00:00 GMT</pubDate><source url="https://o.example">다른매체</source></item>
<item><title>오래된 기사 - 매체</title><link>https://news.google.com/rss/articles/CBMiCCC</link>
<pubDate>Mon, 12 Oct 2026 05:45:07 GMT</pubDate></item>
</channel></rss>"""


def test_split_press_and_link():
    assert split_press("제목 - 매경헬스") == ("제목", "매경헬스")
    assert split_press("제목만") == ("제목만", "")
    assert is_google_link("https://news.google.com/rss/articles/X") and not is_google_link("https://a.example/1")


@respx.mock
async def test_google_news_collects_and_dedupes(monkeypatch):
    monkeypatch.setattr("topicradar.collectors.google_news.now_kst", lambda: kst("2026-10-20 20:00"))
    respx.get(url__startswith="https://news.google.com/rss/search").mock(return_value=httpx.Response(200, content=GN.encode()))
    async with httpx.AsyncClient() as c:
        sigs = await GoogleNewsCollector(["강아지 심장병"], c).fetch(kst("2026-10-19 00:00"))
    assert len(sigs) == 1  # 같은 제목은 하나, 오래된 기사는 뺌
    s = sigs[0]
    assert s.title == "강아지 심장병, 진단이 먼저" and s.source == "google:강아지 심장병"
    assert s.metric["press"] == "바이오타임즈" and s.summary == ""


@respx.mock
async def test_decode_google_link():
    respx.get(url__startswith="https://news.google.com/rss/articles/CBMiAAA").mock(
        return_value=httpx.Response(200, text='<c-wiz><div jscontroller="x" data-n-a-sg="SIG" data-n-a-ts="123"></div></c-wiz>')
    )
    inner = json.dumps(["garturlres", "https://www.biotimes.co.kr/news/1", 1])
    body = ")]}'\n\n" + json.dumps([["wrb.fr", "Fbv4je", inner, None, None, None, "1"], ["di", 10]])
    route = respx.post(BATCH_URL).mock(return_value=httpx.Response(200, text=body))
    async with httpx.AsyncClient() as c:
        url = await decode_google_link(c, "https://news.google.com/rss/articles/CBMiAAA?oc=5")
    assert url == "https://www.biotimes.co.kr/news/1"
    assert "SIG" in route.calls[0].request.content.decode()


@pytest.mark.parametrize("has_key", [True, False])
def test_news_collector_falls_back_without_naver_key(monkeypatch, has_key):
    if has_key:
        monkeypatch.setenv("NAVER_CLIENT_ID", "id")
        monkeypatch.setenv("NAVER_CLIENT_SECRET", "secret")
    else:
        monkeypatch.delenv("NAVER_CLIENT_ID", raising=False)
        monkeypatch.delenv("NAVER_CLIENT_SECRET", raising=False)
    col = pipeline.news_collector(["q"], object())
    assert isinstance(col, NaverNewsCollector if has_key else GoogleNewsCollector)
