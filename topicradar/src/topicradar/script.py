"""대본 초안(10분 묶음 / 3분 단독).

- 주장은 커뮤니티 다수 의견(주제별 opinion), 사실은 기사에서만.
- Claude 경로: 채널 규칙 + 형식 규칙을 시스템 프롬프트로, 영상 계획(주제·의견·근거 기사)을 입력으로 한 번 호출.
- 오프라인 경로: 기사 문장(사실) + 공감 많은 커뮤니티 글·댓글(주장)을 옮겨 놓은 '골격'.
  근거가 없으면 지어내지 않고 `#TODO`, 말로 다듬어야 할 곳은 `#확인` 주석을 남긴다.
- 어느 경로든 `validate_script()`가 규칙(글자 수, 출처 주석, 수집 밖 수치·인용,
  커뮤니티 글 그대로 옮김, 금지 표현)을 점검해 리포트에 싣는다.
"""

from __future__ import annotations

import json
import logging
import re
from datetime import datetime

from studio_core.config import KST
from studio_core.llm import ClaudeLLM, LLMError
from studio_core.text import (
    count_chars,
    numbers_in,
    quotes_in,
    short_name,
    split_sentences,
    to_polite,
    tokenize,
)
from topicradar import prompts
from topicradar.cluster import signal_tokens
from topicradar.models import Signal, Topic, VideoItem, VideoPlan

log = logging.getLogger("topicradar.script")

LINE_LIMIT = 25  # 예전 자막용 줄 길이(이제 대본은 한 줄 한 문장 — 점검하지 않는다)
SCRIPT_MAX_TOKENS = 32000  # 깊게 생각(effort high)하는 10분 대본도 잘리지 않게
BANNED = ["충격", "경악", "난리", "대박", "소름", "어이없", "한심"]
HEDGES = ["로 보입니다", "것 같습니다", "듯합니다", "아닐까"]
_CAUSE_RE = re.compile(r"(때문|이유|배경|책임|탓|반발|여파|원인|면서|따른 것)")
_NEXT_RE = re.compile(
    r"(예정|전망|방침|계획|다음 달|다음 주|내주|내달|후속|수순|할 것으로|되면|하면|시행된다|준비에 들어|검토하겠다|검토한다)"
)
_OPINION_BRIDGE = "이 소식에 대한 반응은 분명합니다."
_ARTICLE_ENDING_RE = re.compile(r"(밝혔습니다|알려졌습니다|전해졌습니다|밝혔다|했다|였다|이다)[.]?$")
SLANG = ["존나", "씨발", "시발", "개새", "병신", "좆"]
# 구체적인 사실 대신 쓰는 뭉뚱그린 말(많이 나오면 예고편처럼 추상적인 대본)
VAGUE_WORDS = ["본질", "프레임", "공세", "말꼬리", "노림수", "정치적 의도", "물타기", "꼼수"]
VAGUE_MAX = 2


# ---- 공통 줄 만들기 --------------------------------------------------------


def with_outro(text: str, outro: str) -> str:
    """대본 끝에 채널 마무리 인사를 한 단락으로 붙인다(이미 있으면 그대로)."""
    outro = (outro or "").strip()
    if not outro or outro in text:
        return text
    return text.rstrip() + "\n\n" + outro + "\n"


def _one_line(text: str) -> list[str]:
    """대본 한 줄 = 한 문장(자막용으로 짧게 끊지 않는다)."""
    text = (text or "").strip()
    return [text] if text else []


def _sentence_lines(sentence: str, url: str | None = None, force_source: bool = False) -> list[str]:
    """한 문장 → 존댓말·자막 단위 줄. 대본에는 출처 줄을 넣지 않는다(근거 기사는 리포트·계획에 남는다)."""
    return _one_line(to_polite(sentence))


def _core_tokens(topic: object) -> set[str]:
    """주제 이름·키워드의 고유한 말(대본 근거 기사가 정말 이 주제 얘기인지 가리는 기준)."""
    from topicradar.cluster import GENERIC_TOKENS, _norm_token

    toks = {_norm_token(t) for t in tokenize(topic.name)} | set(topic.keywords[:6])  # type: ignore[attr-defined]
    return {t for t in toks if t not in GENERIC_TOKENS and len(t) >= 2}


