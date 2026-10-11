"""Claude API 없이 쓰기: claude.ai '대본실' 페이지와 파일로 주고받는다.

- 내보내기(studio-board): 주제판 + 주제별 공감 많은 글·댓글 + 근거 기사 + 채널 규칙(프롬프트)을 JSON 하나로.
  대본실이 이 파일로 의견 정리·묶음 추천·제목·대본을 사용자의 Claude로 만든다.
- 가져오기(studio-script): 대본실에서 저장한 파일(고른 주제·형식·의견·제목·대본)을 받아
  영상 프로젝트(report.md / script.txt / plan.json)를 만든다. 이후 음성·자료·timeline.xml은 그대로.
"""

from __future__ import annotations

import json
from typing import Literal

from pydantic import BaseModel, Field, ValidationError

from studio_core.config import ChannelConfig
from studio_core.text import short_name
from topicradar import prompts
from topicradar.board import LEAD_WEIGHT
from topicradar.db import Store
from topicradar.models import FORMATS, Board, Opinion, VideoFormat, target_chars
from topicradar.opinion import opinion_rows
from topicradar.planner import THUMB_MAX
from topicradar.risk import LEGAL_WORDS, POLL_WORDS, topic_risks
from topicradar.script import (
    BANNED,
    LINE_LIMIT,
    SLANG,
    VAGUE_MAX,
    VAGUE_WORDS,
    article_rows,
    normalize_script,
    ranked_articles,
    tone_rules,
)

EXPORT_KIND = "studio-board"
IMPORT_KIND = "studio-script"
VERSION = 1
SCRIPT_NOTE = "claude.ai 대본실에서 씀"
ARTICLES_PER_TOPIC = 6


def export_board(cfg: ChannelConfig, store: Store, board: Board) -> dict:
    """주제판 → 대본실용 JSON(dict)."""
    start, end = board.window_start, board.window_end
    topics = []
    for bt in board.topics:
        t = store.get_topic(bt.topic_id)
        if t is None:
            continue
        op = bt.opinion
        topics.append(
            {
                "id": t.id,
                "name": t.name,
                "heading": short_name(t.name, 20),
                "category": t.category,
                "score": round(bt.score),
                "trend": bt.trend,
                "heat": op.heat_label,
                "covered_in": list(bt.covered_in),
                "opinion": {
                    "stance": op.stance,
                    "points": op.points,
                    "minority": op.minority,
                    "questions": op.questions,
                    "by": op.by,
                },
                "posts": opinion_rows([t], start, end)[0]["posts"],
                "post_links": [
                    {"site": x.site, "title": x.text, "url": x.url} for x in op.samples if x.kind == "post"
                ],
                "articles": article_rows(ranked_articles(t, start, end)[:ARTICLES_PER_TOPIC]),
                "follow_up": t.follow_up,
                "risk_notes": [f.message for f in topic_risks(t) if f.kind in ("법적", "여론조사")],
            }
        )
    return {
        "kind": EXPORT_KIND,
        "version": VERSION,
        "board_id": board.id,
        "created_at": board.created_at.isoformat(),
        "window": [start.isoformat(), end.isoformat()],
        **channel_rules(cfg),
        "topics": topics,
        "bundles": [b.model_dump() for b in board.bundles],
        "notes": board.notes,
    }


def channel_rules(cfg: ChannelConfig) -> dict:
    """대본을 쓸 때 필요한 채널 규칙: 정체성·말투, 형식별 길이, Claude에 줄 규칙(프롬프트), 점검 기준."""
    cpm = cfg.script.chars_per_minute
    return {
        "stance": cfg.channel.stance,
        "tone": cfg.script.tone,
        "chars_per_minute": cpm,
        "formats": {
            k: {
                "name": f.name,
                "minutes": f.minutes,
                "min_topics": f.min_topics,
                "max_topics": f.max_topics,
                "target_chars": list(target_chars(k, cpm)),
                "lead_weight": LEAD_WEIGHT,
            }
            for k, f in FORMATS.items()
        },
        "rules": {
            "identity": prompts.IDENTITY,
            "opinion_task": prompts.OPINION_TASK,
            "group_task": prompts.GROUP_TASK,
            "plan_task": prompts.PLAN_TASK,
            "script_rules": prompts.SCRIPT_RULES,
            "structure_rules": prompts.SCRIPT_STRUCTURE_RULES,
            "format_rules": prompts.FORMAT_SCRIPT_RULES,
            "tone_rules": tone_rules(cfg.script.tone),
            "thumb_max": THUMB_MAX,
            "line_limit": LINE_LIMIT,
            "banned": BANNED,
            "slang": SLANG,
            "vague_words": VAGUE_WORDS,
            "vague_max": VAGUE_MAX,
            # 표시 전용 리스크(대본 규칙에 같이 넘긴다)
            "risk": {
                "legal_words": LEGAL_WORDS,
                "legal_note": "수사·재판 진행 중: '확인된 사실'과 '의혹·주장'을 구분해 쓰고 확정 표현 금지",
                "poll_words": POLL_WORDS,
                "poll_note": "여론조사 인용 시 조사의뢰자·조사기관·조사기간·조사방법·표본크기·오차범위 명시",
            },
        },
    }


