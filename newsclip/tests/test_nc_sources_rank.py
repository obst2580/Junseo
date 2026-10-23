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


def test_rank_skips_photos_of_other_people_abroad_and_sns_events():
    """뉴스 채널: 사진 주인공이 장면에 없는 사람(李 대통령 사진에 추미애 발언), 장면과 상관없는 얼굴이 섞인 합성,
    외국 사진, 'SNS'가 글자로만 맞은 행사 사진은 뒤로. 소재 사이트만 쓰는 채널(널스윤서)은 예전 그대로."""
    from newsclip.rank import title_people

    assert title_people("李 “소방관에 천금 준들 아깝겠나”…추미애 ‘재정 빨대’ 발언 저격?") == ["이재명"]
    assert title_people("[경기도지사 후보 지지도] 추미애 27.0% - 김동연 21.2% - 한준호 17.2%") == [
        "추미애",
        "김동연",
        "한준호",
    ]
    assert title_people("발언하는 장동혁 국민의힘 대표") == ["장동혁"]
    assert title_people("순직 소방관 빈소 찾은 추미애 지사") == ["추미애"]
    assert title_people("[포토] 더불어민주당 최고위원회의") == []

    def pick(sc, titles):
        assets = [
            asset(
                "naver_image",
                f"https://i/{k}.jpg",
                width=1600,
                height=1200,
                title=t,
                source_domain="imgnews.naver.net",
            )
            for k, t in enumerate(titles)
        ]
        return [a.title for a in rank(sc, assets, CFG)]

    quote = scene("s003", 0, 3, text='"소방이 재정 빨대 같다"고 했는데요,', query="추미애 재정 빨대 발언")
    lee = "李 “소방관에 천금 준들 아깝겠나”…추미애 ‘재정 빨대’ 발언 저격?"
    choo = "추미애 경기도지사 ‘재정 빨대’ 발언 해명"
    assert pick(quote, [lee, choo])[0] == choo
    # 장면에 '대통령'이 나오면 대통령 사진은 그대로
    pres = scene("s013", 0, 3, text="대통령과 정부의 개혁에 힘을 모아야 할 때", query="대통령 국무회의")
    assert (
        pick(pres, ["국무회의 발언하는 이재명 대통령", "국무회의 자료"])[0]
        == "국무회의 발언하는 이재명 대통령"
    )
    poll = "[경기도지사 후보 지지도] 추미애 27.0% - 김동연 21.2% - 한준호 17.2%"
    solo = "국정감사 답변하는 추미애 경기도지사"
    assert (
        pick(scene("s014", 0, 3, text="스스로 만든 악재입니다.", query="추미애 경기도지사"), [poll, solo])[0]
        == solo
    )
    us = "미 워싱턴 DC에 위치한 미국 국회의사당 전경."
    kr = "여의도 국회의사당 전경"
    assert pick(scene("s017", 0, 3, text="구독과 좋아요", query="국회의사당 전경"), [us, kr])[0] == kr
    sns = "[포토] 아동청소년 SNS 중독 좌담회서 발언하는 장동혁 대표"
    plain = "발언하는 장동혁 국민의힘 대표"
    out = pick(
        scene("s010", 0, 3, text='"닷새 만에 등 떠밀려 내놓은 사과",', query="장동혁 대표 SNS"), [sns, plain]
    )
    assert out[0] == plain
    # 소재 사이트만 쓰는 채널은 이 규칙을 쓰지 않는다
    stock = ChannelConfig.model_validate({"images": {"sources": ["naver_image"]}})
    a = asset("naver_image", "https://i/lee.jpg", width=1600, height=1200, title=lee)
    notes = rank(quote, [a], stock)[0].score_notes
    assert not any("장면에 없음" in n or "외국" in n for n in notes)