def ranked_articles(topic: Topic, start: datetime | None, end: datetime | None) -> list[Signal]:
    """대본 사실 근거 기사(구간 안 국내 기사 우선, 나머지는 최신순)."""
    # 수동 일정(schedule)은 편집 메모라 대본 사실 문장으로 쓰지 않는다.
    sigs = [s for s in topic.signals if s.kind not in ("competitor", "own", "schedule", "community")]
    # 주제 이름과 고유어가 하나도 안 겹치는 기사는 빼고 쓴다(잘못 묶인 기사가 대본에 섞이지 않게).
    core = _core_tokens(topic)
    if core:
        related = [s for s in sigs if signal_tokens(s) & core]
        if len(related) >= min(2, len(sigs)):
            sigs = related
    in_win = [s for s in sigs if start and end and start <= s.published_at < end]
    rest = [s for s in sigs if s not in in_win]
    key = {"domestic": 0, "press": 1, "overseas": 2, "schedule": 3}
    return sorted(in_win, key=lambda s: (key.get(s.kind, 5), s.published_at)) + sorted(
        rest, key=lambda s: s.published_at, reverse=True
    )


def _ranked_signals(item: VideoItem, plan: VideoPlan) -> list[Signal]:
    return ranked_articles(item.topic, plan.window_start, plan.window_end)


def article_rows(sigs: list[Signal]) -> list[dict]:
    """대본 입력용 기사 목록(URL·시각·제목·요약)."""
    return [
        {
            "url": s.url,
            "time": s.published_at.astimezone(KST).strftime("%m-%d %H:%M"),
            "title": s.title,
            "summary": s.summary,
        }
        for s in sigs
    ]


# 네이버 뉴스 요약에 섞이는 사진 설명·통신사 머리말·잘린 문장(대본 사실 문장으로 쓰지 않는다)
_PRESS_HEAD_RE = re.compile(
    r"^\s*(?:[\[(（【][^\])）】]{0,30}=[^\])）】]{0,30}[\])）】]\s*)?(?:\S{2,4} 기자 = )?"
)
_CAPTION_RE = re.compile(
    r"(제공\)|사진=|\(사진|자료사진|무단 전재|재배포 금지|구독하기|"
    r"(?:하고|치고|듣고|보고|하며|웃고|서고|들어서고|입장하고|나서고|발언하고|앉아 있고|기다리고|살펴보고) 있(?:다|습니다)\.?$)"
)


def clean_sentence(sent: str) -> str | None:
    """사실 문장으로 쓸 수 있게 다듬는다. 사진 설명·잘린 문장('…', '...')·너무 짧은 조각이면 None."""
    sent = _PRESS_HEAD_RE.sub("", sent).strip()
    if sent.endswith(("...", "…", "..")) or len(sent) < 12 or _CAPTION_RE.search(sent):
        return None
    return sent


def _pick(
    sigs: list[Signal], pattern: re.Pattern[str] | None, used: set[str], limit: int, per_signal: int = 1
) -> list[tuple[str, str]]:
    """(문장, URL) 최대 limit개.

    pattern이 있으면 그 패턴에 맞는 문장, None이면 '사실' 문장(원인·다음 수순 표지가 없는 문장)을
    기사 순서대로 per_signal개까지 고른다. 이미 쓴 문장, 사진 설명·잘린 문장은 건너뛴다.
    """
    out: list[tuple[str, str]] = []
    for s in sigs:
        sents = [c for c in (clean_sentence(x) for x in split_sentences(s.summary)) if c]
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


def _source_for(text: str, item: VideoItem) -> str | None:
    """문장이 나온 기사 URL(앞 12자 일치)."""
    core = re.sub(r"\s+", "", text)[:12]
    for s in item.topic.signals:
        if core and core in re.sub(r"\s+", "", s.summary + s.title):
            return s.url
    return None


def _cited(text: str, item: VideoItem, prefix: str = "") -> list[str]:
    """출처를 찾아 붙이는 줄 만들기(수치·인용이 있을 때)."""
    url = _source_for(text, item)
    lines = _one_line(f"{prefix}{to_polite(text)}")
    if url and (numbers_in(text) or quotes_in(text)):
        lines.append(f"#출처: {url}")
    return lines


ALLOWED_EXTRA_HEADINGS = ("정리",)  # 10분 묶음의 마무리 단락


def section_heading(item: VideoItem, plan: VideoPlan | None = None) -> str:
    """영상 항목의 대본 헤더(`## ` 뒤). 오프라인 초안과 Claude 대본이 같은 헤더를 쓴다."""
    return short_name(item.topic.name, 20)


