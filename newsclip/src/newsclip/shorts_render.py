"""세로 쇼츠 프로젝트 + 음성 파일 → 완성 mp4.

프로젝트의 장면(사진·영상 구간·출처)과 대본은 그대로 쓰고, 자막은 받은 음성에 맞춰 새로 나눈다.
효과음(화제 전환·질문)은 장면 문장에서 찾고, 배경음악은 있을 때만 깐다.
"""

from __future__ import annotations

import hashlib
import json
import math
import re
import subprocess
from dataclasses import replace
from pathlib import Path

from PIL import Image

from newsclip.autosub import SUB_LINE, AudioInfo, align, analyze, script_phrases, subtitle_cues
from newsclip.cards import ZOOM as CARD_ZOOM_MAX
from newsclip.chart_hook import CIRCLE_HOLD, CIRCLE_SEC, circle_time, fit_lines, make_chart_clip, split_lines
from newsclip.models import Scene
from newsclip.render import (
    EMPH_GREEN,
    EMPH_RED,
    EMPH_YELLOW,
    QUOTE_FONT,
    RED,
    YELLOW,
    CueArt,
    Overlay,
    RenderPlan,
    Shot,
    fit_sub_px,
    is_hot,
    render,
    render_cue,
    render_emph,
)
from newsclip.sfx import HIT, HIT_EARLY, WHOOSH, ensure_sfx, sfx_cues
from newsclip.srt import Cue, parse_srt
from newsclip.vertical import BOX, box_of, style_of

SIZE = (1080, 1920)
HOOK_SEC = 3.5  # 훅: 이 시각 전에 시작하는 첫 문장 자막을 짧게 잘라 빠르게 보여 준다
HOOK_CHUNK = 6  # 훅 한 번에 보이는 글자 수(공백 빼고)
HOOK_PX = 140  # 훅 자막 글자 크기(px, 폭이 넘으면 자동으로 줄어든다)
HOOK_CENTER_Y = (BOX[1] + BOX[3]) // 2  # 훅 자막은 사진 칸 한가운데에 띄운다
HOOK_KEY_PX = 150  # 훅의 핵심(발언·행위) 강조 글자 크기
# 훅 문장의 핵심: 따옴표 안 발언, 없으면 문장 끝 행위(서술어). 발언 뒤가 '~고 했습니다'처럼 전하는 말뿐이면
# 발언을 훅이 끝날 때까지 띄워 둔다.
QUOTE_SPAN = re.compile("[\"“‘']([^\"“”‘’']{2,30})[\"”’']")
PREDICATE = re.compile("(다|요|니다|죠)[.!?…,]*$")
REPORT_VERB = re.compile(
    "^(고|라고|이라고|라며|며|란|라는)?\\s*(했|말했|밝혔|표현했|주장했|썼|적었|발언했|물었|외쳤|비판했|지적했)"
)
# 컷 편집: 장면 안에서 후보 사진을 바꿔 쓰는 때를 자막이 바뀌는 때에 맞춘다. 사진 한 장은 장면에서 한 번만
# 쓰고(같은 사진을 가깝게 다시 쓰는 펀치인은 정신없어서 뺐다), 컷마다 줌 인·줌 아웃을 번갈아 준다.
SHOT_SEC = 2.5  # 한 컷 목표 길이(장면의 사진이 모자라면 그만큼 길어진다)
SHOT_MIN = 1.2  # 이보다 짧은 컷은 만들지 않는다
PUSH = 0.06  # 한 컷 동안 다가가거나(줌 인) 물러나는(줌 아웃) 정도
FOCUS = (0.5, 0.33)  # 줌 기준점(사진 칸 위쪽 가운데 — 보통 얼굴 자리)
HOLD_ZOOM = (
    1.0,
    1.02,
)  # 훅이 나오는 동안의 사진: 바꾸지 않고 아주 살짝만 다가간다(글자가 읽히게 배경은 차분히)
XFADE_SEC = 0.16  # 화제가 바뀌는 장면에서 확대 펀치 전환(앞 컷이 빠르게 다가가고 다음 컷이 제자리로)
EMPH_MAX = 12  # 강조 자막(색 글자)이 될 수 있는 자막 길이(공백 빼고)
EMPH_GAP = 4.0  # 강조 자막 사이 최소 간격(초) — 너무 자주 나오면 강조가 아니다
# 디자인에 emph_sag가 있으면(뉴스 채널) 흐름이 늘어질 때만 하나씩: 넣을 때가 되면 그 뒤 이 시간 안의 후보 중
# 가장 센 것(빨강 → 노랑 → 초록)을 고른다
EMPH_LOOK = 3.0
# 센 말 → 빨강, 따옴표로 옮긴 말(직접 인용) → 노랑, 핵심 인물·키워드 → 초록
STRONG = re.compile(
    "폭로|막말|논란|의혹|충격|비판|비난|반발|구속|탄핵|거짓|위반|폭동|망언|파문|저격|고발|기소|경고|분노|격돌|최악"
    "|배신|자멸|민낯|죽이|막장|몰락|폭망|박살"
)
QUOTE = re.compile("[\"“”‘’']")
NEVER = re.compile("(?!)")  # 아무것도 맞지 않는 말(자동 강조 끄기)
# 건강(널스윤서) 강조어: 정치 채널의 센 말 대신 — 작업자가 고른 강조 문구(emphasis)가 없을 때 이 말이 든 낱말을 빨간 강조로
HEALTH_STRONG = re.compile(
    "위험|주의|경고|조심|꼭|반드시|절대|두 배|2배|세 배|3배|급증|사망|치매|뇌졸중|심근경색|돌연사|최악|놓치면|몰랐던|비상"
)


