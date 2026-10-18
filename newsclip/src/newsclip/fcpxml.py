"""프리미어용 FCP XML(xmeml v5).

- 시퀀스: 지정 fps·해상도. --audio가 있으면 A1에 내레이션.
- V1: 장면별 chosen을 start~end에. 영상이 짧으면 다음 후보로 채운다(영상 1클립 최대 video_max_sec).
- 사진: Basic Motion scale 100 → 100×zoom(ease-out, 선형 키프레임 5개로 근사). 홀수 장면 줌인, 짝수 줌아웃.
- V2: 각 클립 구간 동안 출처 자막 PNG(하단 좌측).
- 세로 쇼츠(1080x1920)면 맨 위 트랙에 상단 제목 PNG 하나를 처음부터 끝까지(headline).
- 자료 없는 장면은 "자료 없음: {keywords}", 건너뛴 장면은 "건너뛰기" 시퀀스 마커.
- A2: 내레이션이 있으면 화제 전환·질문 자리에 효과음(sfx.py).
- 생성 후 재파싱해 유효성 검사(validate_xmeml).
"""

from __future__ import annotations

import hashlib
import wave
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote

from newsclip.caption import render_caption, render_subtitle
from newsclip.download import in_point
from newsclip.license_log import attribution_text
from newsclip.models import Asset, Scene, SceneSet
from newsclip.sfx import ensure_sfx, sfx_cues

EASE_STEPS = 5


@dataclass
class Segment:
    """V1에 놓일 클립 하나."""

    scene: Scene
    scene_no: int
    asset: Asset
    start: float
    end: float
    src_in: float = 0.0


@dataclass
class Marker:
    """시퀀스 마커."""

    at: float
    name: str
    comment: str


def plan_segments(sset: SceneSet, video_max_sec: float = 20.0) -> tuple[list[Segment], list[Marker]]:
    """장면 → 클립 구간·마커."""
    segs: list[Segment] = []
    marks: list[Marker] = []
    for no, sc in enumerate(sset.scenes, start=1):
        if sc.skipped:
            marks.append(Marker(sc.start_sec, "건너뛰기", f"{sc.id}: {sc.text[:40]}"))
            continue
        downloaded = [c for c in sc.candidates if c.local_path]
        order = ([sc.chosen] if sc.chosen and sc.chosen.local_path else []) + [
            c for c in downloaded if not sc.chosen or c.id != sc.chosen.id
        ]
        if not order:
            marks.append(Marker(sc.start_sec, "자료 없음", f"자료 없음: {', '.join(sc.keywords_ko)}"))
            continue
        t = sc.start_sec
        for a in order:
            if t >= sc.end_sec - 1e-3:
                break
            if a.media == "image":
                segs.append(Segment(sc, no, a, t, sc.end_sec))
                t = sc.end_sec
                break
            avail = (a.duration_sec or 0.0) - in_point(a)
            length = min(avail, sc.end_sec - t, video_max_sec)
            if length < 0.5:
                continue
            segs.append(Segment(sc, no, a, t, t + length, src_in=in_point(a)))
            t += length
        if t < sc.end_sec - 1e-3:
            marks.append(
                Marker(t, "자료 부족", f"{sc.id}: {sc.end_sec - t:.1f}초 비어 있음 — 후보 추가 필요")
            )
    return segs, marks


def _f(sec: float, fps: int) -> int:
    return int(round(sec * fps))


def path_url(p: str | Path) -> str:
    """로컬 경로 → file://localhost/... (윈도우 드라이브 경로 포함)."""
    posix = Path(p).resolve().as_posix()
    if not posix.startswith("/"):
        posix = "/" + posix
    return "file://localhost" + quote(posix, safe="/:")


def _rate(parent: ET.Element, fps: int) -> None:
    r = ET.SubElement(parent, "rate")
    ET.SubElement(r, "timebase").text = str(fps)
    ET.SubElement(r, "ntsc").text = "FALSE"


def _text(parent: ET.Element, tag: str, value: object) -> ET.Element:
    el = ET.SubElement(parent, tag)
    el.text = str(value)
    return el


def ease_out_keyframes(a: float, b: float, frames: int, steps: int = EASE_STEPS) -> list[tuple[int, float]]:
    """ease-out(cubic) 곡선을 선형 키프레임으로 근사."""
    out = []
    for i in range(steps):
        t = i / (steps - 1)
        v = a + (b - a) * (1 - (1 - t) ** 3)
        out.append((int(round(frames * t)), round(v, 2)))
    return out


