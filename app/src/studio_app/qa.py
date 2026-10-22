"""완성 쇼츠 자동 검수: 렌더 직후 작업자가 스스로 보는 잔실수 목록과 1초 간격 장면 모음.

사용자가 영상을 보고 찾아 주던 실수(자막이 어색한 데서 끊김, 문서 캡처·워터마크 사진, 검은 화면, 음량)를
먼저 잡는다. 걸린 것은 render.qa.issues에 시각과 함께 남기고, 제작실 화면이 '확인 필요'로 띄운다.
"""

from __future__ import annotations

import re
import subprocess
from pathlib import Path

LUFS_RANGE = (-17.0, -11.0)  # 쇼츠 음량(유튜브는 -14 LUFS로 맞춘다 — 너무 작거나 크면 들쭉날쭉)
PEAK_MAX = -0.5  # 최고점(dBTP) — 넘으면 올릴 때 찢어질 수 있다
BREAK_BAD = 8.0  # 이 값 넘게 어색한 자리에서 끊긴 자막(autosub._break_cost)
CUE_MIN = 0.35  # 이보다 짧게 스치는 자막
SILENCE_SEC = 1.2  # 영상 가운데 이보다 긴 무음
BLACK_SEC = 0.3  # 이보다 긴 검은 화면
SHEET_COLS, SHEET_W = 8, 180  # 장면 모음: 한 줄 8칸, 칸 너비 180px(세로 9:16)


def _ff(args: list[str]) -> str:
    proc = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", *args], capture_output=True, text=True)
    return proc.stderr


def loudness(final: Path) -> tuple[float | None, float | None]:
    """(통합 음량 LUFS, 최고점 dBTP)."""
    err = _ff(["-i", str(final), "-af", "ebur128=peak=true", "-f", "null", "-"])
    lufs = re.findall(r"I:\s+(-?\d+(?:\.\d+)?) LUFS", err)
    peak = re.findall(r"Peak:\s+(-?\d+(?:\.\d+)?) dBFS", err)
    return (float(lufs[-1]) if lufs else None, float(peak[-1]) if peak else None)


def black_spans(final: Path) -> list[tuple[float, float]]:
    err = _ff(["-i", str(final), "-vf", f"blackdetect=d={BLACK_SEC}:pix_th=0.10", "-an", "-f", "null", "-"])
    return [
        (float(a), float(b))
        for a, b in re.findall(r"black_start:(\d+(?:\.\d+)?) black_end:(\d+(?:\.\d+)?)", err)
    ]


def silent_spans(final: Path) -> list[tuple[float, float]]:
    err = _ff(
        ["-i", str(final), "-af", f"silencedetect=noise=-45dB:d={SILENCE_SEC}", "-vn", "-f", "null", "-"]
    )
    starts = [float(x) for x in re.findall(r"silence_start: (-?\d+(?:\.\d+)?)", err)]
    ends = [float(x) for x in re.findall(r"silence_end: (\d+(?:\.\d+)?)", err)]
    return list(zip(starts, ends, strict=False))


def subtitle_issues(cues: list, long: bool = False) -> list[dict]:
    """자막: 두 줄이 어색한 데서 갈림, 한 문장 안 자막 경계가 어색함, 너무 짧게 스침, 한 줄이 너무 길어 글씨가 작아짐.
    long이면 롱폼(가로) 자막 — 한 줄 자막(짧은 줄을 합친 것만 두 줄), 한 줄 글자 상한도 롱폼 기준."""
    from newsclip.autosub import _break_cost, _quoted
    from newsclip.render import SUB_WRAP, sub_lines

    row_max = 22 + 4 if long else SUB_WRAP + 4  # 롱폼: long_edit.SUB_LINE_LONG
    out: list[dict] = []
    for k, c in enumerate(cues):
        text = c.text.replace("\n", " ").strip()
        rows = [r for r in c.text.split("\n") if r.strip()] if long else sub_lines(c.text)
        if len(rows) == 2 and " " in text:
            at, q = len(rows[0]), _quoted(text)
            # 긴 인용은 안에서 갈릴 수밖에 없다 — 인용 밖에서 어색하게 갈린 것만
            cost = _break_cost(text, at, q) if text[at : at + 1] == " " and at not in q else 0.0
            if cost >= BREAK_BAD:
                out.append({"kind": "subtitle_break", "at": c.start, "what": " / ".join(rows)})
        if any(len(r.replace(" ", "")) > row_max for r in rows):
            out.append({"kind": "subtitle_long", "at": c.start, "what": text})
        if c.end - c.start < CUE_MIN and k + 1 < len(cues):
            out.append({"kind": "subtitle_flash", "at": c.start, "what": text})
        if k + 1 < len(cues) and not re.search(r"[.?!…]['\"”’」』)]*$", text):
            nxt = cues[k + 1].text.replace("\n", " ").strip()
            joined = f"{text} {nxt}"
            q = _quoted(joined)
            if len(text) not in q and _break_cost(joined, len(text), q) >= BREAK_BAD:
                out.append({"kind": "subtitle_cut", "at": cues[k + 1].start, "what": f"{text} | {nxt}"})
    return out


