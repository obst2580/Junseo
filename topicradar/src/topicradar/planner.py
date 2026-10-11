"""영상 계획의 문장 요소: 제목 3안·썸네일 문구·(10분 묶음) 주제들을 잇는 고리 한 문장.

Claude가 있으면 한 번의 호출로 만들고, 없으면 주제 이름과 커뮤니티 주장(stance)으로 만든다(지어내지 않는다).
"""

from __future__ import annotations

import json

from pydantic import BaseModel

from studio_core.llm import ClaudeLLM, LLMError
from studio_core.text import idf_table, josa, short_name, tokenize
from topicradar import prompts
from topicradar.cluster import GENERIC_TOKENS, IDF_PRIOR, signal_tokens
from topicradar.models import Topic, VideoPlan
from topicradar.risk import title_problems

THUMB_MAX = 10


def thumbnail_for(base: str) -> str:
    """썸네일 문구(10자 이내)."""
    words = base.split()
    text = ""
    for w in words:
        if len(f"{text} {w}".strip()) + 3 > THUMB_MAX:
            break
        text = f"{text} {w}".strip()
    text = text or base[:7]
    return f"{text} 왜?"[:THUMB_MAX]


def _stance_short(stance: str, n: int = 30) -> str:
    s = stance.strip().rstrip(".!?…")
    return s if len(s) <= n else s[:n].rsplit(" ", 1)[0]


def heuristic_titles(plan: VideoPlan) -> tuple[list[str], str]:
    """제목 3안과 썸네일 문구(커뮤니티 주장을 앞세운다)."""
    names = [short_name(it.topic.name, 16) for it in plan.items]
    stances = [_stance_short(it.opinion.stance) for it in plan.items if it.opinion and it.opinion.posts]
    if plan.format == "short" or len(names) < 2:
        name = names[0] if names else "오늘"
        titles = [
            stances[0] if stances else f"{name}, 지금 다들 화난 이유",
            f"{name}, 지금 다들 화난 이유",
            f"{name} — 커뮤니티가 묻는 것",
        ]
    else:
        titles = [
            f"{stances[0]} + {names[1]}" if stances else f"{names[0]} 그리고 {names[1]}",
            " · ".join(names),
            f"{names[0]}부터 {names[-1]}까지 한 번에",
        ]
    titles = list(dict.fromkeys(t for t in titles if not {"날짜형", "감탄형"} & set(title_problems(t))))
    return titles[:3], thumbnail_for(names[0] if names else "오늘")


def link_sentence(names: list[str], words: list[str]) -> str:
    """'A, B 그리고 C, 모두 X·Y로 이어지는 이야기입니다.'"""
    head = ", ".join(names[:-1]) + f" 그리고 {names[-1]}" if len(names) > 1 else names[0]
    if words:
        return f"{head}, 모두 {josa('·'.join(words), '으로/로')} 이어지는 이야기입니다."
    return f"{head}, 지금 커뮤니티가 가장 뜨겁게 말하는 이야기들입니다."


def heuristic_link(topics: list[Topic], generic: set[str] | None = None) -> str:
    """10분 묶음의 고리: 주제들에 함께 나오는 고유한 말."""
    gen = GENERIC_TOKENS | (generic or set())
    docs = [
        set(t.keywords) | set(tokenize(t.name)) | {x for s in t.signals for x in signal_tokens(s)}
        for t in topics
    ]
    idf = idf_table(docs, prior=IDF_PRIOR)
    common = set.intersection(*docs) - gen if docs else set()
    words = sorted((w for w in common if len(w) >= 2), key=lambda w: -idf.get(w, 0.0))[:2]
    return link_sentence([short_name(t.name, 14) for t in topics], words)


class LLMPlanText(BaseModel):
    """제목·썸네일·고리."""

    titles: list[str]
    thumbnail_text: str
    link: str | None


def llm_plan_text(llm: ClaudeLLM, plan: VideoPlan) -> LLMPlanText:
    """한 번의 호출."""
    payload = {
        "format": plan.spec.name,
        "items": [
            {
                "name": it.topic.name,
                "category": it.topic.category,
                "stance": it.opinion.stance if it.opinion else None,
                "points": it.opinion.points if it.opinion else [],
                "articles": [s.title for s in it.topic.news_signals()[:4]],
            }
            for it in plan.items
        ],
        "bundle_link": plan.link,
    }
    system = prompts.IDENTITY + "\n" + prompts.PLAN_TASK
    res = llm.json("plan", system, "영상:\n" + json.dumps(payload, ensure_ascii=False), LLMPlanText)
    assert isinstance(res, LLMPlanText)
    return res


def fill_plan_text(plan: VideoPlan, llm: ClaudeLLM | None, generic: set[str] | None = None) -> None:
    """제목·썸네일·고리를 채운다(제자리). Claude가 빠뜨린 부분은 휴리스틱으로. generic: 고리 말에서 뺄 일반어."""
    if llm is not None:
        try:
            res = llm_plan_text(llm, plan)
            plan.titles = [t for t in res.titles if not {"날짜형", "감탄형"} & set(title_problems(t))][:3]
            plan.thumbnail_text = res.thumbnail_text[:THUMB_MAX]
            if plan.format == "long" and res.link and not plan.link:
                plan.link = res.link
            plan.llm_mode = "claude"
        except LLMError:
            pass
    if len(plan.titles) < 3 or not plan.thumbnail_text:
        titles, thumb = heuristic_titles(plan)
        plan.titles = (plan.titles + [t for t in titles if t not in plan.titles])[:3]
        plan.thumbnail_text = plan.thumbnail_text or thumb
    if plan.format == "long" and not plan.link and len(plan.items) >= 2:
        plan.link = heuristic_link([it.topic for it in plan.items], generic)