def probe_duration(path: Path) -> float:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "json", str(path)],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    return float(json.loads(out)["format"]["duration"])


def caption_name(attribution: str) -> str:
    """출처 표시 PNG 이름(newsclip.fcpxml과 같은 규칙: 기본 디자인 문구의 sha1 앞 8자리)."""
    return f"captions/cap_{hashlib.sha1(attribution.encode()).hexdigest()[:8]}.png"


def _photo_pool(sc: dict, asset_dir: Path) -> list[Path]:
    """장면의 사진 목록: 골라 쓴 사진 먼저, 그다음 같은 장면의 다른 후보 사진."""
    names = [sc["chosen"]] + [n for n in sc.get("files", []) if n != sc["chosen"] and n.startswith("assets/")]
    return [asset_dir / n for n in names if (asset_dir / n).exists()]


CHART_MIN = 2.5  # 차트판 훅을 적어도 이만큼(초) 보여 준다
CARD_ZOOM = (1.0, CARD_ZOOM_MAX)
CARD_MAX = 1.8  # 수치 카드 컷 길이 상한(초) — 숫자만 한눈에 보고 지나간다  # 수치 카드 컷: 1~2초 동안 강조한 줄로 성큼 다가간다


def is_card(path: Path) -> bool:
    """장면 자료가 수치 카드인가(newsclip.cards — <장면>_card.jpg)."""
    return path.stem.endswith("_card")


def card_focus(sc: dict) -> tuple[float, float]:
    f = sc.get("card_focus")
    return (float(f[0]), float(f[1])) if isinstance(f, list | tuple) and len(f) == 2 else (0.5, 0.4)


def is_clip(path: Path) -> bool:
    """장면 자료가 영상 조각인가(mp4)."""
    return path.suffix.lower() in (".mp4", ".mov", ".webm")


def _pieces(start: float, end: float, max_sec: float | None) -> list[tuple[float, float]]:
    """장면을 max_sec 이하 조각으로 나눈다(사진 간격 줄이기). max_sec가 없으면 그대로."""
    if not max_sec or end - start <= max_sec:
        return [(start, end)]
    k = math.ceil((end - start) / max_sec)
    step = (end - start) / k
    return [(start + i * step, start + (i + 1) * step) for i in range(k)]


def cut_pieces(
    start: float, end: float, cuts: list[float], k: int, min_len: float = SHOT_MIN
) -> list[tuple[float, float]]:
    """장면 구간을 k컷으로 나눈다. 나누는 자리는 고르게 나눈 시각에 가장 가까운 자막 경계(cuts) — 말 중간에서
    사진이 바뀌지 않게. 알맞은 경계가 없으면 그 자리는 나누지 않는다."""
    if k <= 1:
        return [(start, end)]
    points: list[float] = []
    prev = start
    for j in range(1, k):
        target = start + (end - start) * j / k
        cands = [c for c in cuts if c - prev >= min_len and end - c >= min_len * (k - j)]
        if cands:
            prev = min(cands, key=lambda c: abs(c - target))
            points.append(prev)
    edges = [start, *points, end]
    return list(zip(edges, edges[1:], strict=False))