def export_filename(board: Board) -> str:
    """'studio-주제판-2026-10-08_1800.json'."""
    return f"studio-주제판-{board.created_at:%Y-%m-%d_%H%M}.json"


class ImportedOpinion(BaseModel):
    """대본실에서 정리한 커뮤니티 의견."""

    stance: str
    points: list[str] = Field(default_factory=list)
    minority: str | None = None
    questions: list[str] = Field(default_factory=list)


class ScriptFile(BaseModel):
    """대본실에서 저장한 파일."""

    kind: Literal["studio-script"]
    version: int
    board_id: str
    format: VideoFormat
    topic_ids: list[str]
    script: str
    opinions: dict[str, ImportedOpinion] = Field(default_factory=dict)
    link: str | None = None
    titles: list[str] = Field(default_factory=list)
    thumbnail_text: str = ""


def parse_script_file(data: dict | str | bytes) -> ScriptFile:
    """파일 내용 → ScriptFile. 틀리면 무엇이 틀렸는지 한국어로 ValueError."""
    if isinstance(data, bytes):
        data = data.decode("utf-8-sig")
    if isinstance(data, str):
        try:
            data = json.loads(data)
        except json.JSONDecodeError as e:
            raise ValueError(
                f"JSON 형식이 아닙니다({e.lineno}번째 줄) — 대본실에서 저장한 파일을 그대로 넣어 주세요"
            ) from e
    if not isinstance(data, dict):
        raise ValueError("대본실 파일이 아닙니다(JSON 객체가 아님)")
    kind = data.get("kind")
    if kind == EXPORT_KIND:
        raise ValueError(
            "이건 studio에서 내보낸 주제판 파일입니다 — 대본실에서 대본을 쓴 뒤 저장한 파일을 넣어 주세요"
        )
    if kind != IMPORT_KIND:
        raise ValueError("대본실에서 저장한 파일이 아닙니다(kind가 studio-script가 아님)")
    if int(data.get("version") or 0) > VERSION:
        raise ValueError("대본실 파일 버전이 studio보다 새것입니다 — studio를 업데이트해 주세요")
    try:
        sf = ScriptFile.model_validate(data)
    except ValidationError as e:
        where = ", ".join(".".join(str(x) for x in err["loc"]) for err in e.errors()[:3])
        raise ValueError(f"대본실 파일에 빠지거나 잘못된 항목: {where}") from e
    sf.script = normalize_script(sf.script) if sf.script.strip() else ""
    if not sf.script:
        raise ValueError("대본이 비어 있습니다 — 대본실에서 대본을 쓴 뒤 저장해 주세요")
    return sf


def imported_opinions(board: Board, sf: ScriptFile) -> dict[str, Opinion]:
    """대본실 의견으로 판의 의견을 덮는다(글·댓글·공감 숫자와 대표 글은 판의 것 그대로)."""
    out: dict[str, Opinion] = {}
    for tid, o in sf.opinions.items():
        bt = board.get(tid)
        if bt is None or not o.stance.strip():
            continue
        out[tid] = bt.opinion.model_copy(
            update={
                "stance": o.stance.strip(),
                "points": [p.strip() for p in o.points if p.strip()][:4],
                "minority": (o.minority or "").strip() or None,
                "questions": [q.strip() for q in o.questions if q.strip()][:2],
                "by": "claude",
            }
        )
    return out