def _motion(parent: ET.Element, zoom_in: bool, zoom: float, frames: int, base: float = 100.0) -> None:
    flt = ET.SubElement(parent, "filter")
    eff = ET.SubElement(flt, "effect")
    _text(eff, "name", "Basic Motion")
    _text(eff, "effectid", "basic")
    _text(eff, "effectcategory", "motion")
    _text(eff, "effecttype", "motion")
    _text(eff, "mediatype", "video")
    par = ET.SubElement(eff, "parameter", {"authoringApp": "PremierePro"})
    _text(par, "parameterid", "scale")
    _text(par, "name", "Scale")
    _text(par, "valuemin", 0)
    _text(par, "valuemax", 1000)
    a, b = (base, base * zoom) if zoom_in else (base * zoom, base)
    _text(par, "value", round(a, 2))
    for when, val in ease_out_keyframes(a, b, frames):
        kf = ET.SubElement(par, "keyframe")
        _text(kf, "when", when)
        _text(kf, "value", val)


class _Files:
    """file 요소 재사용(같은 파일은 두 번째부터 id만 참조)."""

    def __init__(self) -> None:
        self.ids: dict[str, str] = {}

    def add(
        self,
        parent: ET.Element,
        path: str,
        fps: int,
        frames: int,
        w: int | None,
        h: int | None,
        audio: bool = False,
    ) -> None:
        key = str(Path(path).resolve())
        if key in self.ids:
            ET.SubElement(parent, "file", {"id": self.ids[key]})
            return
        fid = f"file-{len(self.ids) + 1}"
        self.ids[key] = fid
        f = ET.SubElement(parent, "file", {"id": fid})
        _text(f, "name", Path(path).name)
        _text(f, "pathurl", path_url(path))
        _rate(f, fps)
        _text(f, "duration", frames)
        media = ET.SubElement(f, "media")
        if audio:
            au = ET.SubElement(media, "audio")
            sc = ET.SubElement(au, "samplecharacteristics")
            _text(sc, "depth", 16)
            _text(sc, "samplerate", 48000)
            _text(au, "channelcount", 1)
        else:
            vid = ET.SubElement(media, "video")
            sc = ET.SubElement(vid, "samplecharacteristics")
            if w and h:
                _text(sc, "width", w)
                _text(sc, "height", h)


def caption_for(scene: Scene, asset: Asset) -> str:
    """출처 칸 문구. 같은 인물이지만 사건과 다른 날 찍은 사진이면 앞에 '다른 날 사진 ·'을 붙인다."""
    text = asset.attribution_text or attribution_text(asset)
    return f"다른 날 사진 · {text}" if scene.other_day else text


def audio_duration(path: Path) -> float | None:
    """WAV 길이(초). WAV가 아니면 None."""
    try:
        with wave.open(str(path), "rb") as w:
            return w.getnframes() / float(w.getframerate())
    except (wave.Error, OSError, EOFError):
        return None