def shots_for(
    scenes: list[dict],
    asset_dir: Path,
    total: float,
    videos: dict[str, Path],
    max_shot_sec: float | None = None,
    cuts: list[float] | None = None,
    xfade_at: list[float] = (),
    hold_until: float = 0.0,
) -> list[Shot]:
    """장면 → 화면 조각. 장면 사이 틈은 앞 조각을 늘려 메운다. videos[장면 id]가 있으면 그 장면은 영상(길이 안)으로.
    cuts(자막이 바뀌는 시각)를 주면 컷 편집: 장면의 후보 사진을 SHOT_SEC초 안팎으로 하나씩 바꿔 쓰되 바꾸는
    때를 자막 경계에 맞추고, 컷마다 줌 인·아웃을 번갈아 준다. 화제가 바뀌는 장면이나 xfade_at(넘기는 효과음
    시각)에서 시작하는 장면의 첫 컷은 확대 펀치 전환으로 넘어간다. hold_until(훅이 끝나는 때)까지는 사진을 바꾸지
    않고 그 컷은 HOLD_ZOOM으로 아주 살짝만 움직인다.
    cuts가 없으면 max_shot_sec보다 길게 나오지 않도록만 나눈다(같은 장면의 다른 사진을 번갈아 쓴다)."""
    shots: list[Shot] = []
    count = 0  # 줌 방향을 조각 순서대로 번갈아 쓰려고 센다(나누지 않으면 장면 번호와 같다)
    topic = None
    for sc in sorted(scenes, key=lambda s: s["start"]):
        start, end = float(sc["start"]), float(sc["end"])
        new_topic = topic is not None and sc.get("topic") != topic
        topic = sc.get("topic")
        clip = videos.get(sc["id"])
        if clip is not None:
            count += 1
            span = min(end - start, probe_duration(clip))
            shots.append(Shot(clip, "video", start, start + span, in_sec=0.0))
            if start + span < end:
                zoom = (1.0, 1.04) if count % 2 else (1.04, 1.0)
                shots.append(Shot(asset_dir / sc["chosen"], "image", start + span, end, zoom=zoom))
            continue
        pool = _photo_pool(sc, asset_dir) or [asset_dir / sc["chosen"]]
        if cuts is not None:
            k = min(len(pool), max(1, round((end - start) / SHOT_SEC)))
            turn = new_topic or any(abs(start - t) < 0.05 for t in xfade_at)
            usable = [c for c in cuts if c >= hold_until - 0.05]  # 훅 동안은 사진을 바꾸지 않는다
            pieces = cut_pieces(start, end, usable, k)
            if is_card(pool[0]) and len(pool) > 1 and pieces[0][1] - pieces[0][0] > CARD_MAX + 0.8:
                # 수치 카드는 지나가는 컷: CARD_MAX초만 보여 주고 그 컷의 나머지는 장면의 다음 자료로
                a0, b0 = pieces[0]
                pieces = [(a0, a0 + CARD_MAX), (a0 + CARD_MAX, b0), *pieces[1:]][: len(pool)]
                if pieces[-1][1] < end:  # 자료가 모자라 잘린 끝은 마지막 컷을 늘려 메운다
                    pieces[-1] = (pieces[-1][0], end)
            for i, (a, b) in enumerate(pieces):
                count += 1
                # 영상 조각(무료 소재 — 받을 때 사진 칸에 맞춰 다듬어 둠): 줌 없이 그대로
                if is_clip(pool[i]):
                    shots.append(Shot(pool[i], "video", a, b, in_sec=0.0))
                    continue
                if is_card(pool[i]):  # 수치 카드: 강조한 줄·숫자 쪽으로 다가간다
                    shots.append(Shot(pool[i], "image", a, b, zoom=CARD_ZOOM, focus=card_focus(sc)))
                    continue
                zoom = (1.0, 1.0 + PUSH) if count % 2 else (1.0 + PUSH, 1.0)
                if a < hold_until:
                    zoom = HOLD_ZOOM
                xf = XFADE_SEC if (i == 0 and turn and start > 0) else 0.0
                shots.append(Shot(pool[i], "image", a, b, zoom=zoom, focus=FOCUS, xfade=xf))
            continue
        for i, (a, b) in enumerate(_pieces(start, end, max_shot_sec)):
            count += 1
            zoom = (
                (1.0, 1.04) if count % 2 else (1.04, 1.0)
            )  # 홀수 조각 줌인, 짝수 줌아웃(fcpxml과 같은 규칙)
            photo = pool[i % len(pool)]
            if is_clip(photo):
                shots.append(Shot(photo, "video", a, b, in_sec=0.0))
                continue
            shots.append(Shot(photo, "image", a, b, zoom=zoom))
    if shots:
        shots[0] = replace(shots[0], start=0.0)
        shots[-1] = replace(shots[-1], end=total)
    filled: list[Shot] = []
    for shot in shots:
        if filled and filled[-1].end < shot.start:
            filled[-1] = replace(filled[-1], end=shot.start)
        filled.append(shot)
    return [s for s in filled if s.end > s.start]


def after(shots: list[Shot], at: float) -> list[Shot]:
    """at 뒤의 컷만(at에 걸친 컷은 at부터 — 영상은 그만큼 뒤에서 시작)."""
    out = []
    for s in shots:
        if s.end <= at + 1e-6:
            continue
        if s.start < at:
            cut = at - s.start
            s = replace(s, start=at, in_sec=s.in_sec + cut if s.kind == "video" else s.in_sec, xfade=0.0)
        out.append(s)
    return out


def default_key(sentence: str) -> str | None:
    """차트판 훅에서 동그라미 칠 말을 정해 주지 않았을 때: 문장 끝 행위(서술어 쪽, 9자 안쪽) 또는 따옴표 안 발언."""
    key = hook_key(sentence)
    return key[1] if key and len(key[1].replace(" ", "")) <= 9 else None


def wrap_hook(text: str, limit: int = 8) -> str:
    """훅 자막은 크게 띄우므로 한 줄을 공백 뺀 8글자 안쪽으로 다시 나눈다."""
    lines: list[str] = []
    cur = ""
    for word in text.replace("\n", " ").split():
        cand = f"{cur} {word}".strip()
        if cur and len(cand.replace(" ", "")) > limit:
            lines.append(cur)
            cur = word
        else:
            cur = cand
    if cur:
        lines.append(cur)
    return "\n".join(lines)


def hook_chunks(text: str, limit: int = HOOK_CHUNK) -> list[str]:
    """훅 자막 한 줄 → 짧은 조각들(낱말을 이어 붙이되 공백 빼고 limit자 안쪽). 긴 낱말은 혼자 한 조각."""
    out: list[str] = []
    cur = ""
    for word in text.replace("\n", " ").split():
        cand = f"{cur} {word}".strip()
        if cur and len(cand.replace(" ", "")) > limit:
            out.append(cur)
            cur = word
        else:
            cur = cand
    if cur:
        out.append(cur)
    return out


_SENT_END = re.compile(r"[.!?…][\"'”’」』)]*$")
_PAIRS = {'"': '"', "'": "'", "“": "”", "‘": "’"}


