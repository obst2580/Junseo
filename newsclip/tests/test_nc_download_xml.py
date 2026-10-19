import io
import os
import re
import wave
from pathlib import Path

import httpx
import pytest
import respx
from PIL import Image

from newsclip.download import DedupeRegistry, download_image, download_video, in_point, section_for, ytdlp_cmd
from newsclip.fcpxml import build_xmeml, ease_out_keyframes, path_url, plan_segments, validate_xmeml
from newsclip.license_log import attribution_text, write_assets_json

from nch import asset, scene, sset

SNAP = Path(__file__).parent / "snapshots" / "timeline.xml"


def _jpeg(w=2400, h=1600) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", (w, h), (10, 20, 30)).save(buf, "JPEG")
    return buf.getvalue()


def test_ytdlp_command_and_sections():
    a = asset(
        "youtube_video",
        "https://www.youtube.com/watch?v=x",
        media="video",
        clip_start_sec=30,
        duration_sec=40,
    )
    assert section_for(a, 6.0, 20) == (28.0, 38.0)
    assert section_for(a, 60.0, 20) == (28.0, 40.0)  # 원본 길이로 자름
    early = a.model_copy(update={"clip_start_sec": 1.0})
    assert section_for(early, 5, 20)[0] == 0.0 and in_point(early) == 1.0 and in_point(a) == 2.0
    cmd = ytdlp_cmd(a.url, 28.0, 38.0, Path("/tmp/o.mp4"))
    joined = " ".join(cmd)
    assert "--download-sections *28.00-38.00" in joined and "--force-keyframes-at-cuts" in cmd
    assert (
        "bv*[height<=1080][ext=mp4]+ba[ext=m4a]" in joined
        and cmd[-1] == a.url
        and "--merge-output-format mp4" in joined
    )


@respx.mock
async def test_download_image_normalizes_and_dedupes(tmp_path):
    data = _jpeg()
    respx.get("https://img.example.com/a.jpg").mock(return_value=httpx.Response(200, content=data))
    respx.get("https://img.example.com/b.jpg").mock(return_value=httpx.Response(200, content=data))
    respx.get("https://img.example.com/bad.jpg").mock(
        return_value=httpx.Response(200, content=b"not an image")
    )
    reg, sem = DedupeRegistry(), __import__("asyncio").Semaphore(5)
    async with httpx.AsyncClient() as c:
        a = await download_image(
            c, asset(url="https://img.example.com/a.jpg"), tmp_path / "s001_1.jpg", reg, (1920, 1080), sem
        )
        b = await download_image(
            c, asset(url="https://img.example.com/b.jpg"), tmp_path / "s002_1.jpg", reg, (1920, 1080), sem
        )
        bad = await download_image(
            c, asset(url="https://img.example.com/bad.jpg"), tmp_path / "s003_1.jpg", reg, (1920, 1080), sem
        )
    assert Image.open(a.local_path).size == (1920, 1080) and a.width == 2400
    assert b.local_path == a.local_path and b.sha256 == a.sha256  # 같은 바이트는 한 파일만
    assert bad is None


async def test_download_video_fake(tmp_path):
    a = asset(
        "youtube_video",
        "https://www.youtube.com/watch?v=x",
        media="video",
        clip_start_sec=10,
        duration_sec=300,
    )
    got = await download_video(
        a, tmp_path / "s001_1.mp4", 5.0, 20.0, DedupeRegistry(), __import__("asyncio").Semaphore(1)
    )
    assert (
        got is not None
        and Path(got.local_path).exists()
        and got.duration_sec == 9.0
        and got.source_duration_sec == 300
    )


