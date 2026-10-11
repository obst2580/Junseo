"""대본 초안.

- Claude 경로: 채널 규칙 + 슬롯 규칙을 시스템 프롬프트로, 편성안과 근거 기사를 입력으로 한 번 호출.
- 오프라인 경로: 수집 기사 문장을 사실 → 원인 → 다음 수순 순서로 뽑아 존댓말로 바꾸고
  자막 단위로 끊는 '추출형 골격'. 근거 문장이 없으면 지어내지 않고 `#TODO` 주석을 남긴다.
- 어느 경로든 `validate_script()`가 규칙(글자 수, 25자, 출처 주석, 수집 밖 수치·인용,
  금지 표현, 회수 문구)을 점검해 리포트에 싣는다.
"""

from __future__ import annotations

import json
import re

from studio_core.config import KST
from studio_core.llm import ClaudeLLM, LLMError
from studio_core.text import (
    count_chars,
    numbers_in,
    quotes_in,
    short_name,
    split_sentences,
    to_polite,
    wrap_subtitle,
)
from topicradar import prompts
from topicradar.cluster import window_title
from topicradar.models import Signal, SlotItem, SlotPlan

CHAR_RANGE = {"morning": (900, 1100), "evening": (1500, 1800), "weekly": (2500, 3000)}
LINE_LIMIT = 25
BANNED = ["충격", "경악", "난리", "대박", "소름", "어이없", "한심"]
HEDGES = ["로 보입니다", "것 같습니다", "듯합니다", "아닐까"]
_CAUSE_RE = re.compile(r"(때문|이유|배경|책임|탓|반발|여파|원인|면서|따른 것)")
_NEXT_RE = re.compile(
    r"(예정|전망|방침|계획|다음 달|다음 주|내주|내달|후속|수순|할 것으로|되면|하면|시행된다|준비에 들어|검토하겠다|검토한다)"
)
_ORDINALS = ["첫째", "둘째", "셋째", "넷째"]
_CAUSE_QUESTION = "근데 왜 이렇게 됐을까요?"
_ARTICLE_ENDING_RE = re.compile(r"(밝혔습니다|알려졌습니다|전해졌습니다|밝혔다|했다|였다|이다)[.]?$")
SLANG = ["존나", "씨발", "시발", "개새", "병신", "좆"]


# ---- 공통 줄 만들기 --------------------------------------------------------


def _sentence_lines(sentence: str, url: str | None = None, force_source: bool = False) -> list[str]:
    """한 문장 → 존댓말·자막 단위 줄(+ 필요 시 출처 주석)."""
    lines = wrap_subtitle(to_polite(sentence), LINE_LIMIT)
    if url and (force_source or numbers_in(sentence) or quotes_in(sentence)):
        lines.append(f"#출처: {url}")
    return lines


def _ranked_signals(item: SlotItem, plan: SlotPlan) -> list[Signal]:
    # 수동 일정(schedule)은 편집 메모라 대본 사실 문장으로 쓰지 않는다.
    sigs = [s for s in item.topic.signals if s.kind not in ("competitor", "own", "schedule")]
    start, end = plan.window_start, plan.window_end
    in_win = [s for s in sigs if start and end and start <= s.published_at < end]
    rest = [s for s in sigs if s not in in_win]
    key = {"domestic": 0, "press": 1, "overseas": 2, "schedule": 3}
    return sorted(in_win, key=lambda s: (key.get(s.kind, 5), s.published_at)) + sorted(
        rest, key=lambda s: s.published_at, reverse=True
    )


def _pick(
    sigs: list[Signal], pattern: re.Pattern[str] | None, used: set[str], limit: int, per_signal: int = 1
) -> list[tuple[str, str]]:
    """(문장, URL) 최대 limit개.

    pattern이 있으면 그 패턴에 맞는 문장, None이면 '사실' 문장(원인·다음 수순 표지가 없는 문장)을
    기사 순서대로 per_signal개까지 고른다. 이미 쓴 문장은 건너뛴다.
    """
    out: list[tuple[str, str]] = []
    for s in sigs:
        sents = split_sentences(s.summary) or [s.title]
        if pattern is not None:
            cands = [x for x in sents if pattern.search(x)]
        else:
            cands = [x for x in sents if not _CAUSE_RE.search(x) and not _NEXT_RE.search(x)] or sents[:1]
        taken = 0
        for sent in cands:
            norm = re.sub(r"\s+", "", sent)
            if norm in used:
                continue
            used.add(norm)
            out.append((sent, s.url))
            taken += 1
            if taken >= per_signal or len(out) >= limit:
                break
        if len(out) >= limit:
            break
    return out


