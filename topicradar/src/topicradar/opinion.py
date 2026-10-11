"""커뮤니티 의견 정리: 주제마다 '채널이 대신 말할 주장'을 글·댓글·공감 수로 뽑는다.

- 무게: 글 = 1 + 공감/10 + 댓글/40, 댓글 = 공감 수. 다수 의견은 무게가 큰 글·댓글에서 나온다.
  (댓글이 많은 건 논란일 수도 있어 '동의'는 공감 수를 더 크게 본다. 반응도 점수는 댓글을 그대로 센다.)
- Claude 경로: 판의 상위 주제를 한 번에 보내 stance·points·minority·questions와 10분 묶음(bundles)을 받는다.
- 오프라인 경로: 무게가 가장 큰 글 제목을 주장으로, 다음 글·공감 많은 댓글을 근거로(지어내지 않고 옮긴다).
닉네임 같은 글쓴이 정보는 수집 단계에서부터 저장하지 않는다.
"""

from __future__ import annotations

import json
import re
from datetime import datetime

from pydantic import BaseModel

from studio_core.llm import ClaudeLLM
from studio_core.text import normalize_title
from topicradar import prompts
from topicradar.models import Bundle, Opinion, OpinionSample, Signal, Topic
from topicradar.score import heat_stats

LLM_TOPICS = 12  # Claude에 의견 정리를 맡길 상위 주제 수(나머지는 휴리스틱)
POSTS_PER_TOPIC = 6
COMMENTS_PER_POST = 5


def post_weight(s: Signal) -> float:
    """글 무게(다수 의견 판단용: 공감 > 댓글)."""
    return 1.0 + int(s.metric.get("votes") or 0) / 10.0 + int(s.metric.get("comments", 0)) / 40.0


def ranked_posts(t: Topic, start: datetime | None = None, end: datetime | None = None) -> list[Signal]:
    """주제의 커뮤니티 글(무게 순). 구간에 글이 없으면 전체에서."""
    buzz = (t.buzz(start, end) if start and end else []) or t.buzz()
    return sorted(buzz, key=lambda s: (-post_weight(s), -s.published_at.timestamp()))


def top_comments(posts: list[Signal], n: int = 6) -> list[tuple[Signal, dict]]:
    """여러 글의 댓글 중 공감 많은 순."""
    rows = [(s, c) for s in posts for c in s.metric.get("top_comments", []) if c.get("t")]
    rows.sort(key=lambda x: -int(x[1].get("v", 0)))
    return rows[:n]


def _clean(text: str) -> str:
    text = re.sub(r"(ㅋ|ㅎ|ㅠ|ㅜ){2,}", "", text)
    return normalize_title(text).strip(" .,!~")


def _samples(posts: list[Signal]) -> list[OpinionSample]:
    out = [
        OpinionSample(
            site=str(s.metric.get("site", "")),
            kind="post",
            text=s.title,
            votes=int(s.metric.get("votes") or 0),
            comments=int(s.metric.get("comments", 0)),
            url=s.url,
        )
        for s in posts[:4]
    ]
    out += [
        OpinionSample(
            site=str(s.metric.get("site", "")),
            kind="comment",
            text=c["t"],
            votes=int(c.get("v", 0)),
            url=s.url,
        )
        for s, c in top_comments(posts, 4)
    ]
    return out


def heuristic_opinion(t: Topic, start: datetime | None = None, end: datetime | None = None) -> Opinion:
    """무게가 큰 글·댓글을 그대로 옮긴 의견(오프라인)."""
    posts = ranked_posts(t, start, end)
    n_posts, comments, votes = heat_stats(posts)
    if not posts:
        return Opinion(stance=f"{t.name} — 커뮤니티 반응 없음", posts=0)
    stance = _clean(posts[0].title)
    points: list[str] = []
    for s in posts[1:4]:
        x = _clean(s.title)
        if x and x != stance and x not in points:
            points.append(x)
    for _s, c in top_comments(posts, 3):
        x = _clean(c["t"])[:120]
        if int(c.get("v", 0)) >= 3 and x and x not in points:
            points.append(x)
    questions = [_clean(s.title) for s in posts if s.title.rstrip().endswith("?")][:2]
    return Opinion(
        stance=stance,
        points=points[:4],
        questions=questions,
        posts=n_posts,
        comments=comments,
        votes=votes,
        samples=_samples(posts),
        by="heuristic",
    )


class LLMOpinion(BaseModel):
    """Claude 의견 정리 항목."""

    topic_id: str
    stance: str
    points: list[str]
    minority: str | None
    questions: list[str]


class LLMBundle(BaseModel):
    """Claude 묶음 추천."""

    topic_ids: list[str]
    link: str


class LLMOpinions(BaseModel):
    """Claude 의견 정리 결과."""

    opinions: list[LLMOpinion]
    bundles: list[LLMBundle]


def _payload(topics: list[Topic], start: datetime | None, end: datetime | None) -> list[dict]:
    rows = []
    for t in topics:
        posts = ranked_posts(t, start, end)[:POSTS_PER_TOPIC]
        rows.append(
            {
                "topic_id": t.id,
                "name": t.name,
                "category": t.category,
                "articles": [s.title for s in t.news_signals()[:3]],
                "posts": [
                    {
                        "title": s.title,
                        "body": s.summary[:200],
                        "comments": int(s.metric.get("comments", 0)),
                        "votes": int(s.metric.get("votes") or 0),
                        "top_comments": [
                            {"text": c["t"][:120], "votes": int(c.get("v", 0))}
                            for c in s.metric.get("top_comments", [])[:COMMENTS_PER_POST]
                        ],
                    }
                    for s in posts
                ],
            }
        )
    return rows


def llm_opinions(
    llm: ClaudeLLM, topics: list[Topic], start: datetime | None = None, end: datetime | None = None
) -> tuple[dict[str, Opinion], list[Bundle]]:
    """상위 주제의 의견 정리 + 10분 묶음 추천(한 번의 호출). 휴리스틱 숫자·대표 글은 그대로 붙인다."""
    system = prompts.IDENTITY + "\n" + prompts.OPINION_TASK
    prompt = "주제별 커뮤니티 글:\n" + json.dumps(_payload(topics, start, end), ensure_ascii=False)
    res = llm.json("opinion", system, prompt, LLMOpinions, max_tokens=24000)
    assert isinstance(res, LLMOpinions)
    by_id = {t.id: t for t in topics}
    out: dict[str, Opinion] = {}
    for o in res.opinions:
        t = by_id.get(o.topic_id)
        if t is None or not o.stance.strip():
            continue
        base = heuristic_opinion(t, start, end)
        out[t.id] = base.model_copy(
            update={
                "stance": o.stance.strip(),
                "points": [p.strip() for p in o.points if p.strip()][:4],
                "minority": (o.minority or "").strip() or None,
                "questions": [q.strip() for q in o.questions if q.strip()][:2],
                "by": "claude",
            }
        )
    bundles = []
    for b in res.bundles:
        ids = list(dict.fromkeys(i for i in b.topic_ids if i in by_id))
        if 2 <= len(ids) <= 3 and b.link.strip():
            bundles.append(Bundle(topic_ids=ids, link=b.link.strip()))
    return out, bundles[:3]
