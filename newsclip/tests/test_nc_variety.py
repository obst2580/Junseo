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