def _source_for(text: str, item: SlotItem) -> str | None:
    """문장이 나온 기사 URL(앞 12자 일치)."""
    core = re.sub(r"\s+", "", text)[:12]
    for s in item.topic.signals:
        if core and core in re.sub(r"\s+", "", s.summary + s.title):
            return s.url
    return None


def _cited(text: str, item: SlotItem, prefix: str = "") -> list[str]:
    """출처를 찾아 붙이는 줄 만들기(수치·인용이 있을 때)."""
    url = _source_for(text, item)
    lines = wrap_subtitle(f"{prefix}{to_polite(text)}", LINE_LIMIT)
    if url and (numbers_in(text) or quotes_in(text)):
        lines.append(f"#출처: {url}")
    return lines


def topic_block(
    item: SlotItem,
    plan: SlotPlan,
    facts: int = 2,
    header: str | None = None,
    tone: str = prompts.DEFAULT_TONE,
) -> list[str]:
    """주제 하나: 사실 → 원인 → 다음 수순."""
    sigs = _ranked_signals(item, plan)
    used: set[str] = set()
    title = (
        item.topic.name
        if plan.slot == "weekly"
        else window_title(item.topic, plan.window_start, plan.window_end)
    )
    lines = [f"## {header or short_name(title, 20)}"]
    fact_sents = _pick(sigs, None, used, facts)
    if len(fact_sents) < facts:  # 기사가 적으면 같은 기사의 다음 사실 문장까지
        fact_sents += _pick(sigs, None, used, facts - len(fact_sents), per_signal=2)
    for n, (sent, url) in enumerate(fact_sents):
        lines += _sentence_lines(sent, url, force_source=(n == 0))
    cause = _pick(sigs, _CAUSE_RE, used, 1)
    if cause:
        if tone != "해설형":  # 확신형 구어: 시청자의 의문을 대신 던지고 답한다
            lines.append(_CAUSE_QUESTION)
        lines += _sentence_lines(*cause[0])
    else:
        lines.append("#TODO: 원인 문장 보강 — 수집 기사에 원인 서술 없음")
    nxt = _pick(sigs, _NEXT_RE, used, 1)
    if nxt:
        lines += _sentence_lines(*nxt[0])
    elif item.topic.follow_up and re.sub(r"\s+", "", item.topic.follow_up) not in used:
        lines += wrap_subtitle(to_polite(item.topic.follow_up), LINE_LIMIT)
    else:
        lines.append("#TODO: 다음 수순 보강 — 후속 일정 근거 없음")
    return lines


# ---- 슬롯별 오프라인 초안 ---------------------------------------------------


def _morning(plan: SlotPlan, tone: str = prompts.DEFAULT_TONE) -> list[str]:
    lines: list[str] = []
    for it in plan.items:
        if it.role in ("헤드라인", "서브"):
            lines += topic_block(it, plan, facts=3 if it.role == "헤드라인" else 2, tone=tone) + [""]
    points = plan.items_by_role("오늘의포인트")
    for it in points:
        lines += topic_block(it, plan, facts=2, tone=tone) + [""]
    if points:
        lines += [
            "## 오늘 볼 포인트",
            f"오늘 볼 포인트 {'세' if len(points) == 3 else len(points)} 가지입니다.",
        ]
        for n, it in enumerate(points):
            lines.append(f"{_ORDINALS[n]}, {short_name(it.topic.name, 16)}.")
            if it.impact:
                lines += _cited(it.impact, it)
            else:
                lines.append("#TODO: '어떻게 되면 뭐가 달라지는지' 한 문장 보강")
    return lines


def _result_signals(it: SlotItem, plan: SlotPlan) -> list[Signal]:
    """회수 항목의 결과 기사(저녁 윈도우 안)."""
    start, end = plan.window_start, plan.window_end
    return [s for s in _ranked_signals(it, plan) if start and end and start <= s.published_at < end]


def _recall_lines(it: SlotItem, plan: SlotPlan) -> list[str]:
    name = short_name(it.topic.name, 16)
    lines = [f"## 아침 포인트 회수: {name}"]
    if it.hit == "hit" and it.actual:
        lines += _cited(it.actual, it, prefix="아침에 말씀드린 대로, ")
    elif it.hit == "miss" and it.actual:
        lines += _cited(it.actual, it, prefix="아침 예상과 달리, ")
        why = _pick(_result_signals(it, plan), _CAUSE_RE, set(), 1)
        lines += _sentence_lines(*why[0]) if why else ["#TODO: 예상과 달라진 이유 보강"]
    elif it.actual:
        lines += _cited(it.actual, it)
        lines.append("#확인: 아침 예상과의 일치 여부 수동 판정 필요")
    else:
        lines += wrap_subtitle(f"{name} 결과는 아직 나오지 않았습니다.", LINE_LIMIT)
        return lines
    # '그래서 다음은'은 결과가 나온 뒤의 기사에서만 찾는다(표결 전 '가결 전망' 같은 문장 배제).
    nxt = _pick(_result_signals(it, plan), _NEXT_RE, {re.sub(r"\s+", "", it.actual or "")}, 1)
    follow = nxt[0][0] if nxt else None
    if follow:
        lines += _cited(follow, it, prefix="그래서 다음은, ")
    else:
        lines.append("#TODO: '그래서 다음은' 한 문장 보강")
    return lines


