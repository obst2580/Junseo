"""script.txt의 `## 헤더` + SRT 타임코드 → 장면 분할, 장면 키워드 생성.

1) SRT 큐를 대본 줄과 순서 보존 퍼지 정렬해 주제(헤더) 경계를 잡는다.
2) 주제 안에서 2~4문장 또는 4~8초 단위로 장면을 나눈다.
3) 장면마다 kind와 한국어·영어 키워드 3개(고유명사 우선)를 만든다.
Claude가 있으면 2)~3)을 한 번의 배치 호출로 하고, 결과가 규칙을 어기면 휴리스틱으로 대체한다.
"""

from __future__ import annotations

import difflib
import json
import re
from collections import Counter
from dataclasses import dataclass
from typing import Literal

from pydantic import BaseModel

from newsclip.models import Scene, SceneKind
from newsclip.srt import Cue
from studio_core.llm import ClaudeLLM, LLMError
from studio_core.text import STOPWORDS, tokenize

SCENES_TASK = """\
너는 정치 해설 유튜브 영상의 편집자다. 내레이션 자막(SRT 큐)을 영상 장면으로 나눈다.
- 같은 주제(topic) 안에서 2~4문장 또는 4~8초 단위. 주제 경계는 넘지 않는다.
- 모든 큐는 정확히 한 장면에 순서대로 들어간다(cue_ids 연속).
- kind: photo(인물·기관·장소) / video(발언·회의·현장) / broll(분위기).
- keywords_ko 3개, keywords_en 3개. 실제 인물·기관·장소가 나오면 고유명사를 우선한다.
- 키워드는 이미지·영상 검색어로 쓰인다. 추상어보다 화면에 보이는 대상을 쓴다.
"""

# 고유명사(기관·장소) → 영어 검색어
PROPER_NOUNS: dict[str, str] = {
    "국회 본회의": "National Assembly plenary session Korea",
    "본회의": "National Assembly plenary session Korea",
    "법제사법위원회": "National Assembly Legislation and Judiciary Committee",
    "법사위": "National Assembly Legislation and Judiciary Committee",
    "국방위": "National Assembly National Defense Committee",
    "국정감사": "National Assembly audit Korea",
    "국회": "National Assembly Korea",
    "대통령실": "Korea Presidential Office",
    "국무회의": "Korea cabinet meeting",
    "서울중앙지법": "Seoul Central District Court",
    "대법원": "Supreme Court of Korea",
    "헌법재판소": "Constitutional Court of Korea",
    "법무부": "Ministry of Justice Korea",
    "국방부": "Ministry of National Defense Korea",
    "고용노동부": "Ministry of Employment and Labor Korea",
    "기획재정부": "Ministry of Economy and Finance Korea",
    "보건복지부": "Ministry of Health and Welfare Korea",
    "한국은행": "Bank of Korea",
    "검찰": "Supreme Prosecutors Office Korea",
    "안보리": "UN Security Council",
    "유엔": "United Nations",
    "백악관": "White House",
    "상원": "United States Senate",
    "하원": "United States House of Representatives",
    "북한": "North Korea",
    "택배노조": "delivery workers union Korea",
    "응급실": "emergency room hospital Korea",
    "공공의대": "public medical school Korea",
    "환율": "won dollar exchange rate",
    "코스피": "KOSPI stock index",
    "뉴욕증시": "Wall Street stock exchange",
    "나스닥": "Nasdaq",
    "국제유가": "crude oil price",
    "서울": "Seoul",
}
# 대본 구조 헤더에서 온 말(검색어로 쓰면 엉뚱한 결과)
STRUCTURAL = {
    "아침",
    "포인트",
    "회수",
    "내일",
    "쟁점",
    "오늘",
    "다음",
    "전망",
    "인과",
    "사슬",
    "말씀드린",
    "예상과",
    "그래서",
    "이번",
    "흐름",
    "가지",
    "첫째",
    "둘째",
    "셋째",
    "넘어",
    "연속",
}
_HEADER_PREFIX_RE = re.compile(
    r"^(아침 포인트 회수|내일 쟁점|다음 주 전망|오늘 볼 포인트|이번 주 인과 사슬)\s*:?\s*"
)
_VIDEO_RE = re.compile(
    r"(발언|회의|본회의|기자회견|브리핑|연설|청문회|표결|국감|국정감사|질타|토론|집회|파업|퇴장|선고)"
)