def _local_assets(tmp_path):
    img = tmp_path / "assets" / "s001_1.jpg"
    img.parent.mkdir(parents=True, exist_ok=True)
    img.write_bytes(_jpeg(1920, 1080))
    vid = tmp_path / "assets" / "s002_1.mp4"
    vid.write_bytes(b"\x00")
    a_img = asset(url="https://img/1.jpg", local_path=str(img), source_domain="assembly.go.kr")
    a_img2 = asset(url="https://img/2.jpg", local_path=str(img), source_domain="korea.kr")
    a_vid = asset(
        "youtube_video",
        "https://y/1",
        media="video",
        local_path=str(vid),
        duration_sec=7.0,
        clip_start_sec=10,
        author_or_channel="KTV 국민방송",
        width=1920,
        height=1080,
    )
    for a in (a_img, a_img2, a_vid):
        a.attribution_text = attribution_text(a)
    return a_img, a_img2, a_vid


def _set(tmp_path):
    a_img, a_img2, a_vid = _local_assets(tmp_path)
    s1 = scene("s001", 0.0, 4.0, candidates=[a_img], chosen=a_img)
    s2 = scene(
        "s002", 4.0, 12.0, kind="video", candidates=[a_vid, a_img2], chosen=a_vid
    )  # 영상 5초 + 사진으로 채움
    s3 = scene("s003", 12.0, 16.0, candidates=[], keywords_ko=["자료", "없음"])
    s4 = scene("s004", 16.0, 20.0, candidates=[a_img], chosen=a_img, skipped=True)
    s5 = scene("s005", 20.0, 24.0, candidates=[a_img2], chosen=a_img2)
    return sset([s1, s2, s3, s4, s5])


def test_plan_segments_fill_and_markers(tmp_path):
    segs, marks = plan_segments(_set(tmp_path), video_max_sec=20)
    spans = [(s.scene.id, s.asset.media, s.start, s.end) for s in segs]
    assert spans[1] == ("s002", "video", 4.0, 9.0) and spans[2] == ("s002", "image", 9.0, 12.0)
    assert {m.name for m in marks} == {"자료 없음", "건너뛰기"}
    assert any("자료 없음: 자료, 없음" == m.comment for m in marks)


def _norm(xml: str, root: Path) -> str:
    return xml.replace(path_url(root).removeprefix("file://localhost"), "{ROOT}").replace(str(root), "{ROOT}")