def build_xmeml(
    sset: SceneSet,
    project_dir: Path,
    *,
    fps: int = 30,
    size: tuple[int, int] = (1920, 1080),
    zoom: float = 1.04,
    audio: Path | None = None,
    video_max_sec: float = 20.0,
    subtitles: list | None = None,
    attribution: bool = True,
    headline: Path | None = None,
    style: str = "default",
    sfx: bool = True,
) -> str:
    """xmeml v5 문자열. attribution이면 V2에 출처 표시, subtitles(자막 큐 목록)가 있으면 그 위 트랙에 대본 자막,
    headline(투명 PNG)이 있으면 맨 위 트랙에 영상 내내. audio가 있고 sfx이면 A2에 효과음."""
    segs, marks = plan_segments(sset, video_max_sec)
    seq_end = max([s.end_sec for s in sset.scenes] + [0.0])
    a_dur = audio_duration(audio) if audio else None
    if a_dur:
        seq_end = max(seq_end, a_dur)
    total = _f(seq_end, fps)

    root = ET.Element("xmeml", {"version": "5"})
    seq = ET.SubElement(root, "sequence", {"id": "sequence-1"})
    _text(seq, "name", sset.project)
    _text(seq, "duration", total)
    _rate(seq, fps)
    tc = ET.SubElement(seq, "timecode")
    _rate(tc, fps)
    _text(tc, "string", "00:00:00:00")
    _text(tc, "frame", 0)
    _text(tc, "displayformat", "NDF")
    media = ET.SubElement(seq, "media")
    video = ET.SubElement(media, "video")
    fmt = ET.SubElement(video, "format")
    sc = ET.SubElement(fmt, "samplecharacteristics")
    _rate(sc, fps)
    _text(sc, "width", size[0])
    _text(sc, "height", size[1])
    _text(sc, "anamorphic", "FALSE")
    _text(sc, "pixelaspectratio", "square")
    _text(sc, "fielddominance", "none")

    files = _Files()
    v1 = ET.SubElement(video, "track")
    v2 = ET.SubElement(video, "track") if attribution else None
    cap_dir = project_dir / "captions"
    for n, seg in enumerate(segs, start=1):
        a = seg.asset
        st, en = _f(seg.start, fps), _f(seg.end, fps)
        if en <= st:
            continue
        length = en - st
        ci = ET.SubElement(v1, "clipitem", {"id": f"clipitem-v1-{n}"})
        _text(ci, "name", Path(a.local_path or "").name)
        _text(ci, "enabled", "TRUE")
        if a.media == "image":
            _text(ci, "duration", length)
            _rate(ci, fps)
            _text(ci, "start", st)
            _text(ci, "end", en)
            _text(ci, "in", 0)
            _text(ci, "out", length)
            files.add(ci, a.local_path or "", fps, length, size[0], size[1])
            _motion(ci, zoom_in=seg.scene_no % 2 == 1, zoom=zoom, frames=length)
        else:
            src_frames = _f(a.duration_sec or seg.end - seg.start, fps)
            src_in = _f(seg.src_in, fps)
            _text(ci, "duration", src_frames)
            _rate(ci, fps)
            _text(ci, "start", st)
            _text(ci, "end", en)
            _text(ci, "in", src_in)
            _text(ci, "out", src_in + length)
            files.add(ci, a.local_path or "", fps, src_frames, a.width, a.height)
            if a.width and a.width != size[0]:
                _motion(ci, zoom_in=True, zoom=1.0, frames=length, base=round(100.0 * size[0] / a.width, 2))
        # V2 출처 표시(직접 그린 슬라이드는 출처 표시 없음)
        if v2 is None or a.source == "slide":
            continue
        text = caption_for(seg.scene, a)
        key = text if style == "default" else f"{style}:{text}"  # 디자인이 다르면 출처 표시 그림도 따로
        png = cap_dir / f"cap_{hashlib.sha1(key.encode()).hexdigest()[:8]}.png"
        if not png.exists():
            render_caption(text, png, size, style=style)
        cc = ET.SubElement(v2, "clipitem", {"id": f"clipitem-v2-{n}"})
        _text(cc, "name", text)
        _text(cc, "enabled", "TRUE")
        _text(cc, "duration", length)
        _rate(cc, fps)
        _text(cc, "start", st)
        _text(cc, "end", en)
        _text(cc, "in", 0)
        _text(cc, "out", length)
        files.add(cc, str(png), fps, length, size[0], size[1])

    # V3 대본 자막(음성에 맞춘 자막 큐 → 줄마다 PNG)
    if subtitles:
        v3 = ET.SubElement(video, "track")
        sub_dir = project_dir / "subtitles"
        last_end = 0
        for n, cue in enumerate(sorted(subtitles, key=lambda c: c.start), start=1):
            st = max(_f(cue.start, fps), last_end)
            en = min(_f(cue.end, fps), total)
            if en <= st:
                continue
            png = sub_dir / f"sub_{hashlib.sha1(cue.text.encode()).hexdigest()[:10]}.png"
            if not png.exists():
                render_subtitle(cue.text, png, size)
            sc_ = ET.SubElement(v3, "clipitem", {"id": f"clipitem-v3-{n}"})
            _text(sc_, "name", cue.text)
            _text(sc_, "enabled", "TRUE")
            _text(sc_, "duration", en - st)
            _rate(sc_, fps)
            _text(sc_, "start", st)
            _text(sc_, "end", en)
            _text(sc_, "in", 0)
            _text(sc_, "out", en - st)
            files.add(sc_, str(png), fps, en - st, size[0], size[1])
            last_end = en

    # 맨 위: 상단 제목(세로 쇼츠) — 처음부터 끝까지 한 클립
    if headline is not None and total > 0:
        vh = ET.SubElement(video, "track")
        hc = ET.SubElement(vh, "clipitem", {"id": "clipitem-head-1"})
        _text(hc, "name", headline.name)
        _text(hc, "enabled", "TRUE")
        _text(hc, "duration", total)
        _rate(hc, fps)
        _text(hc, "start", 0)
        _text(hc, "end", total)
        _text(hc, "in", 0)
        _text(hc, "out", total)
        files.add(hc, str(headline), fps, total, size[0], size[1])

    if audio is not None:
        aud = ET.SubElement(media, "audio")
        afmt = ET.SubElement(aud, "format")
        asc = ET.SubElement(afmt, "samplecharacteristics")
        _text(asc, "depth", 16)
        _text(asc, "samplerate", 48000)
        a1 = ET.SubElement(aud, "track")
        frames = _f(a_dur or seq_end, fps)
        ac = ET.SubElement(a1, "clipitem", {"id": "clipitem-a1-1"})
        _text(ac, "name", audio.name)
        _text(ac, "enabled", "TRUE")
        _text(ac, "duration", frames)
        _rate(ac, fps)
        _text(ac, "start", 0)
        _text(ac, "end", frames)
        _text(ac, "in", 0)
        _text(ac, "out", frames)
        files.add(ac, str(audio), fps, frames, None, None, audio=True)
        stk = ET.SubElement(ac, "sourcetrack")
        _text(stk, "mediatype", "audio")
        _text(stk, "trackindex", 1)
        # A2 효과음: 화제 전환·질문 자리(sfx.py). 시퀀스 끝을 넘는 소리는 자른다.
        cues = sfx_cues(sset.scenes) if sfx else []
        if cues:
            sfx_files = ensure_sfx(project_dir)
            a2 = ET.SubElement(aud, "track")
            for n, cue in enumerate(cues, start=1):
                path = sfx_files[cue.name]
                st = _f(cue.at, fps)
                length = min(_f(audio_duration(path) or 0.0, fps), total - st)
                if length <= 0:
                    continue
                xc = ET.SubElement(a2, "clipitem", {"id": f"clipitem-a2-{n}"})
                _text(xc, "name", path.name)
                _text(xc, "enabled", "TRUE")
                _text(xc, "duration", length)
                _rate(xc, fps)
                _text(xc, "start", st)
                _text(xc, "end", st + length)
                _text(xc, "in", 0)
                _text(xc, "out", length)
                files.add(xc, str(path), fps, length, None, None, audio=True)
                xtk = ET.SubElement(xc, "sourcetrack")
                _text(xtk, "mediatype", "audio")
                _text(xtk, "trackindex", 1)

    for m in marks:
        mk = ET.SubElement(seq, "marker")
        _text(mk, "comment", m.comment)
        _text(mk, "name", m.name)
        _text(mk, "in", _f(m.at, fps))
        _text(mk, "out", -1)

    ET.indent(root, space="  ")
    body = ET.tostring(root, encoding="unicode")
    return '<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE xmeml>\n' + body + "\n"