@dataclass
class ScriptLine:
    """대본 한 줄(녹음 대상)."""

    topic: str
    text: str


def parse_script(text: str) -> list[ScriptLine]:
    """`## 헤더` 단위 주제 + 녹음 대상 줄(주석·빈 줄 제외)."""
    topic = "도입"
    out: list[ScriptLine] = []
    for raw in text.splitlines():
        ln = raw.strip()
        if ln.startswith("## "):
            topic = ln[3:].strip()
            continue
        if not ln or ln.startswith("#"):
            continue
        out.append(ScriptLine(topic, ln))
    return out


def _norm(s: str) -> str:
    return re.sub(r"[\s.,!?·…\"'“”]", "", s)


def align_topics(cues: list[Cue], lines: list[ScriptLine], lookahead: int = 12) -> list[str]:
    """큐마다 주제 이름(대본 줄 순서를 벗어나지 않는 탐욕 정렬)."""
    if not lines:
        return ["본문"] * len(cues)
    ptr, topics = 0, []
    current = lines[0].topic
    for cue in cues:
        best, best_j = 0.0, None
        target = _norm(cue.text)
        for j in range(ptr, min(len(lines), ptr + lookahead)):
            # 큐가 여러 줄을 합친 경우를 위해 두 줄 연결도 비교
            cands = [_norm(lines[j].text)]
            if j + 1 < len(lines):
                cands.append(_norm(lines[j].text + lines[j + 1].text))
            r = max(difflib.SequenceMatcher(None, target, c).ratio() for c in cands)
            if r > best:
                best, best_j = r, j
        if best_j is not None and best >= 0.35:
            current = lines[best_j].topic
            ptr = best_j + 1 if best >= 0.6 else best_j
        topics.append(current)
    return topics


def segment(
    cues: list[Cue],
    topics: list[str],
    min_sec: float = 4.0,
    max_sec: float = 8.0,
    min_cues: int = 2,
    max_cues: int = 4,
) -> list[list[int]]:
    """주제 안에서 2~4문장 또는 4~8초 단위로 큐 index를 묶는다."""
    groups: list[list[int]] = []
    cur: list[int] = []
    for i, cue in enumerate(cues):
        if cur and topics[i] != topics[cur[0]]:
            groups.append(cur)
            cur = []
        cur.append(i)
        dur = cue.end - cues[cur[0]].start
        if dur >= max_sec or len(cur) >= max_cues or (dur >= min_sec and len(cur) >= min_cues):
            groups.append(cur)
            cur = []
    if cur:
        groups.append(cur)
    # 같은 주제의 너무 짧은 꼬리(2초 미만)는 앞 장면에 붙인다.
    merged: list[list[int]] = []
    for g in groups:
        dur = cues[g[-1]].end - cues[g[0]].start
        if merged and dur < 2.0 and topics[g[0]] == topics[merged[-1][0]]:
            merged[-1].extend(g)
        else:
            merged.append(g)
    return merged