def pair_quotes(texts: list[str]) -> list[str]:
    """자막 조각마다 따옴표 짝을 맞춘다: 인용 하나가 두 자막에 걸치면 앞 조각 끝에 닫는 따옴표, 뒤 조각 앞에
    여는 따옴표를 붙인다(한쪽 따옴표만 있으면 인용이 끊겨 보인다)."""
    out: list[str] = []
    inside: tuple[str, str] | None = None  # 지금 열려 있는 인용의 (여는, 닫는) 따옴표
    for text in texts:
        shown = inside[0] + text if inside else text
        for ch in text:
            if inside is None:
                if ch in _PAIRS:
                    inside = (ch, _PAIRS[ch])
            elif ch == inside[1]:
                inside = None
        out.append(shown + inside[1] if inside else shown)
    return out


def emph_color(text: str, keywords: set[str], strong: re.Pattern = STRONG) -> tuple[int, int, int] | None:
    """짧은 자막 중 강조할 것의 색: 센 말 → 빨강, 직접 인용 → 노랑, 핵심 키워드(숫자 말고) → 초록, 아니면 None."""
    core = re.sub(r"\s+", "", text)
    if not core or len(core) > EMPH_MAX:
        return None
    if strong.search(core):
        return EMPH_RED
    if QUOTE.search(core):
        return EMPH_YELLOW
    if any(is_hot(w, keywords) and not is_hot(w, set()) for w in text.split()):
        return EMPH_GREEN
    return None


def hook_key(sentence: str) -> tuple[str, str] | None:
    """훅 문장의 핵심 → ("quote", 따옴표 안 발언) 또는 ("act", 문장 끝 행위 — 서술어와 앞 낱말 하나, 9자 안쪽).
    서술어로 끝나지 않는 문장(훅이 문장 중간에서 끝남)은 None."""
    m = QUOTE_SPAN.search(sentence)
    if m:
        return ("quote", m.group(1).strip())
    words = sentence.split()
    if not words or not PREDICATE.search(words[-1]):
        return None
    last = re.sub("[.,…]+$", "", words[-1])
    if len(words) >= 2 and len(words[-2] + last) <= 9:
        return ("act", f"{words[-2]} {last}")
    return ("act", last)


def hook_arts(
    cues: list, asset_dir: Path, size: tuple[int, int], keywords: set[str], words: list | None
) -> list[CueArt]:
    """훅(첫 문장) 자막: 앞부분은 짧게 잘라 빠르게, 핵심(발언은 "따옴표"째 손글씨 노랑, 행위는 빨강)은 그 말이
    들리는 순간 사진 칸 가운데에 크게 띄운다. 핵심 뒤가 '~고 했습니다'처럼 전하는 말뿐이면 핵심을 훅 끝까지 둔다."""
    start, end = cues[0].start, cues[-1].end
    text = " ".join(c.text.strip() for c in cues)
    key = hook_key(text)
    pieces: list[tuple[str, str]] = []  # (글, 종류: chunk | quote | act)
    if key is not None and key[1] in text:
        kind, phrase = key
        at = text.index(phrase)
        pre = text[:at].rstrip(" \"“‘'")
        post = text[at + len(phrase) :].lstrip("\"”’'")
        pieces = [(p, "chunk") for p in hook_chunks(pre)] + [(phrase, kind)]
        if len(re.sub("[\\s.!?…,]", "", post)) > 5 and not REPORT_VERB.match(post.strip()):
            pieces += [(p, "chunk") for p in hook_chunks(post)]
    else:
        pieces = [(p, "chunk") for p in hook_chunks(text)]
    starts = chunk_starts([p for p, _k in pieces], start, end, words)
    arts: list[CueArt] = []
    for j, (piece, kind) in enumerate(pieces):
        at, until = starts[j], (starts[j + 1] if j + 1 < len(pieces) else end)
        out = asset_dir / "subtitles" / f"hook_{j:02d}.png"
        if kind == "quote":
            art = render_emph(
                f"“{piece}”",
                out,
                size,
                color=EMPH_YELLOW,
                px=HOOK_KEY_PX,
                font_path=QUOTE_FONT,
                center_y=HOOK_CENTER_Y,
            )
        elif kind == "act":
            art = render_emph(piece, out, size, color=EMPH_RED, px=HOOK_KEY_PX, center_y=HOOK_CENTER_Y)
        else:
            art = render_cue(
                piece,
                out,
                size,
                keywords=keywords,
                px=HOOK_PX,
                center_y=HOOK_CENTER_Y,
                text_color=YELLOW,
                hot_color=RED,
            )
        if art is not None:
            arts.append(_timed(art, at, until))
    return arts


def chunk_starts(pieces: list[str], start: float, end: float, words: list | None) -> list[float]:
    """훅 조각마다 뜨는 시각. 낱말 시각(타입캐스트나 음성 인식)이 있으면 그 조각의 말이 들리는 때에 맞추고,
    없거나 못 찾은 조각은 글자 수 비율로 나눈다."""
    from newsclip.autosub import asr_boundaries

    weights = [max(1, len(p.replace(" ", ""))) for p in pieces]
    total = sum(weights)
    starts = [start + (end - start) * sum(weights[:j]) / total for j in range(len(pieces))]
    near = [w for w in words or [] if w[2] > start - 0.3 and w[1] < end + 0.3]
    if near and len(pieces) > 1:
        heard = asr_boundaries(pieces, [(str(w[0]), float(w[1]), float(w[2])) for w in near])
        for j in range(1, len(pieces)):
            h = heard[j]
            if h is not None and start < h[0] < end:
                starts[j] = h[0]
    for j in range(1, len(starts)):  # 순서가 뒤집히지 않게, 한 조각은 0.1초 이상
        starts[j] = max(starts[j], starts[j - 1] + 0.1)
    return starts