def _opinion_lines(item: VideoItem, tone: str) -> list[str]:
    """커뮤니티 주장(오프라인): 공감 많은 글·댓글을 옮겨 두고 말로 다듬게 표시한다."""
    op = item.opinion
    if op is None or not op.posts:
        return ["#TODO: 커뮤니티 의견 없음 — 주장 보강"]
    lines = [_OPINION_BRIDGE] if tone != "해설형" else []
    for text in [
        op.stance,
        *op.points[:2],
    ]:  # 커뮤니티 말 그대로(존댓말 변환은 하지 않는다 — 다듬을 곳으로 표시)
        lines += _one_line(text.rstrip(".") + ".")
    lines.append(f"#확인: 커뮤니티 주장({op.heat_label}) — 글·댓글을 옮겨 둔 것, 채널의 말로 다듬기")
    return lines


def topic_block(
    item: VideoItem, plan: VideoPlan, facts: int = 2, tone: str = prompts.DEFAULT_TONE
) -> list[str]:
    """주제 하나: 사실(기사) → 커뮤니티 주장 → 다음 수순(기사 속 일정)."""
    sigs = _ranked_signals(item, plan)
    used: set[str] = set()
    lines = [f"## {section_heading(item, plan)}"]
    fact_sents = _pick(sigs, None, used, facts)
    if len(fact_sents) < facts:  # 기사가 적으면 같은 기사의 다음 사실 문장까지
        fact_sents += _pick(sigs, None, used, facts - len(fact_sents), per_signal=3)
    if not fact_sents and sigs:  # 요약이 사진 설명·잘린 문장뿐이면 기사 제목이라도
        lines += _one_line(sigs[0].title) + [
            f"#출처: {sigs[0].url}",
            "#확인: 기사 제목뿐 — 사실 문장 보강",
        ]
    elif not fact_sents:
        lines.append("#TODO: 사실 확인 — 기사 없음")
    for n, (sent, url) in enumerate(fact_sents):
        lines += _sentence_lines(sent, url, force_source=(n == 0))
    lines += _opinion_lines(item, tone)
    nxt = _pick(sigs, _NEXT_RE, used, 1)
    if nxt:
        lines += _sentence_lines(*nxt[0])
    elif item.topic.follow_up and re.sub(r"\s+", "", item.topic.follow_up) not in used:
        lines += _one_line(to_polite(item.topic.follow_up))
    return lines


def heuristic_script(plan: VideoPlan, tone: str = prompts.DEFAULT_TONE) -> str:
    """오프라인 골격(말투는 연결 문장 정도만 — 입담과 주장 다듬기는 Claude 경로에서)."""
    lines: list[str] = []
    many = plan.format == "short" or len(plan.items) == 1
    for n, it in enumerate(plan.items):  # 목표 분량에 가깝게: 3분은 사실 6문장, 10분은 첫 주제 6·나머지 4
        block = topic_block(it, plan, facts=6 if many or n == 0 else 4, tone=tone)
        if n == 0 and plan.format == "long" and plan.link:
            block[1:1] = _one_line(plan.link)  # 첫 문장: 묶은 주제들을 잇는 고리
        lines += block + [""]
    if plan.format == "long":
        lead = plan.items[0].opinion if plan.items else None
        lines += ["## 정리"]
        lines += _one_line(plan.link) if plan.link else []
        if lead is not None and lead.posts:
            lines += _one_line(lead.stance.rstrip(".") + ".")
        lines.append("#확인: 마무리 — 묶은 주제들을 하나의 주장으로 맺기")
    return normalize_script("\n".join(lines))


# ---- Claude 경로 ---------------------------------------------------------


def tone_rules(tone: str) -> str:
    """말투 프리셋 본문(모르는 이름이면 기본값)."""
    return prompts.TONES.get(tone, prompts.TONES[prompts.DEFAULT_TONE])


def _opinion_payload(item: VideoItem) -> dict | None:
    op = item.opinion
    if op is None:
        return None
    return {
        "stance": op.stance,
        "points": op.points,
        "minority": op.minority,
        "questions": op.questions,
        "heat": op.heat_label,
        "samples": [{"kind": x.kind, "text": x.text, "votes": x.votes} for x in op.samples[:6]],
    }