def _evening(plan: SlotPlan, tone: str = prompts.DEFAULT_TONE) -> list[str]:
    lines: list[str] = []
    for it in plan.items_by_role("헤드라인", "서브"):
        lines += topic_block(it, plan, facts=3 if it.role == "헤드라인" else 2, tone=tone) + [""]
    for it in plan.items_by_role("포인트회수"):
        lines += _recall_lines(it, plan) + [""]
    for it in plan.items_by_role("내일쟁점"):
        lines += topic_block(
            it, plan, facts=1, header=f"내일 쟁점: {short_name(it.topic.name, 14)}", tone=tone
        )
    return lines


def _weekly(plan: SlotPlan, tone: str = prompts.DEFAULT_TONE) -> list[str]:
    lines: list[str] = []
    if plan.chains:
        lines += ["## 이번 주 인과 사슬", "이번 주는 하나의 흐름으로 읽힙니다."]
        for ch in plan.chains:
            lines += wrap_subtitle(f"{ch.title}의 흐름입니다.", LINE_LIMIT)
            for step in ch.summary.split(" → "):
                lines += wrap_subtitle(f"{step}.", LINE_LIMIT)
        lines += ["#확인: 사슬 연결은 키워드 기반 추정 — 인과 관계를 녹음 전 검토", ""]
    for it in plan.items_by_role("줄기", "서브"):
        lines += topic_block(it, plan, facts=5 if it.role == "줄기" else 3, tone=tone) + [""]
    nxt = plan.items_by_role("내일쟁점")
    if nxt:
        lines += ["## 다음 주 전망"]
        for it in nxt:
            lines += topic_block(it, plan, facts=1, tone=tone)[1:]
    return lines


def heuristic_script(plan: SlotPlan, tone: str = prompts.DEFAULT_TONE) -> str:
    """오프라인 추출형 초안(말투는 연결 문장 정도만 반영 — 입담은 Claude 경로에서)."""
    body = {"morning": _morning, "evening": _evening, "weekly": _weekly}[plan.slot](plan, tone)
    return normalize_script("\n".join(body))


# ---- Claude 경로 ---------------------------------------------------------


def tone_rules(tone: str) -> str:
    """말투 프리셋 본문(모르는 이름이면 기본값)."""
    return prompts.TONES.get(tone, prompts.TONES[prompts.DEFAULT_TONE])


def llm_script(llm: ClaudeLLM, plan: SlotPlan, tone: str = prompts.DEFAULT_TONE) -> str:
    """Claude로 대본 생성."""
    items = []
    for it in plan.items:
        sigs = _ranked_signals(it, plan)[:6]
        items.append(
            {
                "order": it.order,
                "role": it.role,
                "topic": it.topic.name,
                "category": it.topic.category,
                "chain": it.chain,
                "expected": it.expected,
                "impact": it.impact,
                "actual": it.actual,
                "hit": it.hit,
                "hit_reason": it.hit_reason,
                "est_seconds": it.est_seconds,
                "articles": [
                    {
                        "url": s.url,
                        "time": s.published_at.astimezone(KST).strftime("%m-%d %H:%M"),
                        "title": s.title,
                        "summary": s.summary,
                    }
                    for s in sigs
                ],
            }
        )
    payload = {
        "slot": plan.slot,
        "date": plan.date.isoformat(),
        "chains": [c.model_dump() for c in plan.chains],
        "risk_notes": [f.message for f in plan.risk_flags if f.kind in ("법적", "여론조사")],
        "items": items,
    }
    system = "\n".join(
        [prompts.IDENTITY, prompts.SCRIPT_RULES, tone_rules(tone), prompts.SLOT_SCRIPT_RULES[plan.slot]]
    )
    text = llm.text(
        "script", system, "편성안과 근거 기사:\n" + json.dumps(payload, ensure_ascii=False), role="script"
    )
    return normalize_script(text)


def draft_script(plan: SlotPlan, llm: ClaudeLLM | None, tone: str = prompts.DEFAULT_TONE) -> str:
    """대본 초안(실패 시 오프라인 초안)."""
    if llm is not None:
        try:
            return llm_script(llm, plan, tone)
        except LLMError:
            pass
    return heuristic_script(plan, tone)