def validate_xmeml(xml_text: str) -> list[str]:
    """재파싱 유효성 검사. 문제 목록(빈 목록이면 통과)."""
    errs: list[str] = []
    try:
        root = ET.fromstring(xml_text.split("<!DOCTYPE xmeml>", 1)[-1])
    except ET.ParseError as e:
        return [f"XML 파싱 실패: {e}"]
    if root.tag != "xmeml" or root.get("version") != "5":
        errs.append("루트가 xmeml version=5가 아님")
    seq = root.find("sequence")
    if seq is None:
        return [*errs, "sequence 없음"]
    total = int(seq.findtext("duration", "0"))
    defined: set[str] = set()
    for ti, track in enumerate(seq.iter("track"), start=1):
        last_end = -1
        for ci in track.findall("clipitem"):
            cid = ci.get("id")
            st, en = int(ci.findtext("start", "-1")), int(ci.findtext("end", "-1"))
            i, o = int(ci.findtext("in", "-1")), int(ci.findtext("out", "-1"))
            if not 0 <= st < en:
                errs.append(f"{cid}: start/end 오류({st},{en})")
            if o - i != en - st:
                errs.append(f"{cid}: in/out 길이({o - i})와 타임라인 길이({en - st}) 불일치")
            if st < last_end:
                errs.append(f"{cid}: 트랙 {ti}에서 앞 클립과 겹침")
            if en > total:
                errs.append(f"{cid}: 시퀀스 길이 초과")
            last_end = en
            f = ci.find("file")
            if f is None:
                errs.append(f"{cid}: file 없음")
                continue
            fid = f.get("id", "")
            if f.find("pathurl") is not None:
                defined.add(fid)
            elif fid not in defined:
                errs.append(f"{cid}: 정의되지 않은 file 참조 {fid}")
    for mk in seq.findall("marker"):
        if not 0 <= int(mk.findtext("in", "-1")) <= total:
            errs.append(f"마커 위치 범위 밖: {mk.findtext('name')}")
    return errs
