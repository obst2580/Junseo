"""건강 채널(널스윤서) 화면 소재: Pexels·Pixabay 검색, 영상 조각 다듬기, 수치 카드, 쇼츠 영상 컷, 출처 기록."""

import json
import shutil
import subprocess

import httpx
import pytest
import respx
from PIL import Image

from newsclip import cards
from newsclip.download import prepare_clip, probe_video, stock_clip_seconds
from newsclip.license_log import attribution_text, write_credits
from newsclip.models import SceneSet
from newsclip.rank import rank
from newsclip.shorts_render import shots_for
from newsclip.sources import pexels, pixabay
from newsclip.sources.base import SourceUnavailable
from newsclip.vertical import BOX, SIZE, box_of, compose_photo, style_of
from studio_core.config import ChannelConfig

from nch import scene

FFMPEG = shutil.which("ffmpeg") is not None and shutil.which("ffprobe") is not None
HEALTH = ChannelConfig.model_validate(
    {
        "images": {
            "sources": ["pexels_video", "pexels_image", "pixabay_video", "pixabay_image"],
            "orientation": "portrait",
        }
    }
)


def _pexels_video(vid: int, w: int, h: int, dur: float = 9) -> dict:
    return {
        "id": vid,
        "url": f"https://www.pexels.com/video/woman-checking-blood-pressure-{vid}/",
        "duration": dur,
        "user": {"name": "작가"},
        "video_files": [
            {"link": f"https://v/{vid}/4k.mp4", "file_type": "video/mp4", "width": w * 2, "height": h * 2},
            {"link": f"https://v/{vid}/hd.mp4", "file_type": "video/mp4", "width": w, "height": h},
            {"link": f"https://v/{vid}/sd.mp4", "file_type": "video/mp4", "width": w // 2, "height": h // 2},
        ],
    }


def test_pexels_parse_picks_hd_file_and_skips_short_clips():
    vids = pexels.parse_videos(
        {"videos": [_pexels_video(1, 1080, 1920), _pexels_video(2, 1080, 1920, dur=1.5)]}
    )
    assert len(vids) == 1  # 3초보다 짧은 영상은 뺀다
    v = vids[0]
    assert v.download_url.endswith("/hd.mp4") and (v.width, v.height) == (1080, 1920)  # 4K는 피한다
    assert (
        v.provider_id == "1" and v.title == "woman checking blood pressure" and v.license == "Pexels License"
    )
    photos = pexels.parse_photos(
        {
            "photos": [
                {
                    "id": 7,
                    "url": "https://www.pexels.com/photo/x-7/",
                    "alt": "Blood pressure monitor",
                    "width": 3000,
                    "height": 4000,
                    "photographer": "A",
                    "src": {"large2x": "https://p/7.jpg"},
                }
            ]
        }
    )
    assert photos[0].source == "pexels_image" and photos[0].title == "Blood pressure monitor"


def test_pixabay_parse_and_pick():
    hits = pixabay.parse_videos(
        {
            "hits": [
                {
                    "id": 5,
                    "pageURL": "https://pixabay.com/videos/id-5/",
                    "tags": "walk, park, woman",
                    "duration": 12,
                    "user": "B",
                    "videos": {
                        "large": {"url": "https://x/l.mp4", "width": 3840, "height": 2160},
                        "medium": {"url": "https://x/m.mp4", "width": 1920, "height": 1080},
                        "small": {"url": "https://x/s.mp4", "width": 1280, "height": 720},
                    },
                }
            ]
        }
    )
    assert hits[0].download_url == "https://x/m.mp4" and hits[0].title == "walk park woman"


@respx.mock
async def test_pixabay_error_hides_key(monkeypatch):
    monkeypatch.setenv("PIXABAY_API_KEY", "SECRET-KEY-123")
    respx.get(url__startswith=pixabay.VIDEO_API).mock(
        return_value=httpx.Response(400, text="bad SECRET-KEY-123")
    )
    async with httpx.AsyncClient() as c:
        src = pixabay.PixabaySource(c, media="video")
        with pytest.raises(pixabay.PixabayError) as e:
            await src.search("walk", "broll")
    assert "SECRET-KEY-123" not in str(e.value) and "***" in str(e.value)


@respx.mock
async def test_pexels_sends_key_in_header_and_orientation(monkeypatch):
    monkeypatch.setenv("PEXELS_API_KEY", "PKEY")
    route = respx.get(url__startswith=pexels.VIDEO_API).mock(
        return_value=httpx.Response(200, json={"videos": [_pexels_video(3, 1080, 1920)]})
    )
    async with httpx.AsyncClient() as c:
        src = pexels.PexelsSource(c, media="video", orientation="portrait")
        found = await src.search("walking", "broll")
    req = route.calls[0].request
    assert req.headers["Authorization"] == "PKEY" and "PKEY" not in str(req.url)
    assert "orientation=portrait" in str(req.url) and found[0].media == "video"


def test_missing_keys_skip(monkeypatch):
    monkeypatch.delenv("PEXELS_API_KEY", raising=False)
    monkeypatch.delenv("PIXABAY_API_KEY", raising=False)
    for cls in (pexels.PexelsSource, pixabay.PixabaySource):
        with pytest.raises(SourceUnavailable):
            cls(None).check_ready()  # type: ignore[arg-type]


def test_rank_follows_configured_route_and_prefers_portrait():
    sc = scene("s001", 0, 4, query="blood pressure")
    v = pexels.parse_videos({"videos": [_pexels_video(1, 1080, 1920)]})[0]
    land = pixabay.parse_videos(
        {
            "hits": [
                {
                    "id": 9,
                    "pageURL": "https://pixabay.com/videos/id-9/",
                    "tags": "blood pressure",
                    "duration": 10,
                    "videos": {"medium": {"url": "https://x/m.mp4", "width": 1920, "height": 1080}},
                }
            ]
        }
    )[0]
    ranked = rank(sc, [land, v], HEALTH)
    assert ranked[0].source == "pexels_video"  # 설정 순서가 앞 + 세로형 가점
    assert any("세로형 +8" in n for n in ranked[0].score_notes)


def test_attribution_and_credits(tmp_path):
    v = pexels.parse_videos({"videos": [_pexels_video(1, 1080, 1920)]})[0]
    assert attribution_text(v) == "영상: 작가 / Pexels"
    sc = scene("s001", 0, 4)
    sc.candidates = [
        v.model_copy(
            update={"local_path": str(tmp_path / "a.mp4"), "fetched_at": "2026-10-18T09:00:00+09:00"}
        )
    ]
    n = write_credits(tmp_path / "credits.txt", SceneSet(project="p", scenes=[sc]))
    text = (tmp_path / "credits.txt").read_text(encoding="utf-8")
    assert (
        n == 1
        and "Pexels 영상 #1" in text
        and "2026-10-18T09:00:00+09:00" in text
        and "pexels.com/video" in text
    )


def test_health_style_white_top_black_title_sky_emphasis():
    st = style_of("health")
    assert st.text_colors[0] == (17, 17, 17) and st.text_colors[1][2] > 200  # 검정 + 하늘색
    frame = compose_photo(Image.new("RGB", (900, 1600), (200, 50, 50)), SIZE, "health")
    assert frame.getpixel((540, 100)) == (255, 255, 255)  # 위쪽 흰 바탕
    box = box_of("health")
    assert box[3] - box[1] > 1000 and box[1] < BOX[1]  # 거의 정사각형으로 큰 사진 칸
    assert frame.getpixel((540, box[1] - 3))[2] > 200  # 제목 띠와 사진 칸 사이 하늘색 선
    assert frame.getpixel((10, box[1] + 20))[0] > 150  # 세로 사진도 칸을 꽉 채운다(검은 옆 띠 없음)
    assert frame.getpixel((540, 1800)) == (0, 0, 0)  # 칸 아래는 검정


def test_cards_render_and_skip_back_to_back(tmp_path):
    fonts = {"Regular": "", "Bold": "", "ExtraBold": ""}
    specs = {
        "s003": {
            "layout": "table",
            "title": "고혈압 기준",
            "cols": ["어디서", "기준"],
            "rows": [["집", "135/85"]],
            "hi": 0,
        },
        "s004": {"layout": "number", "value": "140/90", "unit": "mmHg"},  # 바로 앞 장면에 카드 → 건너뜀
        "s006": {
            "layout": "bars",
            "title": "하루 걸음",
            "items": [{"label": "A", "value": 3000}, {"label": "B", "value": 7000}],
        },
        "s008": {
            "layout": "compare",
            "left": {"label": "전", "value": "150"},
            "right": {"label": "후", "value": "135"},
        },
    }
    made = cards.render_cards(specs, fonts, tmp_path, SIZE, "health")
    assert sorted(made) == ["s003", "s006", "s008"]
    path, focus = made["s003"]
    with Image.open(path) as im:
        assert im.size == SIZE and im.getpixel((540, box_of("health")[1] + 100)) != (
            0,
            0,
            0,
        )  # 카드는 사진 칸에
    assert 0.1 <= focus[1] <= 0.9  # 강조한 줄 쪽으로 다가갈 자리


def test_shots_use_video_clips_without_zoom(tmp_path):
    (tmp_path / "assets").mkdir()
    for name in ("s001_1.mp4", "s001_2.jpg"):
        (tmp_path / "assets" / name).write_bytes(b"x")
    scenes = [
        {
            "id": "s001",
            "start": 0,
            "end": 5.2,
            "topic": "t",
            "chosen": "assets/s001_1.mp4",
            "files": ["assets/s001_1.mp4", "assets/s001_2.jpg"],
        }
    ]
    shots = shots_for(scenes, tmp_path, 5.2, {}, cuts=[2.6])
    assert [s.kind for s in shots] == ["video", "image"] and shots[0].in_sec == 0.0


def test_clip_seconds():
    assert (
        stock_clip_seconds(0.5) == 2.0 and stock_clip_seconds(4.0) == 4.6 and stock_clip_seconds(60) == 20.0
    )


@pytest.mark.skipif(not FFMPEG, reason="ffmpeg 필요")
@pytest.mark.parametrize("src_size", [(1280, 720), (720, 1280)])
def test_prepare_clip_vertical_no_audio_and_length(tmp_path, src_size):
    src = tmp_path / "src.mp4"
    w, h = src_size
    subprocess.run(
        [
            "ffmpeg",
            "-loglevel",
            "error",
            "-y",
            "-f",
            "lavfi",
            "-i",
            f"testsrc2=size={w}x{h}:rate=25:duration=1.5",
            "-f",
            "lavfi",
            "-i",
            "sine=duration=1.5",
            "-shortest",
            "-pix_fmt",
            "yuv420p",
            str(src),
        ],
        check=True,
    )
    out = tmp_path / "assets" / "s001_1.mp4"
    assert prepare_clip(src, out, 3.0, SIZE, "health") == (w, h)
    dur, ow, oh = probe_video(out)
    assert (ow, oh) == SIZE and abs(dur - 3.0) < 0.15  # 짧은 원본은 되풀이해 장면 길이를 채운다
    streams = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "stream=codec_type", "-of", "json", str(out)],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    assert [s["codec_type"] for s in json.loads(streams)["streams"]] == ["video"]  # 원래 소리 없음
    subprocess.run(
        [
            "ffmpeg",
            "-loglevel",
            "error",
            "-y",
            "-ss",
            "1",
            "-i",
            str(out),
            "-frames:v",
            "1",
            str(tmp_path / "f.png"),
        ],
        check=True,
    )
    with Image.open(tmp_path / "f.png") as im:
        assert im.getpixel((540, 100))[0] > 240  # 디자인 바탕(흰 위쪽)