def llm_script(llm: ClaudeLLM, plan: VideoPlan, tone: str = prompts.DEFAULT_TONE) -> str:
    """Claude로 대본 생성."""
    items = []
    for it in plan.items:
        sigs = _ranked_signals(it, plan)[:6]
        items.append(
            {
                "order": it.order,
                "heading": section_heading(it, plan),
                "topic": it.topic.name,
                "category": it.topic.category,
                "est_seconds": it.est_seconds,
                "opinion": _opinion_payload(it),
                "follow_up": it.topic.follow_up,
                "articles": article_rows(sigs),
            }
        )
    payload = {
        "format": plan.spec.name,
        "target_chars": list(plan.target_chars),
        "link": plan.link,
        "risk_notes": [f.message for f in plan.risk_flags if f.kind in ("법적", "여론조사")],
        "items": items,
    }
    system = "\n".join(
        [
            prompts.IDENTITY,
            prompts.SCRIPT_RULES,
            prompts.SCRIPT_STRUCTURE_RULES,
            tone_rules(tone),
            prompts.FORMAT_SCRIPT_RULES[plan.format],
        ]
    )
    prompt = "영상 계획과 근거 기사·커뮤니티 의견:\n" + json.dumps(payload, ensure_ascii=False)
    text = normalize_script(llm.text("script", system, prompt, role="script", max_tokens=SCRIPT_MAX_TOKENS))
    problems = structure_problems(text, plan)
    if problems:  # 계획과 다른 대본이면 한 번 고쳐 쓰게 한다
        log.warning("대본이 영상 계획과 다름 → 다시 씀: %s", "; ".join(problems))
        retry = (
            prompt
            + "\n\n방금 쓴 대본이 영상 계획과 달랐다: "
            + "; ".join(problems)
            + "\nitems 순서대로, 항목마다 heading을 `## heading` 그대로 달고 그 항목 기사·의견으로만 다시 써라."
        )
        text = normalize_script(
            llm.text("script_retry", system, retry, role="script", max_tokens=SCRIPT_MAX_TOKENS)
        )
    return text


OFFLINE_NOTE = "Claude를 쓰지 않아(키 없음·오프라인 모드) 기사 문장과 공감 많은 글을 옮겨 둔 골격입니다"


def write_script(
    plan: VideoPlan, llm: ClaudeLLM | None, tone: str = prompts.DEFAULT_TONE
) -> tuple[str, str, str | None]:
    """(대본, 'claude'|'offline', 골격이 된 이유). Claude가 실패하면 이유를 남기고 오프라인 골격으로."""
    if llm is not None:
        try:
            return llm_script(llm, plan, tone), "claude", None
        except LLMError as e:
            log.warning("Claude 대본 실패 → 오프라인 골격으로 대신: %s", e)
            return heuristic_script(plan, tone), "offline", f"Claude 대본 실패 — {e}"
    return heuristic_script(plan, tone), "offline", OFFLINE_NOTE


def draft_script(plan: VideoPlan, llm: ClaudeLLM | None, tone: str = prompts.DEFAULT_TONE) -> str:
    """대본 초안(실패 시 오프라인 골격)."""
    return write_script(plan, llm, tone)[0]


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


