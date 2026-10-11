from topicradar.history import hit_rates, pearson, suggest_weights, title_pattern
from topicradar.models import Chain, RiskFlag, ScoreDetail, SlotItem, SlotPlan
from topicradar.report import render_report, slack_text
from topicradar.script import (
    draft_script,
    heuristic_script,
    llm_script,
    normalize_script,
    spoken_lines,
    validate_script,
)
from topicradar.slots import window_for

from trh import kst, sig, topic

DAY = kst("2026-10-08 00:00").date()
S, E = window_for("evening", DAY)


def _plan(slot="evening"):
    s, e = window_for(slot, DAY)
    head = topic(
        "h",
        "국방위 국감 질타",
        [
            sig(
                "국방위 국감, 북 미사일 탐지 지연 질타",
                "2026-10-08 10:20",
                "국방부 국정감사에서 탐지 지연 질타가 이어졌다. 발사 준비 시간이 짧아졌기 때문이라고 해명했다. 국방위는 예산 증액을 검토하기로 했다.",
                url="https://n.example/h1",
            ),
        ],
    )
    rec = topic(
        "p",
        "본회의 표결",
        [
            sig(
                "플랫폼노동자 기본법 본회의 가결",
                "2026-10-08 14:50",
                "국회는 8일 본회의를 열고 플랫폼노동자 기본법을 가결했다. 새한당은 표결에 불참했다. 법은 공포 후 6개월 뒤 시행된다.",
                url="https://n.example/p1",
            ),
        ],
        scheduled_at=kst("2026-10-08 14:00"),
    )
    miss = topic(
        "m",
        "1심 선고",
        [
            sig(
                "재판부, 1심 선고 연기",
                "2026-10-08 14:30",
                "재판부가 8일 1심 선고를 연기했다. 검찰이 새 증거를 냈기 때문이다. 재판부는 다음 달 선고기일을 다시 잡을 예정이다.",
                url="https://n.example/m1",
            ),
        ],
        scheduled_at=kst("2026-10-08 14:00"),
    )
    for t in (head, rec, miss):
        t.total = 70
        t.scores = {k: ScoreDetail(score=70, reason="r") for k in ("interest", "gap", "persistence", "fit")}
    items = [
        SlotItem(topic=head, order=1, role="헤드라인"),
        SlotItem(
            topic=rec,
            order=2,
            role="포인트회수",
            expected="가결 전망",
            actual="국회는 8일 본회의를 열고 플랫폼노동자 기본법을 가결했습니다.",
            hit="hit",
        ),
        SlotItem(
            topic=miss,
            order=3,
            role="포인트회수",
            expected="유죄 가능성",
            actual="재판부가 8일 1심 선고를 연기했습니다.",
            hit="miss",
            hit_reason="연기",
        ),
    ]
    return SlotPlan(
        date=DAY,
        slot=slot,
        items=items,
        titles=["국방위 국감, 진짜 이유", "a 다음", "결국 b"],
        thumbnail_text="국감 왜?",
        window_start=s,
        window_end=e,
        risk_flags=[RiskFlag(kind="법적", topic_id="m", message="의혹 구분")],
    )


def test_heuristic_evening_script_rules():
    plan = _plan()
    text = heuristic_script(plan)
    assert text.startswith("## ")
    assert "아침에 말씀드린 대로" in text and "아침 예상과 달리" in text and "그래서 다음은" in text
    assert "#출처: https://n.example/p1" in text
    assert all(len(ln) <= 25 for ln in spoken_lines(text))
    warns = validate_script(text, plan)
    assert any("글자 수" in w for w in warns)
    assert not any("수집 기사에 없는 수치" in w for w in warns)


def test_morning_and_weekly_offline_drafts():
    m = _plan()
    m.slot = "morning"
    m.items[1].role = "오늘의포인트"
    m.items[1].impact = "법이 통과되면 6개월 뒤 시행됩니다."
    m.items[2].role = "오늘의포인트"
    text = heuristic_script(m)
    assert "오늘 볼 포인트 2 가지입니다." in text and "#TODO: '어떻게 되면" in text
    w = _plan("weekly")
    w.items[0].role = "줄기"
    w.items[1].role = "서브"
    w.items[2].role = "내일쟁점"
    w.chains = [Chain(title="국감", summary="국방위 국감 → 본회의 표결", topic_ids=["h", "p"])]
    wt = heuristic_script(w)
    assert "## 이번 주 인과 사슬" in wt and "## 다음 주 전망" in wt and "#확인:" in wt


def test_validate_catches_violations():
    plan = _plan()
    bad = (
        '## 헤드\n충격적인 소식입니다.\n지지율이 99%입니다.\n"지어낸 말"이라고 했습니다.\n#출처: https://not.collected/x\n'
        + "이" * 30
        + "\n"
    )
    warns = " | ".join(validate_script(bad, plan))
    for frag in (
        "금지 표현",
        "수집 기사에 없는 수치",
        "수집 기사에 없는 인용",
        "수집되지 않은 출처",
        "25자 초과",
        "아침에 말씀드린 대로",
    ):
        assert frag in warns
    assert "헤더 없음" in " ".join(validate_script("헤더 없는 대본\n", plan))
    assert normalize_script("```\n줄1\n\n\n줄2\n```") == "줄1\n\n줄2\n"


def test_llm_script_path_and_fallback():
    plan = _plan()

    class LLM:
        def text(self, task, system, prompt, role="script", **kw):
            assert "아침에 말씀드린 대로" in system and "https://n.example/p1" in prompt
            return "```\n## 국방위\n탐지가 늦었습니다.\n```"

    assert llm_script(LLM(), plan) == "## 국방위\n탐지가 늦었습니다.\n"

    class Broken:
        def text(self, *a, **k):
            from studio_core.llm import LLMError

            raise LLMError("x")

    assert draft_script(plan, Broken()).startswith("## ")