def pick_emphasis(
    cues: list, keywords: set[str], start: int = 0, sag: float = 0.0, strong: re.Pattern = STRONG
) -> dict[int, tuple[int, int, int]]:
    """강조 자막 고르기. 반환: {자막 번호: 색}. start 앞(훅)은 고르지 않는다. strong은 빨강으로 강조할 센 말(채널마다).
    sag가 0이면 빨강 → 노랑 → 초록 순으로, 이미 고른 강조와 EMPH_GAP초 안에 붙지 않는 것만.
    sag가 있으면 흐름이 늘어질 때만 하나씩: 훅 끝(또는 영상 시작)이나 앞 강조에서 sag초가 지난 뒤 처음 나오는 후보
    때부터 EMPH_LOOK초 안의 후보 중 가장 센 것(같으면 먼저 나오는 것)."""
    order = (EMPH_RED, EMPH_YELLOW, EMPH_GREEN)
    cands = [
        (k, col) for k, c in enumerate(cues) if k >= start and (col := emph_color(c.text, keywords, strong))
    ]
    picked: dict[int, tuple[int, int, int]] = {}
    if sag <= 0:
        for k, col in sorted(cands, key=lambda kc: (order.index(kc[1]), kc[0])):
            if all(abs(cues[k].start - cues[j].start) >= EMPH_GAP for j in picked):
                picked[k] = col
        return picked
    last = cues[start - 1].end if 0 < start <= len(cues) else 0.0
    i = 0
    while i < len(cands):
        t = cues[cands[i][0]].start
        if t - last < sag:
            i += 1
            continue
        window = [j for j in range(i, len(cands)) if cues[cands[j][0]].start - t <= EMPH_LOOK]
        best = min(window, key=lambda j: (order.index(cands[j][1]), j))
        k, col = cands[best]
        picked[k] = col
        last = cues[k].start
        i = best + 1
    return picked


def phrase_in(text: str, phrases: list[str], strong: re.Pattern | None) -> str | None:
    """긴 자막 안에서 강조할 문구: 작업자가 고른 문구(phrases) 중 이 자막에 든 것, 없으면 strong 말이 든 낱말.
    EMPH_MAX자를 넘는 문구, 자막 전체와 같은 문구는 고르지 않는다(자막 전체는 pick_emphasis가 맡는다)."""
    core = re.sub(r"\s+", "", text)
    for ph in phrases:
        ph = ph.strip()
        if ph and ph in text and len(ph.replace(" ", "")) <= EMPH_MAX and ph.replace(" ", "") != core:
            return ph
    if strong is not None:
        for w in text.split():
            bare = w.strip(".,!?…\"'“”‘’")
            if bare and strong.search(bare) and bare.replace(" ", "") != core:
                return bare
    return None


def split_emphasis(cue: Cue, phrase: str, words: list | None) -> list[tuple[str, float, float, bool]]:
    """자막 하나를 강조 문구 앞·문구·뒤로 나눈다 → [(글, 시작, 끝, 강조?)]. 시각은 그 말이 들리는 때(낱말 시각,
    없으면 글자 수 비율)."""
    at = cue.text.index(phrase)
    parts = [(cue.text[:at].strip(), False), (phrase, True), (cue.text[at + len(phrase) :].strip(), False)]
    parts = [(t, e) for t, e in parts if re.sub("[\\s.,!?…]", "", t)]
    starts = chunk_starts([t for t, _e in parts], cue.start, cue.end, words)
    ends = [*starts[1:], cue.end]
    return [(t, a, b, e) for (t, e), a, b in zip(parts, starts, ends, strict=True)]