def test_rank_skips_strangers_graphics_party_shots_and_dropped():
    """10/23 오세훈 무죄 영상에서 나온 실수: '김건희 특검' 검색어에 대본에 없는 김건희 여사 사진, 녹취 공개
    합성 썸네일, '…기자회견 갖는 민주당'(사진은 민주당 사람들)이 뽑혔다. 작업자가 뺀 사진(drop)은 아예 안 쓴다."""
    from newsclip.rank import title_people

    assert title_people("특검 사무실 향하는 김건희 여사") == ["김건희"]
    assert title_people("지팡이 짚고 공판 출석하는 명태균 씨") == ["명태균"]
    assert title_people("동네 아저씨 인터뷰") == []

    def pick(sc, titles, story="", drop=()):
        assets = [
            asset(
                "naver_image",
                f"https://i/{k}.jpg",
                width=1600,
                height=1200,
                title=t,
                source_domain="imgnews.naver.net",
            )
            for k, t in enumerate(titles)
        ]
        return [a.title for a in rank(sc, assets, CFG, story=story, drop=drop)]

    story = "오세훈 서울시장이 2심에서 무죄를 받았습니다. 명태균 씨 진술이 흔들렸고 특검은 상고해야 합니다."
    tk = scene("s015", 0, 3, text="특검은 상고해서 끝까지 가야 합니다.", query="김건희 특검 사무실")
    kim = "특검 사무실 향하는 김건희 여사"
    office = "김건희 특검 사무실 현판"
    assert pick(tk, [kim, office], story)[0] == office
    assert pick(tk, [kim, office])[0] == kim  # 대본을 안 주면 예전 그대로
    ms = scene("s005", 0, 3, text="명태균 씨 진술까지 배척했고,", query="명태균 씨")
    tape = '민주 "명태균, 유튜버 부탁으로 여의도연구원 인사청탁" 녹취 공개'
    court = "법정 출석하는 명태균"
    assert pick(ms, [tape, court], story)[0] == court
    oh = scene("s033", 0, 3, text="오 시장 한 사람만의 일이 아니라서", query="오세훈 서울시장 기자회견")
    dem = "오세훈 시장의 정책 변화 촉구 기자회견 갖는 민주당"
    own = "기자회견 하는 오세훈 서울시장"
    assert pick(oh, [dem, own], story)[0] == own
    assert pick(ms, [court, tape], story, drop=("https://i/0.jpg",)) == [tape]
    assert pick(ms, [court, tape], story, drop=("출석하는 명태균",)) == [tape]


def test_watermark_stock_sites_are_never_used():
    """'크라우드픽'처럼 미리보기에 워터마크를 박는 유료 사진 사이트는 채널 설정과 상관없이 뺀다."""
    from newsclip.rank import score_asset

    sc = scene("s001", 0, 3)
    cfg = ChannelConfig()
    bad = asset(url="https://cdn.crowdpic.net/detail-thumb/x.jpg", source_domain="cdn.crowdpic.net")
    getty = asset(url="https://media.gettyimages.com/id/1.jpg", source_domain="news.example.com")
    ok = asset(url="https://imgnews.pstatic.net/a.jpg", source_domain="imgnews.naver.net")
    assert score_asset(bad, sc, ["naver_image"], cfg) is None
    assert score_asset(getty, sc, ["naver_image"], cfg) is None  # 주소가 워터마크 사이트여도
    assert score_asset(ok, sc, ["naver_image"], cfg) is not None


def test_document_captures_are_detected(tmp_path):
    """흰 바탕 잔글씨(문서·표·화면 캡처)는 걸리고, 현수막이 있는 행사 사진 같은 보통 사진은 안 걸린다."""
    import numpy as np
    from PIL import Image, ImageDraw

    from newsclip.download import DOC_ROWS, doc_text_rows

    doc = Image.new("RGB", (800, 1000), "white")
    d = ImageDraw.Draw(doc)
    for y in range(40, 960, 22):  # 줄마다 잔글씨처럼 짧은 검은 조각
        for x in range(30, 760, 16):
            d.rectangle((x, y, x + 4, y + 10), fill="black")
    doc.save(tmp_path / "doc.png")
    rng = np.random.default_rng(0)
    photo = Image.fromarray(rng.integers(0, 200, (600, 900, 3), dtype=np.uint8))
    ImageDraw.Draw(photo).rectangle((100, 50, 800, 150), fill="white")  # 현수막
    photo.save(tmp_path / "photo.png")
    hit = lambda r, w: any(r >= a and w >= b for a, b in DOC_ROWS)  # noqa: E731
    assert hit(*doc_text_rows(tmp_path / "doc.png"))
    assert not hit(*doc_text_rows(tmp_path / "photo.png"))