def test_chart_hook_lines_key_and_frames():
    from newsclip.chart_hook import ChartHook, layout, split_lines

    assert split_lines("혈압, 아무 때나 재고 계시죠?") == ["혈압, 아무 때나", "재고 계시죠?"]
    assert split_lines("혈압 재세요") == ["혈압 재세요"]  # 짧으면 한 줄
    _font, words = layout(["혈압, 아무 때나", "재고 계시죠?"], "아무 때나")
    assert [w.text for w in words if w.red] == ["아무", "때나"]
    hook = ChartHook(["혈압, 아무 때나", "재고 계시죠?"], "아무 때나", [0.1, 0.8, 1.0, 1.4, 1.7], 2.5, SIZE)
    assert hook.punch_at == 0.8 and hook.circle_at == pytest.approx(1.0 + 0.18)
    box = box_of("health")
    early, written = hook.frame(0.05), hook.frame(0.6)
    assert early.size == SIZE and early.getpixel((540, 100)) == (255, 255, 255)
    first = (box[0] + 100, box[1] + 330, box[0] + 400, box[1] + 520)  # '혈압,' 자리(줌 컷 전)

    def ink(im):
        return sum(1 for p in im.crop(first).getdata() if sum(p) < 200)

    assert ink(written) > ink(early) + 300  # 시간이 지나면 글씨가 써진다
    red = lambda im: sum(1 for p in im.getdata() if p[0] > 180 and p[1] < 80 and p[2] < 80)  # noqa: E731
    assert red(hook.frame(1.6)) > 3 * red(hook.frame(0.79)) + 1000  # 핵심 말에서 줌 컷 → 빨간 글씨가 크게