def subtitles_for(
    script: str,
    audio: Path,
    size: tuple[int, int],
    asset_dir: Path,
    keywords: set[str],
    srt: Path | None = None,
    words: list | None = None,
    hook_mode: str = "flash",
    sub_shift: int = 0,
    emph_sag: float = 0.0,
    quote_pairs: bool = False,
    emphasis: list[str] | None = None,
    strong: re.Pattern = STRONG,
) -> tuple[list[CueArt], list[tuple[float, float]], AudioInfo, tuple[str, float, float] | None]:
    """대본 + 음성 → 자막 그림 목록(음성 쉼에 맞춘 한 줄씩). words(타입캐스트 낱말 시각)가 있으면 그 시각에 맞춘다.
    hook_mode "chart"(널스윤서)면 첫 문장 자막을 만들지 않는다 — 그동안 화면은 차트판 훅(chart_hook)이 첫 문장을
    손글씨로 쓴다. sub_shift만큼 자막을 아래로 옮긴다(사진 칸이 다른 디자인). emph_sag는 강조 자막 고르기
    (pick_emphasis), quote_pairs면 훅 뒤 자막의 따옴표 짝을 맞춘다(pair_quotes). 마지막 값은 첫 문장 (글, 시작, 끝).
    emphasis(강조 문구 목록, 널스윤서)를 주면 긴 자막 안의 그 문구를 잘라 강조 자막(정치 채널과 같은 효과)으로 띄운다 —
    자막을 문구 앞·문구·뒤로 나눠 그 말이 들리는 때에 차례로. strong은 강조할 센 말(채널마다)."""
    info = analyze(audio)
    if srt is not None:
        # 받은 자막(SRT)을 그대로 쓴다: 제작실에서 본 자막과 같은 시간
        lines = parse_srt(srt.read_text(encoding="utf-8"))
    else:
        cues, _matched = align(script_phrases(script), info, words=words)
        lines = subtitle_cues(cues, limit=SUB_LINE, info=info, words=words)
    # 훅: 첫 문장(HOOK_SEC 전에 시작하는 자막, 문장이 끝나는 자막까지)은 짧게 잘라 빠르게 이어 보여 준다
    hook_n = 0
    for cue in lines:
        if cue.start >= HOOK_SEC:
            break
        hook_n += 1
        if _SENT_END.search(cue.text.strip()):
            break
    px = fit_sub_px([cue.text for cue in lines[hook_n:]] or [""], size)
    arts = []
    # 작업자가 강조 문구를 골랐으면 그 문구만 강조한다(센 말 자동 강조는 고른 문구가 없을 때만)
    chosen = [e for e in emphasis or [] if e.strip()]
    if chosen:
        strong = NEVER
    emph = pick_emphasis(lines, keywords, start=hook_n, sag=emph_sag, strong=strong)
    # 훅 자막은 첫 문장을 이어 붙여 핵심(따옴표 안 발언)을 찾으므로 그대로 두고, 그 뒤 자막만 짝을 맞춘다
    texts = pair_quotes([c.text for c in lines]) if quote_pairs else [c.text for c in lines]
    split: dict[int, str] = {}  # 자막 번호 → 그 안에서 강조할 문구
    if emphasis is not None:
        for k, cue in enumerate(lines):
            if k < hook_n or k in emph:
                continue
            near = [lines[j].start for j in [*emph, *split]]
            if any(abs(cue.start - t) < EMPH_GAP for t in near):
                continue
            core = re.sub(r"\s+", "", cue.text.strip(".,!?… "))
            if any(re.sub(r"\s+", "", ph) == core for ph in emphasis):  # 자막 한 줄이 곧 강조 문구 → 줄째로
                emph[k] = EMPH_RED
                continue
            ph = phrase_in(cue.text, emphasis, None if chosen else strong)
            if ph:
                split[k] = ph
    hook = (
        (" ".join(c.text.strip() for c in lines[:hook_n]), lines[0].start, lines[hook_n - 1].end)
        if hook_n
        else None
    )
    if hook_n and hook_mode != "chart":
        arts += hook_arts(lines[:hook_n], asset_dir, size, keywords, words)
    for k, cue in enumerate(lines):
        if k < hook_n:
            continue
        if k in split:  # 긴 자막 안 강조 문구: 앞은 보통 자막, 문구는 강조 자막, 뒤는 다시 보통 자막
            for j, (text, a, b, hot) in enumerate(split_emphasis(cue, split[k], words)):
                name = asset_dir / "subtitles" / f"{'emph' if hot else 'cue'}_{k:03d}_{j}.png"
                part = (
                    render_emph(text, name, size, color=EMPH_RED)
                    if hot
                    else render_cue(text, name, size, keywords=keywords, px=px)
                )
                if part is not None:
                    arts.append(replace(_timed(part, a, b), top=part.top + sub_shift))
            continue
        if k in emph:
            # 직접 인용(노랑)은 손글씨 글꼴로 — 그 사람이 한 말처럼 보이게
            font = QUOTE_FONT if emph[k] == EMPH_YELLOW else None
            out = asset_dir / "subtitles" / f"emph_{k:03d}.png"
            art = render_emph(texts[k], out, size, color=emph[k], font_path=font)
        else:
            art = render_cue(
                texts[k], asset_dir / "subtitles" / f"cue_{k:03d}.png", size, keywords=keywords, px=px
            )
        if art is not None:
            arts.append(replace(_timed(art, cue.start, cue.end), top=art.top + sub_shift))
    return arts, [(cue.start, cue.end) for cue in lines], info, hook


def _timed(art: CueArt, start: float, end: float) -> CueArt:
    return replace(art, start=start, end=end)