# ---- 정리·점검 -----------------------------------------------------------


def normalize_script(text: str) -> str:
    """코드블록 제거, 줄 정리, 빈 줄 하나로."""
    text = re.sub(r"^```[a-z]*\s*|\s*```$", "", text.strip())
    out: list[str] = []
    for ln in text.splitlines():
        ln = ln.rstrip()
        if not ln.strip():
            if out and out[-1] != "":
                out.append("")
            continue
        out.append(ln.strip())
    return "\n".join(out).strip() + "\n"


def spoken_lines(text: str) -> list[str]:
    """녹음 대상 줄(헤더·주석·빈 줄 제외)."""
    return [ln.strip() for ln in text.splitlines() if ln.strip() and not ln.strip().startswith("#")]


def validate_script(text: str, plan: SlotPlan) -> list[str]:
    """대본 규칙 점검 결과(경고 문장 목록)."""
    warns: list[str] = []
    lines = [ln.strip() for ln in text.splitlines()]
    spoken = spoken_lines(text)
    n = count_chars(lines)
    lo, hi = CHAR_RANGE[plan.slot]
    if not lo <= n <= hi:
        warns.append(f"글자 수 {n:,}자 — 목표 {lo:,}~{hi:,}자")
    long_lines = [ln for ln in spoken if len(ln) > LINE_LIMIT]
    if long_lines:
        warns.append(f"25자 초과 줄 {len(long_lines)}개 (예: '{long_lines[0][:30]}…')")
    if not any(ln.startswith("## ") for ln in lines):
        warns.append("`## 주제명` 헤더 없음(newsclip 장면 분할 불가)")
    sources = " ".join(s.text for it in plan.items for s in it.topic.signals)
    src_nums = set(numbers_in(sources))
    urls = {s.url for it in plan.items for s in it.topic.signals}
    bad_nums, bad_quotes, missing_src = [], [], 0
    for i, ln in enumerate(lines):
        if not ln or ln.startswith("#"):
            continue
        nums = [x for x in numbers_in(ln) if x not in src_nums]
        bad_nums += nums
        bad_quotes += [q for q in quotes_in(ln) if q not in sources]
        if numbers_in(ln) or quotes_in(ln):
            window = lines[i + 1 : i + 4]
            if not any(w.startswith("#출처:") for w in window):
                missing_src += 1
    if bad_nums:
        warns.append(
            f"수집 기사에 없는 수치 {len(bad_nums)}개: {', '.join(sorted(set(bad_nums))[:5])} — 삭제 또는 출처 확인"
        )
    if bad_quotes:
        warns.append(f"수집 기사에 없는 인용 {len(bad_quotes)}개 — 삭제 또는 출처 확인")
    if missing_src:
        warns.append(f"수치·인용 줄 {missing_src}곳에 `#출처:` 주석 없음")
    foreign = [ln for ln in lines if ln.startswith("#출처:") and ln.split(":", 1)[1].strip() not in urls]
    if foreign:
        warns.append(f"수집되지 않은 출처 URL {len(foreign)}개")
    banned = sorted({w for w in BANNED if any(w in ln for ln in spoken)})
    if banned:
        warns.append(f"금지 표현: {', '.join(banned)}")
    slang = sorted({w for w in SLANG if any(w in ln for ln in spoken)})
    if slang:
        warns.append(f"비속어: {', '.join(slang)}")
    article = [ln for ln in spoken if _ARTICLE_ENDING_RE.search(ln)]
    if len(article) >= 3:
        warns.append(f"기사체 종결 {len(article)}줄(예: '{article[0][-12:]}') — 말하듯 바꿀 것")
    hedges = sum(1 for ln in spoken for h in HEDGES if h in ln)
    if hedges > 2:
        warns.append(f"완충 표현 {hedges}회 — 사실관계가 불확실한 곳에만")
    joined = "\n".join(spoken)
    if plan.slot == "morning" and plan.items_by_role("오늘의포인트") and "오늘 볼 포인트" not in joined:
        warns.append("'오늘 볼 포인트 세 가지' 마무리 없음")
    if plan.slot == "evening":
        for it in plan.items_by_role("포인트회수"):
            if it.hit == "hit" and "아침에 말씀드린 대로" not in joined:
                warns.append(f"hit '{it.topic.name}' 회수 문구('아침에 말씀드린 대로') 없음")
            if it.hit == "miss" and "아침 예상과 달리" not in joined:
                warns.append(f"miss '{it.topic.name}' 회수 문구('아침 예상과 달리') 없음")
    todos = sum(1 for ln in lines if ln.startswith(("#TODO", "#확인")))
    if todos:
        warns.append(f"편집자 확인 주석 {todos}개(#TODO/#확인) — 녹음 전 처리")
    return warns
