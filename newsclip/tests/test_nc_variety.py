"""장면마다 다른 자료: 장면 검색어 나누기, 앞 장면에서 쓴 자료 감점, 영상 장면·영상 후보(링크)."""

from newsclip.models import Asset, asset_id
from newsclip.pipeline import RunReport, gather_candidates, query_for, topic_query
from newsclip.rank import rank
from newsclip.scenes import build_scenes, mark_video_scenes, noun_tokens
from newsclip.srt import Cue
from studio_core.config import ChannelConfig

from nch import asset, scene

SCRIPT = """## 대통령 SNS 공방
국민의힘이 대통령 SNS 메시지를 두고 비판했습니다.
대통령은 엑스에 가짜뉴스 유포를 하지 말자고 썼습니다.
폭언과 욕설은 이미 패배라고 했습니다.
계정 차단은 무뢰배에게만 한다고 밝혔습니다.
비판하는 손님은 막지 않는다고 했습니다.
명절 잔소리 같은 이야기라는 반응도 나옵니다.
"""


def _cues() -> list[Cue]:
    lines = [ln for ln in SCRIPT.splitlines() if ln and not ln.startswith("#")]
    return [Cue(i + 1, i * 5.0, i * 5.0 + 4.8, ln) for i, ln in enumerate(lines)]


def test_scenes_get_their_own_search_words():
    scenes = build_scenes(_cues(), SCRIPT)
    assert len(scenes) >= 3
    assert len({tuple(sc.topic_keywords) for sc in scenes}) == 1 and scenes[0].topic_keywords
    queries = [query_for(sc, "naver_image") for sc in scenes]
    assert len(set(queries)) == len(queries)  # 장면마다 다른 사진 검색어
    assert {query_for(sc, "youtube_video") for sc in scenes} == {
        topic_query(scenes[0])
    }  # 영상은 주제 단위(캐시)
    assert noun_tokens("말씀드리면 따지고 했고요 솔직히 가짜뉴스 메시지 장면") == [
        "가짜뉴스",
        "메시지",
        "장면",
    ]


def test_mark_video_scenes_every_third():
    group = [scene(f"s{i:03d}", i, i + 1, kind="photo", topic="A") for i in range(7)]
    group[4].kind = "video"
    mark_video_scenes(group, every=3)
    assert [sc.kind for sc in group] == ["video", "photo", "photo", "photo", "video", "photo", "video"]


class _Src:
    def __init__(self, results: dict[str, list[Asset]]):
        self.results, self.queries = results, []

    async def search(self, query, kind):
        self.queries.append(query)
        return self.results.get(query, [])


def _img(n: int) -> Asset:
    return asset("naver_image", f"https://img.example.com/{n}.jpg")


def _vid(n: int) -> Asset:
    url = f"https://www.youtube.com/watch?v=v{n}"
    return Asset(source="youtube_video", id=asset_id("youtube_video", url), url=url, media="video")


async def test_videos_do_not_count_and_topic_query_fills_up(monkeypatch):
    monkeypatch.delenv("GOOGLE_API_KEY", raising=False)
    sc = scene(
        "s001", 0, 5, kind="video", topic_keywords=["대통령", "sns"], keywords_ko=["가짜뉴스", "대통령"]
    )
    yt = _Src({"대통령 sns": [_vid(1), _vid(2)]})
    nv = _Src({"대통령 가짜뉴스": [_img(1)], "대통령 sns": [_img(2), _img(3), _img(4)]})
    out = await gather_candidates(sc, {"youtube_video": yt, "naver_image": nv}, RunReport(), want=4)
    assert yt.queries == ["대통령 sns"] and nv.queries == ["대통령 가짜뉴스", "대통령 sns"]
    assert sum(a.media == "video" for a in out) == 2 and sum(a.media == "image" for a in out) == 4


def test_rank_penalizes_assets_used_by_earlier_scenes():
    cfg = ChannelConfig()
    a, b = _img(1), _img(2)
    first = rank(scene("s001", 0, 5), [a, b], cfg)[0]
    again = rank(scene("s002", 5, 9), [a, b], cfg, used={first.id: 1})
    assert again[0].id != first.id and any("앞 장면에서 이미 씀" in n for x in again for n in x.score_notes)


async def test_queries_from_reading_the_script(monkeypatch):
    """대사를 읽고 정한 검색어: 사진은 장면 검색어, 영상은 주제 검색어. 비유로 든 말 대신 실제 대상을 찾는다."""
    from newsclip.pipeline import apply_queries

    monkeypatch.delenv("GOOGLE_API_KEY", raising=False)
    sc = scene("s008", 0, 5, kind="photo", topic="SNS 공방", keywords_ko=["명절", "잔소리"])
    apply_queries(
        [sc],
        {
            "scenes": {"s008": {"q": "이재명 대통령 엑스 게시글", "video": True}},
            "videos": {"SNS 공방": "이재명 SNS 소통"},
        },
    )
    assert sc.query == "이재명 대통령 엑스 게시글" and sc.kind == "video" and sc.keywords_ko[0] == "이재명"
    yt = _Src({"이재명 SNS 소통": [_vid(1)]})
    nv = _Src({"이재명 대통령 엑스 게시글": [_img(1), _img(2)]})
    await gather_candidates(sc, {"youtube_video": yt, "naver_image": nv}, RunReport(), want=2)
    assert yt.queries == ["이재명 SNS 소통"] and nv.queries == ["이재명 대통령 엑스 게시글"]


def test_empty_scene_borrows_neighbor_photo_and_scoring_prefers_matching_news_photo(monkeypatch):
    from newsclip.pipeline import borrow_empty

    monkeypatch.setenv("STUDIO_NOW", "2026-10-12T20:00")
    a, b, c = scene("s001", 0, 5), scene("s002", 5, 9), scene("s003", 9, 12)
    a.chosen = asset(url="https://img.example.com/a.jpg", local_path="/p/s001_1.jpg")
    c.chosen = asset(url="https://img.example.com/c.jpg", local_path="/p/s003_1.jpg")
    first_empty = scene("s000", 0, 1)
    assert borrow_empty([first_empty, a, b, c]) == 2
    assert b.borrowed == "s001" and b.chosen.local_path == "/p/s001_1.jpg"  # 앞 장면 사진
    assert first_empty.borrowed == "s001"  # 앞이 없으면 뒤 장면

    sc = scene("s010", 0, 5, kind="photo")
    sc.query = "박지원 국정감사 질의"
    news = asset(
        url="https://imgnews.pstatic.net/image/003/2026/10/12/n.jpg",
        title="국정감사 질의하는 박지원 의원",
        source_domain="imgnews.pstatic.net",
        width=700,
        height=467,
    )
    big_doc = asset(
        url="https://lh7-us.googleusercontent.com/docs/x",
        title="대법원 특수활동비 지급내역 - Google Sheets",
        source_domain="lh7-us.googleusercontent.com",
        width=1920,
        height=1080,
    )
    big_other = asset(url="https://img.example.com/o.jpg", title="가을 풍경", width=1920, height=1080)
    ranked = rank(sc, [big_doc, big_other, news], ChannelConfig())
    assert ranked[0].url == news.url and ranked[-1].url == big_doc.url
    assert any("검색어 3개 제목 일치" in n for n in ranked[0].score_notes)