def build_plan(
    project: dict,
    asset_dir: Path,
    audio: Path,
    *,
    srt: Path | None = None,
    bgm: Path | None = None,
    videos: dict[str, Path] | None = None,
    keywords: set[str] = frozenset(),
    max_shot_sec: float | None = None,
    words: list | None = None,
    style: str = "default",
) -> RenderPlan:
    """프로젝트 dict(script, thumb, scenes) + 자료 폴더 + 음성 → 렌더 계획."""
    from newsclip.autosub import wav_duration

    total = wav_duration(audio)
    scenes = project["scenes"]
    if words is None:
        # 받은 음성이면 음성 인식으로 낱말 시각을 얻어 훅 조각을 실제 말에 맞춘다(같은 음성은 저장해 둔 결과)
        try:
            from newsclip.asr import transcribe

            words = transcribe(audio, cache_dir=asset_dir)
        except Exception:  # noqa: BLE001 - 듣지 못하면 글자 수 비율로
            words = None
    # 핵심어: 호출자가 준 말 + 첫 장면 검색어의 말(훅에서 빨간색으로 강조)
    first = min(scenes, key=lambda s: float(s["start"]))
    # 검색어는 사진을 찾는 말이라 다 강조하면 빨강·노랑이 넘친다 — 첫 낱말(보통 인물 이름) 하나만 핵심어로
    query_words = {w for w in str(first.get("query", "")).split()[:1] if len(w) >= 2}
    st = style_of(style)
    cues, _spans, _info, hook = subtitles_for(
        project["script"],
        audio,
        SIZE,
        asset_dir,
        set(keywords) | query_words,
        srt=srt,
        words=words,
        hook_mode=st.hook,
        sub_shift=st.sub_shift,
        emph_sag=st.emph_sag,
        quote_pairs=st.quote_pairs,
        emphasis=list(project.get("emphasis") or []) if st.hook == "chart" else None,
        strong=HEALTH_STRONG if st.hook == "chart" else STRONG,
    )
    cues = [c for c in cues if c.start < total]
    # 컷 편집: 자막이 바뀌는 때(훅 조각 포함)에 맞춰 사진을 바꾼다. max_shot_sec를 주면 예전 방식(길이로만 나눔)
    cut_at = None if max_shot_sec else sorted({round(c.start, 3) for c in cues if c.start > 0})
    scene_models = [
        Scene(
            id=sc["id"],
            start_sec=float(sc["start"]),
            end_sec=float(sc["end"]),
            text=sc["text"],
            topic=sc["topic"],
        )
        for sc in scenes
    ]
    sfx_dir = ensure_sfx(asset_dir, fetch=True)
    sounds = [cue for cue in sfx_cues(scene_models) if cue.at < total]
    sfx = [(cue.at, sfx_dir[cue.name]) for cue in sounds]
    # 강조 자막(색 글자)이 뜰 때 짧은 팝 — 말 직전 쉼에 떨어지게 HIT_EARLY만큼 먼저. 다른 효과음과 0.5초 안이면 빼고
    for at in [c.start for c in cues if c.intro]:
        if all(abs(at - a) >= 0.5 for a, _p in sfx):
            sfx.append((max(0.0, at - HIT_EARLY), sfx_dir[HIT]))
    sfx.sort()
    # 넘기는 효과음(whoosh)이 나는 장면은 화면도 확대 펀치 전환으로 넘긴다
    whoosh = [cue.at for cue in sounds if cue.name == "whoosh"]
    hook_end = max((c.end for c in cues if c.path.name.startswith("hook_")), default=0.0)
    chart = st.hook == "chart" and hook is not None
    if chart:
        # 차트판은 첫 문장 끝까지, 짧으면 CHART_MIN초까지, 그리고 동그라미를 다 그린 뒤 CIRCLE_HOLD초까지는 둔다
        spec = project.get("hook") or {}
        key = spec.get("key") or default_key(hook[0])
        lines = fit_lines(spec.get("lines") or split_lines(hook[0]), key)
        starts = chunk_starts(" ".join(lines).split(), hook[1], hook[2], words)
        circle = circle_time(lines, key, starts)
        need = max(CHART_MIN, circle + CIRCLE_SEC + CIRCLE_HOLD if circle is not None else 0.0)
        # 자막 경계까지 늘리지 않는다(다음 문장 자막이 통째로 가려진다) — 걸친 자막은 차트판이 끝나면 바로 뜬다
        hook_end = min(max(hook[2], need), total)
        # 차트판이 떠 있는 동안은 자막을 띄우지 않는다(손글씨를 가린다) — 걸친 자막은 차트판이 끝나면 뜬다
        cues = [replace(c, start=max(c.start, hook_end)) for c in cues if c.end > hook_end + 0.2]
    shots = shots_for(
        scenes,
        asset_dir,
        total,
        videos or {},
        max_shot_sec,
        cuts=cut_at,
        xfade_at=whoosh,
        hold_until=hook_end,
    )
    if chart:  # 첫 문장 동안은 차트판 훅 영상 하나 — 그 뒤 컷부터 사진·영상
        out = asset_dir / "subtitles" / "chart_hook.mp4"
        clip, punch_at, circle_at = make_chart_clip(
            hook[0], key, starts, hook_end, out, SIZE, style, lines=lines
        )
        if (
            punch_at is not None and punch_at < hook_end
        ):  # 펀치인에 휙 소리(소리가 가장 클 때 당김이 끝나게 조금 먼저)
            sfx.append((max(0.0, punch_at - 0.12), sfx_dir[WHOOSH]))
        shots = [Shot(clip, "video", 0.0, hook_end, in_sec=0.0), *after(shots, hook_end)]
        if circle_at is not None and circle_at < hook_end:  # 동그라미 칠 때 짧은 팝
            sfx.append((max(0.0, circle_at - HIT_EARLY), sfx_dir[HIT]))
            sfx.sort()
    caps = []
    for k, sc in enumerate(scenes):
        src = asset_dir / caption_name(sc["attribution"]) if sc.get("attribution") else None
        if src is None or not src.exists():
            continue
        # 출처 그림은 전체 화면 크기라서, 글자가 있는 부분만 잘라 쓴다(렌더 속도)
        img = Image.open(src).convert("RGBA")
        box = img.getchannel("A").getbbox()
        if box is None:
            continue
        cut = asset_dir / "subtitles" / f"cap_{k:03d}.png"
        cut.parent.mkdir(parents=True, exist_ok=True)
        img.crop(box).save(cut, "PNG")
        caps.append(Overlay(cut, float(sc["start"]), float(sc["end"]), x=box[0], y=box[1]))
    head = next(iter(sorted((asset_dir / "overlays").glob("headline_*.png"))), None)
    return RenderPlan(
        shots=shots,
        total=total,
        narration=audio,
        size=SIZE,
        headline=head,
        captions=caps,
        cues=cues,
        sfx=sfx,
        bgm=bgm,
        photo_box=box_of(st),
        photo_fade=_photo_fade(asset_dir, style),
        peak=st.peak,
        bgm_volume=st.bgm_volume,
        duck=st.duck,
    )