def photo_issues(scenes: list[dict], proj: Path) -> list[dict]:
    """사진: 워터마크 사이트, 문서·표·화면 캡처, 멀리 떨어진 장면에 같은 사진."""
    from newsclip.download import DOC_ROWS, doc_text_rows
    from newsclip.rank import WATERMARK_DOMAINS, domain_match

    out: list[dict] = []
    seen: dict[str, int] = {}
    for n, sc in enumerate(scenes):
        at, until = float(sc.get("start") or 0.0), float(sc.get("end") or sc.get("start") or 0.0)
        dom = re.sub(r"^출처:\s*", "", str(sc.get("attribution") or "")).strip()
        if dom and domain_match(dom, WATERMARK_DOMAINS):
            out.append({"kind": "photo_watermark", "at": at, "until": until, "what": dom})
        for rel in sc.get("files") or ([sc["chosen"]] if sc.get("chosen") else []):
            path = proj / rel
            if not path.exists() or path.suffix.lower() not in (".jpg", ".jpeg", ".png"):
                continue
            if path.stem.endswith("_card"):  # 우리가 그린 수치 카드(글자가 많아 문서처럼 재지만 일부러 넣은 것)
                continue
            try:
                measured = [doc_text_rows(path)]
                probe = _white_box(
                    path, proj / ".qa_box.png"
                )  # 쇼츠 사진은 흐린 바탕 가운데 칸 — 흰 칸만 다시 잰다
                if probe is not None:
                    measured.append(doc_text_rows(probe))
                    probe.unlink(missing_ok=True)
            except Exception:  # noqa: BLE001 - 못 재면 넘어간다
                continue
            if any(rows >= r and white >= wt for rows, white in measured for r, wt in DOC_ROWS):
                out.append({"kind": "photo_document", "at": at, "until": until, "what": rel})
            key = path.read_bytes()[:4096].hex() + str(path.stat().st_size)
            if key in seen and n - seen[key] > 2:
                out.append({"kind": "photo_repeat", "at": at, "until": until, "what": rel})
            seen.setdefault(key, n)
    return out


def _white_box(path: Path, probe: Path) -> Path | None:
    """그림 안 흰 바탕 칸(흰 화소가 30% 넘는 줄·열이 이어진 곳)을 잘라 probe로 저장. 그림의 8% 안쪽이면 None."""
    import numpy as np
    from PIL import Image

    with Image.open(path) as im:
        rgb = im.convert("RGB")
    g = np.asarray(rgb.convert("L"))
    white = g > 215
    cols = np.flatnonzero(white.mean(axis=0) > 0.3)
    rows = np.flatnonzero(white.mean(axis=1) > 0.3)
    if not len(cols) or not len(rows):
        return None
    box = (int(cols.min()), int(rows.min()), int(cols.max()) + 1, int(rows.max()) + 1)
    if (box[2] - box[0]) * (box[3] - box[1]) < 0.08 * g.size:
        return None
    rgb.crop(box).save(probe)
    return probe