def test_fcpxml_snapshot_and_validation(tmp_path):
    s = _set(tmp_path)
    wav = tmp_path / "narration.wav"
    with wave.open(str(wav), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(48000)
        w.writeframes(b"\x00\x00" * 48000 * 25)
    xml = build_xmeml(s, tmp_path, fps=30, size=(1920, 1080), zoom=1.04, audio=wav)
    assert validate_xmeml(xml) == []
    assert xml.count("<clipitem") == 2 * 4 + 1  # V1 4 + V2 4 + A1 1
    assert "<name>Basic Motion</name>" in xml and "<marker>" in xml
    scales = re.findall(r"<parameterid>scale</parameterid>.*?<value>([\d.]+)</value>", xml, re.S)
    assert scales[0] == "100.0" and scales[1] == "104.0"  # 1번 장면 줌인, 2번 장면(사진 채움) 줌아웃
    got = _norm(xml, tmp_path)
    if os.environ.get("UPDATE_SNAPSHOTS") or not SNAP.exists():
        SNAP.parent.mkdir(exist_ok=True)
        SNAP.write_text(got, encoding="utf-8")
    assert got == SNAP.read_text(encoding="utf-8")


def test_validate_detects_problems():
    bad = """<?xml version="1.0"?><!DOCTYPE xmeml><xmeml version="5"><sequence><duration>10</duration><media><video><track>
<clipitem id="a"><start>0</start><end>8</end><in>0</in><out>8</out><file id="f1"><pathurl>x</pathurl></file></clipitem>
<clipitem id="b"><start>5</start><end>12</end><in>0</in><out>3</out><file id="f2"/></clipitem>
</track></video></media><marker><name>m</name><in>99</in></marker></sequence></xmeml>"""
    errs = " | ".join(validate_xmeml(bad))
    for frag in ("겹침", "불일치", "시퀀스 길이 초과", "정의되지 않은 file", "마커 위치"):
        assert frag in errs
    assert validate_xmeml("<not xml") and "sequence 없음" in validate_xmeml("<xmeml version='5'/>")[0]


def test_ease_out_and_attribution(tmp_path):
    kf = ease_out_keyframes(100, 104, 120)
    assert kf[0] == (0, 100.0) and kf[-1] == (120, 104.0)
    deltas = [b[1] - a[1] for a, b in zip(kf, kf[1:], strict=False)]
    assert deltas == sorted(deltas, reverse=True)  # ease-out: 처음 빠르고 끝에서 느림
    assert (
        attribution_text(asset("wikimedia", author_or_channel="홍길동", license="CC BY 4.0"))
        == "사진: 홍길동 / Wikimedia Commons / CC BY 4.0"
    )
    assert attribution_text(asset(source_domain="korea.kr")) == "출처: korea.kr"
    assert (
        attribution_text(asset("youtube_video", media="video", author_or_channel="KTV 국민방송"))
        == "출처: KTV 국민방송 (YouTube)"
    )
    s = _set(tmp_path)
    write_assets_json(tmp_path / "assets.json", s)
    data = __import__("json").loads((tmp_path / "assets.json").read_text(encoding="utf-8"))
    assert (
        data["assets"]
        and all(r["attribution_text"] for r in data["assets"])
        and "허락이 아님" in data["notice"]
    )
    assert path_url("/a b/c.jpg") == "file://localhost/a%20b/c.jpg"
    _ = pytest


def test_subtitle_track_follows_voice(tmp_path):
    from PIL import Image

    from newsclip.pipeline import write_xml
    from newsclip.srt import Cue, write_srt

    s = _set(tmp_path)
    cues = [
        Cue(1, 0.5, 2.0, "금융보안원이 2주 전에 이미"),
        Cue(2, 1.9, 3.6, "위험을 알렸는데도"),  # 앞 자막과 살짝 겹침 → 겹치지 않게 당김
        Cue(3, 4.0, 6.5, "금융사들이 그대로 뚫렸습니다."),
        Cue(4, 30.0, 31.0, "시퀀스 밖 자막"),  # 영상 길이 밖 → 버림
    ]
    xml = build_xmeml(s, tmp_path, fps=30, size=(1920, 1080), subtitles=cues)
    assert validate_xmeml(xml) == []
    v3 = re.findall(r'<clipitem id="clipitem-v3-\d+">.*?</clipitem>', xml, re.S)
    assert len(v3) == 3
    spans = [
        tuple(int(x) for x in re.search(r"<start>(\d+)</start>\s*<end>(\d+)</end>", c).groups()) for c in v3
    ]
    assert spans[0] == (15, 60) and spans[1][0] == 60 and spans[2] == (120, 195)
    pngs = sorted((tmp_path / "subtitles").glob("sub_*.png"))
    assert len(pngs) == 3
    img = Image.open(pngs[0])
    assert img.size == (1920, 1080) and img.getbbox()[1] > 1080 * 0.7  # 화면 아래쪽
    cap = Image.open(next((tmp_path / "captions").glob("cap_*.png")))
    assert cap.getbbox()[3] < 1080 * 0.2  # 출처 표시는 왼쪽 위(자막과 안 겹침)
    # write_xml: 자막은 기본으로 넣지 않고(narration.srt가 있어도), 설정에서 켰을 때만 붙인다
    from studio_core.config import TimelineSection

    (tmp_path / "narration.srt").write_text(write_srt(cues[:3]), encoding="utf-8")
    assert write_xml(tmp_path, s, options=TimelineSection()) == []
    plain = (tmp_path / "timeline.xml").read_text(encoding="utf-8")
    assert "clipitem-v3-" not in plain and "clipitem-v2-" in plain  # 출처 표시는 그대로
    assert write_xml(tmp_path, s, options=TimelineSection(subtitles=True)) == []
    assert (tmp_path / "timeline.xml").read_text(encoding="utf-8").count("clipitem-v3-") == 3
    assert write_xml(tmp_path, s, options=TimelineSection(attribution=False)) == []
    bare = (tmp_path / "timeline.xml").read_text(encoding="utf-8")
    assert "clipitem-v2-" not in bare and "clipitem-v3-" not in bare and validate_xmeml(bare) == []
    # 설정 파일에서 읽기: channel.yaml timeline.subtitles
    (tmp_path / "channel.yaml").write_text("timeline:\n  subtitles: true\n", encoding="utf-8")
    from newsclip.pipeline import timeline_options

    assert timeline_options(tmp_path).subtitles is True


@respx.mock
async def test_download_image_tries_https_first(tmp_path):
    # 네이버 뉴스 사진은 검색 결과가 http://imgnews.naver.net으로 오는데, 클라우드에서는 http가 막힌다
    respx.get("https://imgnews.naver.net/image/003/2026/10/12/a.jpg").mock(
        return_value=httpx.Response(200, content=_jpeg())
    )
    blocked = respx.get("http://imgnews.naver.net/image/003/2026/10/12/a.jpg").mock(
        side_effect=httpx.ConnectError("Name or service not known")
    )
    reg, sem = DedupeRegistry(), __import__("asyncio").Semaphore(5)
    async with httpx.AsyncClient() as c:
        got = await download_image(
            c,
            asset(url="http://imgnews.naver.net/image/003/2026/10/12/a.jpg"),
            tmp_path / "s001_1.jpg",
            reg,
            (1920, 1080),
            sem,
        )
    assert got is not None and Path(got.local_path).exists() and not blocked.called


def test_vertical_photo_and_headline(tmp_path):
    """세로 쇼츠: 가로 사진은 가운데 사진 칸에 꽉 차고(바탕은 흐린 같은 사진), 상단 제목은 위쪽 자리에만 그린다."""
    from newsclip.download import normalize_image
    from newsclip.vertical import BOX, SIZE, render_headline, split_head, style_of

    buf = io.BytesIO()
    Image.new("RGB", (1920, 1080), (200, 40, 40)).save(buf, "JPEG")
    dest = tmp_path / "v.jpg"
    normalize_image(buf.getvalue(), dest, SIZE)
    im = Image.open(dest)
    assert im.size == SIZE
    mid = im.getpixel((SIZE[0] // 2, (BOX[1] + BOX[3]) // 2))
    top = im.getpixel((SIZE[0] // 2, 60))
    assert mid[0] > 150 and top[0] < mid[0]  # 사진 칸은 원래 색, 바깥은 어둡게 깐 바탕

    assert split_head("이게 사과입니까") == ["이게", "사과입니까"]
    assert split_head("한마디") == ["한마디"]
    assert split_head("與도 등 돌린 추미애") == [
        "與도 등 돌린",
        "추미애",
    ]  # 한 글자 낱말('등') 뒤에서 안 자름
    png = render_headline("민주당 지지율 진짜 이유", tmp_path / "h.png", SIZE)
    alpha = Image.open(png).getchannel("A")
    hb = style_of("default").head_box
    x0, y0, x1, y1 = alpha.point(lambda a: 255 if a > 200 else 0).getbbox()  # 글자(그림자 가장자리는 빼고)
    assert hb[1] - 30 <= y0 and y1 <= hb[3] + 30 and x0 >= hb[0] - 30 and x1 <= hb[2] + 30


def test_fcpxml_vertical_with_headline(tmp_path):
    from newsclip.vertical import SIZE

    s = _set(tmp_path)
    head = tmp_path / "overlays" / "headline.png"
    head.parent.mkdir()
    Image.new("RGBA", SIZE, (0, 0, 0, 0)).save(head)
    xml = build_xmeml(s, tmp_path, fps=30, size=SIZE, headline=head)
    assert validate_xmeml(xml) == []
    assert "<width>1080</width>" in xml and "<height>1920</height>" in xml
    assert xml.count('id="clipitem-head-1"') == 1 and "headline.png" in xml


def test_shorts_styles_differ_by_channel(tmp_path):
    """자유뉴스365(navy_red)는 남색·빨강 띠 위 흰 글씨와 검은 바탕, 정치는푸르다(default)는 띠 없는 테두리 글씨와 흐린 사진 바탕."""
    from newsclip.vertical import HEAD, NAVY, RED, SIZE, compose_photo, render_headline, style_of

    assert style_of("없는 이름").name == "default" and style_of("navy_red").bands == (NAVY, RED)
    band = Image.open(
        render_headline("민주당 예산 폭주", tmp_path / "r.png", SIZE, style="navy_red")
    ).convert("RGB")
    plain = Image.open(render_headline("민주당 예산 폭주", tmp_path / "p.png", SIZE)).convert("RGBA")
    colors = {c for _n, c in band.crop(HEAD).getcolors(1_000_000)}
    assert NAVY in colors and RED in colors
    assert not any(px[:3] in (NAVY, RED) for px in plain.crop(HEAD).getdata() if px[3])
    photo = Image.new("RGB", (1920, 1080), (200, 160, 120))
    assert compose_photo(photo, SIZE, "navy_red").getpixel((540, 60)) == (0, 0, 0)  # 사진 칸 위는 검은색
    assert compose_photo(photo, SIZE).getpixel((540, 60)) != (0, 0, 0)  # 정치는푸르다는 흐린 사진 바탕


def test_text_heavy_spots_video_thumbnails(tmp_path):
    """16:9에 검은 테두리 큰 제목 글씨 → 영상 썸네일(뉴스 채널은 안 씀). 흰 바탕 검은 글씨(인포그래픽)·
    16:9가 아닌 그림은 그대로 쓴다."""
    import random

    from PIL import ImageDraw, ImageFont

    from newsclip.download import text_heavy
    from newsclip.models import Asset

    font = ImageFont.truetype(str(Path(__file__).parents[1] / "src/newsclip/fonts/210MammothPpang-R.TTF"), 56)
    rnd = random.Random(3)

    def photo(size):
        im = Image.new("RGB", size)
        px = im.load()
        for y in range(size[1]):
            for x in range(size[0]):
                px[x, y] = (90 + rnd.randint(0, 60), 80 + rnd.randint(0, 50), 70 + rnd.randint(0, 40))
        return im

    def save(im, name):
        p = tmp_path / name
        im.save(p, "JPEG", quality=92)
        return Asset(source="naver_image", id=name, url="https://x/" + name, local_path=str(p))

    thumb = photo((640, 360))
    d = ImageDraw.Draw(thumb)
    for k, line in enumerate(["검찰보다 더 잔혹했던 감사원", "“목표 뚜렷했던 칼춤!”"]):
        d.text((20, 200 + k * 70), line, font=font, fill=(255, 236, 0), stroke_width=4, stroke_fill=(0, 0, 0))
    info = Image.new("RGB", (640, 360), (250, 250, 250))
    d = ImageDraw.Draw(info)
    for k, line in enumerate(["대법원 격려금", "감사 지적에도 증액"]):
        d.text((20, 60 + k * 90), line, font=font, fill=(20, 30, 60))
    assert text_heavy(save(thumb, "thumb.jpg"))
    assert not text_heavy(save(info, "info.jpg"))
    assert not text_heavy(save(photo((640, 360)), "plain.jpg"))
    assert not text_heavy(save(thumb.resize((480, 360)), "four3.jpg"))  # 16:9가 아니면 보도사진으로 본다