def heuristic_keywords(text: str, topic: str) -> tuple[list[str], list[str]]:
    """고유명사 우선 키워드(ko 3, en 3)."""
    topic = _HEADER_PREFIX_RE.sub("", topic)
    found: list[str] = []
    for noun in sorted(PROPER_NOUNS, key=len, reverse=True):
        for src in (text, topic):
            if noun in src and not any(noun in f for f in found):
                found.append(noun)
    found.sort(key=lambda n: text.find(n) if n in text else 999)
    ko = list(found)
    counts = Counter(
        t for t in tokenize(f"{text} {topic} {topic}") if t not in STOPWORDS and t not in STRUCTURAL
    )
    for tok, _ in counts.most_common():
        if len(ko) >= 3:
            break
        if not any(tok in k or k in tok for k in ko):
            ko.append(tok)
    en = [PROPER_NOUNS[n] for n in found][:3]
    for filler in ("Korea politics", "Seoul city", "news briefing"):
        if len(en) >= 3:
            break
        if filler not in en:
            en.append(filler)
    return ko[:3] or ["국회", "정치", "서울"], en[:3]


def heuristic_kind(text: str, ko: list[str]) -> SceneKind:
    """장면 성격."""
    if _VIDEO_RE.search(text):
        return "video"
    if any(k in PROPER_NOUNS for k in ko):
        return "photo"
    return "broll"


class LLMScene(BaseModel):
    """Claude 장면 결과."""

    cue_ids: list[int]
    kind: Literal["photo", "video", "broll"]
    keywords_ko: list[str]
    keywords_en: list[str]


class LLMScenes(BaseModel):
    """Claude 장면 목록."""

    scenes: list[LLMScene]


def _valid_llm(res: LLMScenes, cues: list[Cue], topics: list[str]) -> bool:
    flat = [i for s in res.scenes for i in s.cue_ids]
    if flat != list(range(1, len(cues) + 1)):
        return False
    return all(len({topics[i - 1] for i in s.cue_ids}) == 1 for s in res.scenes)


def _to_scenes(groups: list[list[int]], cues: list[Cue], topics: list[str]) -> list[Scene]:
    scenes: list[Scene] = []
    for n, g in enumerate(groups, start=1):
        text = " ".join(cues[i].text for i in g)
        ko, en = heuristic_keywords(text, topics[g[0]])
        scenes.append(
            Scene(
                id=f"s{n:03d}",
                start_sec=cues[g[0]].start,
                end_sec=cues[g[-1]].end,
                text=text,
                topic=topics[g[0]],
                keywords_ko=ko,
                keywords_en=en,
                kind=heuristic_kind(text, ko),
                cue_ids=[cues[i].index for i in g],
            )
        )
    # 빈틈 없는 타임라인: 첫 장면은 0초부터, 각 장면은 다음 장면 시작까지
    if scenes:
        scenes[0].start_sec = 0.0
        for a, b in zip(scenes, scenes[1:], strict=False):
            a.end_sec = b.start_sec
    return scenes


def build_scenes(cues: list[Cue], script_text: str, llm: ClaudeLLM | None = None) -> list[Scene]:
    """장면 목록 생성."""
    topics = align_topics(cues, parse_script(script_text))
    groups = segment(cues, topics)
    scenes = _to_scenes(groups, cues, topics)
    if llm is None or not cues:
        return scenes
    payload = [
        {
            "cue_id": c.index,
            "start": round(c.start, 2),
            "end": round(c.end, 2),
            "topic": topics[i],
            "text": c.text,
        }
        for i, c in enumerate(cues)
    ]
    try:
        res = llm.json(
            "scenes", SCENES_TASK, "자막 큐:\n" + json.dumps(payload, ensure_ascii=False), LLMScenes
        )
    except LLMError:
        return scenes
    assert isinstance(res, LLMScenes)
    if not _valid_llm(res, cues, topics):
        return scenes
    groups = [[i - 1 for i in s.cue_ids] for s in res.scenes]
    out = _to_scenes(groups, cues, topics)
    for sc, ls in zip(out, res.scenes, strict=True):
        sc.kind = ls.kind
        sc.keywords_ko = ls.keywords_ko[:3] or sc.keywords_ko
        sc.keywords_en = ls.keywords_en[:3] or sc.keywords_en
    return out