def contact_sheet(final: Path, out: Path, duration: float, marks: list[tuple[float, float]]) -> Path | None:
    """1초 간격 장면 모음(긴 영상은 간격을 늘려 많아야 96칸). 검수에 걸린 구간(시작, 끝)의 칸은 빨간 테두리."""
    from PIL import Image, ImageDraw

    step = max(1.0, duration / 96)
    tmp = out / "qa_frames"
    tmp.mkdir(parents=True, exist_ok=True)
    for old in tmp.glob("*.jpg"):
        old.unlink()
    _ff(
        [
            "-y",
            "-i",
            str(final),
            "-vf",
            f"fps=1/{step},scale={SHEET_W}:-2",
            "-q:v",
            "4",
            str(tmp / "f_%04d.jpg"),
        ]
    )
    frames = sorted(tmp.glob("f_*.jpg"))
    if not frames:
        return None
    with Image.open(frames[0]) as f0:
        fw, fh = f0.size
    rows = -(-len(frames) // SHEET_COLS)
    sheet = Image.new("RGB", (SHEET_COLS * fw, rows * (fh + 18)), (24, 24, 24))
    d = ImageDraw.Draw(sheet)
    for i, f in enumerate(frames):
        t = i * step
        x, y = (i % SHEET_COLS) * fw, (i // SHEET_COLS) * (fh + 18)
        with Image.open(f) as im:
            sheet.paste(im, (x, y + 18))
        bad = any(a - 0.5 <= t + step / 2 and t < max(a, b) + 0.5 for a, b in marks)
        d.text(
            (x + 4, y + 3),
            f"{int(t // 60)}:{int(t % 60):02d}",
            fill=(255, 80, 80) if bad else (220, 220, 220),
        )
        if bad:
            d.rectangle((x + 1, y + 18, x + fw - 2, y + 17 + fh), outline=(255, 40, 40), width=4)
    path = out / "sheet.jpg"
    sheet.save(path, "JPEG", quality=82)
    return path


LABELS = {
    "audio_quiet": "음량이 작음",
    "audio_loud": "음량이 큼",
    "audio_peak": "소리 최고점이 높음(찢어질 수 있음)",
    "black": "검은 화면",
    "silence": "소리가 끊김",
    "subtitle_break": "자막 두 줄이 어색하게 갈림",
    "subtitle_cut": "자막이 어색한 데서 넘어감",
    "subtitle_flash": "자막이 너무 짧게 스침",
    "subtitle_long": "자막 한 줄이 길어 글씨가 작아짐",
    "photo_watermark": "워터마크 사진",
    "photo_document": "문서·화면 캡처 사진",
    "photo_repeat": "같은 사진이 다시 나옴",
    "photo_mismatch": "문장과 안 맞는 사진",
}


def check_short(
    final: Path, proj: Path, scenes: list[dict], out: Path, fixed: list[dict] | None = None
) -> dict:
    """완성 쇼츠 검수 → {ok, issues[{kind, label, at, what}], fixed, lufs, peak}. out/sheet.jpg(장면 모음)도 만든다.
    issues는 고치고도 남은 것, fixed는 제출 전에 고친 것."""
    from newsclip.shorts_render import probe_duration
    from newsclip.srt import parse_srt

    duration = probe_duration(final)
    issues: list[dict] = []
    fixed = list(fixed or [])
    lufs, peak = loudness(final)
    loud_fix = fix_audio(final, lufs, peak)
    if loud_fix:
        fixed += loud_fix
        lufs, peak = loudness(final)
    if lufs is not None and lufs < LUFS_RANGE[0]:
        issues.append({"kind": "audio_quiet", "at": 0.0, "what": f"{lufs:.1f} LUFS"})
    if lufs is not None and lufs > LUFS_RANGE[1]:
        issues.append({"kind": "audio_loud", "at": 0.0, "what": f"{lufs:.1f} LUFS"})
    if peak is not None and peak > PEAK_MAX:
        issues.append({"kind": "audio_peak", "at": 0.0, "what": f"{peak:.1f} dBTP"})
    for a, b in black_spans(final):
        issues.append({"kind": "black", "at": a, "what": f"{b - a:.1f}초"})
    for a, b in silent_spans(final):
        if 0.5 < a and b < duration - 1.0:  # 시작·끝 무음은 괜찮다
            issues.append({"kind": "silence", "at": a, "what": f"{b - a:.1f}초"})
    srt = proj / "narration.srt"
    if srt.exists():
        issues += subtitle_issues(parse_srt(srt.read_text(encoding="utf-8")))
    issues += photo_issues(scenes, proj)
    for it in [*issues, *fixed]:
        it["label"] = LABELS.get(it["kind"], it["kind"])
        it["at"] = round(float(it["at"]), 2)
        if "until" in it:
            it["until"] = round(float(it["until"]), 2)
    issues.sort(key=lambda x: x["at"])
    marks = [(it["at"], it.get("until", it["at"])) for it in issues if not it["kind"].startswith("audio_")]
    contact_sheet(final, out, duration, marks)
    return {
        "ok": not issues,
        "issues": issues[:30],
        "fixed": fixed[:30],
        "lufs": lufs,
        "peak": peak,
    }


# ---- 고치기: 걸린 것을 제출 전에 고친다 ------------------------------------------------------------


def _bare(t: str) -> str:
    return re.sub(r"\s+", "", t or "")


def _times_for(lines: list[str], a: float, b: float, words: list | None) -> list[float] | None:
    """lines(이어 붙이면 한 덩어리)의 줄마다 시작 시각. 받은 낱말 시각(타입캐스트)에서 그 글을 찾아 줄 첫 낱말 시각을
    쓰고, 못 찾으면 읽는 길이 비율로 나눈다."""
    from newsclip.autosub import spoken_len

    whole = _bare("".join(lines))
    if words:
        stream, owner = "", []
        for n, w in enumerate(words):
            piece = _bare(str(w[0]))
            stream += piece
            owner += [n] * len(piece)
        best = None
        at = stream.find(whole)
        while at >= 0:
            t0 = float(words[owner[at]][1])
            if best is None or abs(t0 - a) < abs(float(words[owner[best]][1]) - a):
                best = at
            at = stream.find(whole, at + 1)
        if best is not None and abs(float(words[owner[best]][1]) - a) < 1.5:
            out, pos = [], best
            for ln in lines:
                out.append(float(words[owner[pos]][1]))
                pos += len(_bare(ln))
            out[0] = a
            return out
    weights = [spoken_len(ln) for ln in lines]
    total = sum(weights) or 1.0
    out, t = [], a
    for w in weights:
        out.append(t)
        t += (b - a) * w / total
    return out


def _resplit(cues: list, i: int, j: int, words: list | None, limit: int) -> list | None:  # noqa: PLR0911
    """cues[i..j]를 이어 지금 규칙(subtitle_lines)으로 다시 나눈 자막들(시각은 낱말 시각). 같으면 None."""
    from newsclip.autosub import subtitle_lines
    from newsclip.srt import Cue

    if not words:  # 낱말 시각이 없으면(직접 올린 음성) 시각을 어림하게 되어 고치지 않는다
        return None
    text = " ".join(c.text.replace("\n", " ").strip() for c in cues[i : j + 1])
    lines = subtitle_lines(text, limit)
    if lines == [c.text.replace("\n", " ").strip() for c in cues[i : j + 1]]:
        return None
    starts = _times_for(lines, cues[i].start, cues[j].end, words)
    if starts is None:
        return None
    ends = [*starts[1:], cues[j].end]
    if any(e - s < 0.2 for s, e in zip(starts, ends, strict=True)):
        return None
    return [
        Cue(0, round(s, 3), round(e - (0.02 if k + 1 < len(lines) else 0), 3), ln)
        for k, (ln, s, e) in enumerate(zip(lines, starts, ends, strict=True))
    ]


def fix_subtitles(cues: list, words: list | None, limit: int, long: bool = False) -> tuple[list, list[dict]]:
    """자막 검수에 걸린 곳마다 앞뒤 자막과 이어 다시 나눠 본다(걸린 수가 줄어드는 쪽으로). (고친 자막, 고친 것).
    long이면 롱폼 자막 기준(subtitle_issues의 long)."""
    from newsclip.srt import Cue

    fixed: list[dict] = []
    tried: set[str] = set()
    for _round in range(40):
        issues = [x for x in subtitle_issues(cues, long) if x["kind"].startswith("subtitle_")]
        todo = [x for x in issues if f"{x['kind']}|{x['what']}" not in tried]
        if not todo:
            break
        it = todo[0]
        tried.add(f"{it['kind']}|{it['what']}")
        k = min(range(len(cues)), key=lambda n: abs(cues[n].start - it["at"]))
        best, best_n = None, len(issues)
        for i, j in ((k - 1, k), (k, k + 1), (k - 1, k + 1), (k - 2, k), (k, k + 2)):
            if i < 0 or j >= len(cues):
                continue
            new = _resplit(cues, i, j, words, limit)
            if new is None:
                continue
            cand = [*cues[:i], *new, *cues[j + 1 :]]
            n = len(subtitle_issues(cand, long))
            if n < best_n:
                best, best_n = cand, n
        if best is not None:
            fixed.append({"kind": it["kind"], "at": it["at"], "what": it["what"]})
            cues = [Cue(n + 1, c.start, c.end, c.text) for n, c in enumerate(best)]
    return cues, fixed


def fix_photos(
    scenes: list[dict], proj: Path, drop: list[str] | tuple[str, ...] = ()
) -> tuple[list[dict], list[dict]]:
    """사진 검수에 걸린 사진(과 drop — 작업자가 장면 모음을 보고 문장과 안 맞는다고 고른 사진)을 뺀다: 장면의 다른
    후보 사진으로, 골라 쓴 사진이 걸렸으면 옆 장면 사진(출처째)으로."""
    import copy

    scenes = copy.deepcopy(scenes)
    fixed: list[dict] = []
    todo = [(it, None) for it in photo_issues(scenes, proj)]
    for rel in drop:
        if rel.startswith("@"):  # '@15.5' — 그 시각 장면의 대표 사진
            t = float(rel[1:])
            hit = next(
                (sc for sc in scenes if float(sc.get("start") or 0) <= t < float(sc.get("end") or 0)), None
            )
            rel = (hit or {}).get("chosen") or ""
        k = next(
            (k for k, sc in enumerate(scenes) if rel == sc.get("chosen") or rel in (sc.get("files") or [])),
            None,
        )
        if k is not None:
            at = float(scenes[k].get("start") or 0)
            todo.append(
                (
                    {
                        "kind": "photo_mismatch",
                        "at": at,
                        "until": float(scenes[k].get("end") or at),
                        "what": rel,
                    },
                    k,
                )
            )
    for it, n in todo:
        if n is None:
            n = next((k for k, sc in enumerate(scenes) if float(sc.get("start") or 0) == it["at"]), None)
        if n is None:
            continue
        sc = scenes[n]
        bad = it["what"] if it["kind"] != "photo_watermark" else sc.get("chosen")
        if it["kind"] == "photo_repeat" and bad == sc.get("chosen"):
            continue  # 골라 쓴 사진의 반복은 빌려 와도 또 반복이다 — 그대로 둔다
        others = [f for f in sc.get("files") or [] if f != bad]
        if bad != sc.get("chosen") and it["kind"] != "photo_watermark":
            sc["files"] = others
        elif it["kind"] == "photo_mismatch" and others:
            # 문장과 안 맞는 대표 사진: 같은 장면의 다른 후보가 있으면 그걸로(출처는 같은 검색 결과)
            sc["chosen"], sc["files"] = others[0], others
        else:
            # 골라 쓴 사진(출처가 이 사진의 것)이 걸림 — 옆 장면의 사진과 출처를 빌린다
            near = [m for m in (n - 1, n + 1, n - 2, n + 2) if 0 <= m < len(scenes)]
            donor = next(
                (scenes[m] for m in near if scenes[m].get("chosen") and not _flagged(scenes[m], proj)), None
            )
            if donor is None:
                continue
            sc["chosen"], sc["attribution"] = donor["chosen"], donor.get("attribution", "")
            sc["files"] = [donor["chosen"]]
        fixed.append({"kind": it["kind"], "at": it["at"], "what": it["what"]})
    return scenes, fixed


def _flagged(sc: dict, proj: Path) -> bool:
    return any(x["kind"] != "photo_repeat" for x in photo_issues([sc], proj))


def fix_audio(final: Path, lufs: float | None, peak: float | None) -> list[dict]:
    """음량이 범위를 벗어나거나 최고점이 높으면 소리만 -14 LUFS·-1.5 dBTP로 맞춰 다시 담는다(화면은 그대로)."""
    bad_lufs = lufs is not None and not (LUFS_RANGE[0] <= lufs <= LUFS_RANGE[1])
    bad_peak = peak is not None and peak > PEAK_MAX
    if not (bad_lufs or bad_peak):
        return []
    tmp = final.with_name("final-loud.mp4")
    _ff(
        [
            "-y",
            "-i",
            str(final),
            "-c:v",
            "copy",
            "-af",
            "loudnorm=I=-14:TP=-1.5:LRA=11",
            "-ar",
            "48000",
            "-shortest",
            "-c:a",
            "aac",
            "-b:a",
            "192k",
            "-movflags",
            "+faststart",
            str(tmp),
        ]
    )
    if tmp.exists() and tmp.stat().st_size > 0:
        tmp.replace(final)
        kind = (
            "audio_peak"
            if bad_peak and not bad_lufs
            else ("audio_quiet" if lufs is not None and lufs < LUFS_RANGE[0] else "audio_loud")
        )
        return [{"kind": kind, "at": 0.0, "what": f"{lufs:.1f} LUFS · {peak:.1f} dBTP → -14 LUFS"}]
    return []