def fade_color(st) -> tuple[int, int, int]:  # noqa: ANN001
    """사진 아랫부분이 이어지는 바탕 색(보통 검정, 널스윤서는 흰색)."""
    return st.under_bg if st.top_bg is not None else (0, 0, 0)


def _photo_fade(asset_dir: Path, style: str = "default") -> Path | None:
    """사진 칸 아래를 바탕 검정으로 잇는 그림(확대해도 사진 아래 끝이 늘 부드럽게 사라지게). 디자인에 없으면 None."""
    from newsclip.vertical import _fade_mask, style_of

    st = style_of(style)
    if not st.photo_fade:
        return None
    x0, y0, x1, y1 = box_of(st)
    mask = _fade_mask((x1 - x0, y1 - y0), st.photo_fade)
    img = Image.new("RGBA", mask.size, (*fade_color(st), 0))  # 바탕 색으로 잇는다
    img.putalpha(mask.point(lambda v: 255 - v))
    out = asset_dir / "subtitles" / "photo_fade.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG")
    return out


def refresh_design(
    asset_dir: Path, headline: str, style: str = "default", font_path: str | None = None
) -> int:
    """자료를 모은 뒤 디자인이 바뀌었으면 지금 디자인으로 다시 맞춘다: 사진 칸 바깥(바탕)이 지금 디자인과 다른 사진은
    사진 칸만 잘라 다시 합성하고, 상단 제목 그림은 지금 디자인으로 다시 그린다(font_path: 패키지 폰트가 없는 디자인의
    상단 제목 폰트 — 자료 모을 때 받아 둔 Pretendard ExtraBold). 다시 합성한 사진 수를 돌려준다."""
    import numpy as np

    from newsclip.vertical import compose_photo, local_font, plain_background, render_headline, style_of

    st = style_of(style)
    bg = plain_background(SIZE, st)
    n = 0
    box = box_of(st)
    if bg is not None:
        ref = np.asarray(bg.crop((0, 0, SIZE[0], box[1])), dtype=np.int16)
        for p in sorted((asset_dir / "assets").glob("*.jpg")):
            im = Image.open(p).convert("RGB")
            if im.size != SIZE:
                continue
            top = np.asarray(im.crop((0, 0, SIZE[0], box[1])), dtype=np.int16)
            if np.abs(top - ref).mean() > 12:  # 바탕이 지금 디자인과 다르다(예전 디자인으로 모은 자료)
                compose_photo(im.crop(box), style=st.name).save(p, quality=92)
                n += 1
    if headline.strip():
        for old in (asset_dir / "overlays").glob("headline_*.png"):
            old.unlink()
        render_headline(
            headline.strip(),
            asset_dir / "overlays" / "headline_now.png",
            SIZE,
            local_font(st) or font_path,  # 패키지 폰트가 없는 디자인(널스윤서)은 받아 둔 Pretendard ExtraBold
            style=st.name,
        )
    return n


def render_short(
    project: dict,
    asset_dir: Path,
    audio: Path,
    out: Path,
    *,
    srt: Path | None = None,
    bgm: Path | None = None,
    videos: dict[str, Path] | None = None,
    keywords: set[str] = frozenset(),
    max_shot_sec: float | None = None,
    words: list | None = None,
    crf: int = 18,
    style: str = "default",
) -> Path:
    plan = build_plan(
        project,
        asset_dir,
        audio,
        srt=srt,
        bgm=bgm,
        videos=videos,
        keywords=keywords,
        max_shot_sec=max_shot_sec,
        words=words,
        style=style,
    )
    return render(replace(plan, crf=crf), out)


def voice_and_render(
    project: dict,
    asset_dir: Path,
    out: Path,
    *,
    voice_id: str,
    tempo: float = 1.0,
    remove_silence_ms: int | None = None,
    bgm: Path | None = None,
    max_shot_sec: float | None = None,
    keywords: set[str] = frozenset(),
    client=None,  # noqa: ANN001 - 시험용 httpx.Client
) -> Path:
    """대본을 타입캐스트로 읽고(속도·쉼 조절), 장면 시간을 새 음성에 다시 맞춘 뒤 완성 mp4를 만든다."""
    from newsclip.autosub import narration_text
    from newsclip.typecast_tts import retime_scenes, synthesize

    speech = synthesize(
        narration_text(project["script"]),
        voice_id,
        asset_dir / "narration_typecast.wav",
        tempo=tempo,
        remove_silence_ms=remove_silence_ms,
        client=client,
    )
    retimed = {**project, "scenes": retime_scenes(project["scenes"], speech.chars, speech.duration)}
    return render_short(
        retimed,
        asset_dir,
        speech.path,
        out,
        bgm=bgm,
        keywords=keywords,
        max_shot_sec=max_shot_sec,
        words=speech.words,
    )