def _heading_matches(heading: str, item: VideoItem, plan: VideoPlan) -> bool:
    want = section_heading(item, plan)
    if heading.strip() == want:
        return True
    core = _core_tokens(item.topic)
    got = {t for t in tokenize(heading)}
    return bool(core) and len(got & core) >= max(1, min(2, len(core) // 2))


_SHORT_END_RE = re.compile(r"[다요죠]\.$|[다요죠][?!]$")


def _choppy_runs(spoken: list[str], short: int = 20, run: int = 3) -> int:
    """'~다.' '~요.'로 끝나는 짧은 한 줄 문장이 run번 이상 이어진 곳 수(말이 뚝뚝 끊기는 대본)."""
    count = streak = 0
    for ln in spoken:
        if len(ln) < short and _SHORT_END_RE.search(ln):
            streak += 1
            if streak == run:
                count += 1
        else:
            streak = 0
    return count


def structure_problems(text: str, plan: VideoPlan) -> list[str]:
    """대본 헤더가 영상 계획 항목과 맞는지(빠진 주제, 계획에 없는 단락)."""
    headings = [ln[3:].strip() for ln in text.splitlines() if ln.startswith("## ")]
    problems: list[str] = []
    claimed: set[int] = set()
    for it in plan.items:
        hit = next(
            (i for i, h in enumerate(headings) if i not in claimed and _heading_matches(h, it, plan)), None
        )
        if hit is None:
            problems.append(f"고른 주제가 대본에 없음: '{short_name(it.topic.name, 24)}'")
        else:
            claimed.add(hit)
    extra_ok = ALLOWED_EXTRA_HEADINGS if plan.format == "long" else ()
    for i, h in enumerate(headings):
        if i not in claimed and not (extra_ok and h.startswith(extra_ok)):
            problems.append(f"계획에 없는 단락: '{h}'")
    return problems


def _copied_lines(spoken: list[str], plan: VideoPlan, min_len: int = 10, prefix: int = 18) -> int:
    """커뮤니티 글 제목·댓글을 그대로 옮긴 곳 수(공백 빼고 비교: 짧은 글은 통째로, 긴 글은 앞 18자)."""
    texts = []
    for it in plan.items:
        for s in it.topic.buzz():
            texts.append(re.sub(r"\s+", "", s.title))
            texts += [re.sub(r"\s+", "", c.get("t", "")) for c in s.metric.get("top_comments", [])]
    keys = {t[:prefix] for t in texts if len(t) >= min_len}
    if not keys:
        return 0
    joined = re.sub(r"\s+", "", "".join(spoken))
    return sum(1 for k in keys if k in joined)


def validate_script(text: str, plan: VideoPlan) -> list[str]:
    """대본 규칙 점검 결과(경고 문장 목록)."""
    warns: list[str] = []
    lines = [ln.strip() for ln in text.splitlines()]
    spoken = spoken_lines(text)
    n = count_chars(lines)
    lo, hi = plan.target_chars
    if not lo <= n <= hi:
        warns.append(f"글자 수 {n:,}자 — 목표 {lo:,}~{hi:,}자({plan.spec.name})")
    choppy = _choppy_runs(spoken)
    if choppy:
        warns.append(
            f"짧은 문장이 연달아 끊김 {choppy}곳(20자 미만 '~다.'·'~요.' 3번 연속) — 연결어미로 이어 쓰기"
        )
    if not any(ln.startswith("## ") for ln in lines):
        warns.append("`## 주제명` 헤더 없음(newsclip 장면 분할 불가)")
    else:
        warns += [f"계획과 다름 — {p}" for p in structure_problems(text, plan)]
    # 사실 근거는 뉴스·일정만(커뮤니티 글은 출처가 될 수 없다)
    sources = " ".join(s.text for it in plan.items for s in it.topic.signals if s.kind != "community")
    src_nums = set(numbers_in(sources))
    bad_nums, bad_quotes = [], []
    for ln in lines:
        if not ln or ln.startswith("#"):
            continue
        bad_nums += [x for x in numbers_in(ln) if x not in src_nums]
        bad_quotes += [q for q in quotes_in(ln) if q not in sources]
    if bad_nums:
        warns.append(
            f"수집 기사에 없는 수치 {len(bad_nums)}개: {', '.join(sorted(set(bad_nums))[:5])} — 삭제 또는 출처 확인"
        )
    if bad_quotes:
        warns.append(f"수집 기사에 없는 인용 {len(bad_quotes)}개 — 삭제 또는 출처 확인")
    notes = [ln for ln in lines if ln.startswith("#") and not ln.startswith(("## ", "#TODO", "#확인"))]
    if notes:
        warns.append(f"출처·주석 줄 {len(notes)}개(예: '{notes[0][:30]}') — 대본에서 빼기")
    copied = _copied_lines(spoken, plan)
    if copied:
        warns.append(f"커뮤니티 글·댓글을 그대로 옮긴 곳 {copied}개 — 채널의 말로 바꿀 것")
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
    vague = [w for ln in spoken for w in VAGUE_WORDS if w in ln]
    if len(vague) > VAGUE_MAX:
        warns.append(
            f"뭉뚱그린 말 {len(vague)}번({', '.join(sorted(set(vague)))}) — 누가 언제 무슨 말·행동을 했는지로 바꿀 것"
        )
    todos = sum(1 for ln in lines if ln.startswith(("#TODO", "#확인")))
    if todos:
        warns.append(f"편집자 확인 주석 {todos}개(#TODO/#확인) — 녹음 전 처리")
    return warns