def test_report_and_slack(tmp_path):
    plan = _plan()
    md = render_report(plan, tmp_path / "script.txt", ["글자 수 부족"])
    assert "| 순서 | 역할 | 주제 | 카테고리 | 총점(세부) | 추세 | 커뮤니티 반응 | actual / hit |" in md
    assert (
        "✅ hit" in md
        and "❌ miss" in md
        and "## 제목 3안" in md
        and "썸네일 문구" in md
        and "⚠️ 글자 수 부족" in md
    )
    assert "[법적]" in md
    assert "포인트회수" in slack_text(plan, tmp_path)


def test_history_helpers():
    assert abs(pearson([1, 2, 3], [2, 4, 6]) - 1.0) < 1e-9
    assert pearson([1, 1, 1], [1, 2, 3]) is None and pearson([1, 2], [1, 2]) is None
    assert title_pattern("표결을 미룬 진짜 이유") == "이유" and title_pattern("아무 제목") == "기타"
    assert suggest_weights([], {"interest": 40, "gap": 20, "persistence": 10, "fit": 30})[0] is None
    rates = hit_rates([_plan()])
    assert rates["2026-W41"]["hit"] == 1 and rates["2026-W41"]["miss"] == 1


def test_tone_presets_in_prompt_and_offline():
    from topicradar import prompts

    plan = _plan()
    seen = {}

    class LLM:
        def text(self, task, system, prompt, role="script", **kw):
            seen["system"] = system
            return "## a\n결론부터 말씀드립니다.\n"

    llm_script(LLM(), plan, "확신형+입담형")
    assert "확신형 구어 + 예능 입담" in seen["system"] and "인신공격" in seen["system"]
    llm_script(LLM(), plan, "없는말투")
    assert prompts.TONES[prompts.DEFAULT_TONE] in seen["system"]
    assert "근데 왜 이렇게 됐을까요?" in heuristic_script(plan, "확신형+입담형")
    assert "근데 왜 이렇게 됐을까요?" not in heuristic_script(plan, "해설형")


def test_validate_tone_checks():
    plan = _plan()
    text = "## a\n그는 반대했다고 밝혔습니다.\n입장을 밝혔다.\n결과였다.\n존나 웃기죠.\n"
    warns = " | ".join(validate_script(text, plan))
    assert "기사체 종결 3줄" in warns and "비속어" in warns


def test_script_follows_plan_structure_and_retries():
    from topicradar.script import section_heading, structure_problems

    plan = _plan()
    good = "\n".join(f"## {section_heading(it, plan)}\n문장입니다." for it in plan.items) + "\n"
    assert structure_problems(good, plan) == []
    off = "## 금융권 해킹\n금융사가 뚫렸습니다.\n## 국방위 국감 질타\n질타가 이어졌습니다.\n"
    probs = structure_problems(off, plan)
    assert any("편성에 없는 단락: '금융권 해킹'" in p for p in probs)
    assert sum("편성 주제가 대본에 없음" in p for p in probs) == 2
    assert any("편성과 다름" in w for w in validate_script(off, plan))

    calls = []

    class Drifting:  # 처음엔 편성과 다른 대본, 고쳐 달라고 하면 맞게
        def text(self, task, system, prompt, role="script", **kw):
            calls.append(task)
            assert "편성 지키기" in system and '"heading"' in prompt
            return off if task == "script" else good

    assert llm_script(Drifting(), plan) == good and calls == ["script", "script_retry"]


def test_script_uses_only_articles_about_the_topic():
    from topicradar.script import _ranked_signals

    s, e = window_for("evening", DAY)
    mixed = topic(
        "g",
        "대통령, 골프장서 대구 붕괴사고 보고받고 신속수습 지시",
        [
            sig(
                "대통령, 골프장서 대구 붕괴사고 보고받아",
                "2026-10-08 09:00",
                "대통령이 골프장에서 대구 붕괴사고 보고를 받았다.",
            ),
            sig(
                "대구 붕괴사고 신속수습 지시",
                "2026-10-08 10:00",
                "대통령은 대구 붕괴사고 신속수습을 지시했다.",
            ),
            sig("금융보안원 경고에도 금융사 해킹", "2026-10-08 07:00", "금융보안원이 위험을 알렸다."),
        ],
        keywords=["골프장", "대구", "붕괴사고"],
    )
    item = SlotItem(topic=mixed, order=1, role="헤드라인")
    plan = SlotPlan(date=DAY, slot="evening", items=[item], window_start=s, window_end=e)
    titles = [x.title for x in _ranked_signals(item, plan)]
    assert all("해킹" not in t for t in titles) and len(titles) == 2


def test_choppy_script_warning():
    from topicradar.script import _choppy_runs

    choppy = [
        "오늘 헤드라인은 해킹입니다.",
        "결론부터 말씀드립니다.",
        "금융사들이 뚫렸습니다.",
        "경고를 받았거든요.",
    ]
    flowing = ["금융보안원이 2주 전에 이미", "위험을 알렸는데도", "금융사들이 그대로 뚫렸습니다."]
    assert _choppy_runs(choppy) == 1 and _choppy_runs(flowing) == 0
    plan = _plan()
    assert any(
        "짧은 문장이 연달아" in w for w in validate_script("## 국방위 국감 질타\n" + "\n".join(choppy), plan)
    )
