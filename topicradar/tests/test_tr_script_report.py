from datetime import timedelta

from topicradar.history import pearson
from topicradar.models import Opinion, OpinionSample, RiskFlag, VideoItem, VideoPlan, target_chars
from topicradar.report import board_text, render_report, slack_text
from topicradar.script import (
    draft_script,
    heuristic_script,
    llm_script,
    normalize_script,
    spoken_lines,
    validate_script,
)

from trh import kst, sig, topic

NOW = kst("2026-10-08 18:00")


def _op(stance, votes=100, points=()):
    return Opinion(
        stance=stance,
        points=list(points),
        posts=2,
        comments=120,
        votes=votes,
        samples=[OpinionSample(site="다모앙", kind="post", text=stance, votes=votes, comments=60)],
    )


def _topics():
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
            sig(
                "감시자산 예산 누가 깎았죠",
                "2026-10-08 11:00",
                kind="community",
                site="보배드림",
                comments=58,
                votes=66,
            ),
        ],
        keywords=["국방위", "국감", "탐지"],
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
        keywords=["본회의", "표결", "기본법"],
    )
    for t in (head, rec):
        t.total = 70
    return head, rec


def _plan(fmt="long"):
    head, rec = _topics()
    items = [
        VideoItem(
            topic=head,
            order=1,
            opinion=_op("안보 예산 깎은 건 새한당이다", points=["회의록에 다 나온다"]),
            est_seconds=330,
        )
    ]
    if fmt == "long":
        items.append(
            VideoItem(topic=rec, order=2, opinion=_op("끝까지 반대한 새한당을 기억하자"), est_seconds=270)
        )
    return VideoPlan(
        id=f"2026-10-08_1805_{fmt}",
        format=fmt,
        created_at=NOW,
        items=items,
        link="국감과 본회의, 모두 새한당의 반대로 이어지는 이야기입니다." if fmt == "long" else None,
        target_chars=target_chars(fmt, 300),
        titles=["안보 예산 깎은 건 누구", "a", "b"],
        thumbnail_text="국감 왜?",
        window_start=NOW - timedelta(hours=24),
        window_end=NOW,
        risk_flags=[RiskFlag(kind="법적", topic_id="p", message="의혹 구분")],
    )


def test_target_chars():
    assert target_chars("long", 300) == (2700, 3300) and target_chars("short", 300) == (810, 990)
    assert target_chars("short", 350) == (940, 1160)


def test_heuristic_long_script():
    plan = _plan("long")
    text = heuristic_script(plan)
    heads = [ln for ln in text.splitlines() if ln.startswith("## ")]
    assert heads == ["## 국방위 국감 질타", "## 본회의 표결", "## 정리"]
    lines = text.splitlines()
    assert lines[1].startswith("국감과 본회의,")  # 첫 문장 = 묶은 주제들을 잇는 고리
    assert "안보 예산 깎은 건 새한당이다." in text and "회의록에 다 나온다." in text  # 커뮤니티 주장
    assert "#출처" not in text and "#확인: 커뮤니티 주장(글 2 · 댓글 120 · 공감 100)" in text
    assert all(len(ln) <= 25 for ln in spoken_lines(text))
    warns = validate_script(text, plan)
    assert any("글자 수" in w and "10분 묶음" in w for w in warns)
    assert not any("수집 기사에 없는 수치" in w for w in warns) and not any("계획과 다름" in w for w in warns)


def test_heuristic_short_script_and_missing_news():
    plan = _plan("short")
    text = heuristic_script(plan)
    assert [ln for ln in text.splitlines() if ln.startswith("## ")] == ["## 국방위 국감 질타"]
    rumor = topic(
        "r", "대표 사퇴설", [sig("대표 사퇴설 도는데", "2026-10-08 10:00", kind="community", comments=40)]
    )
    plan.items = [VideoItem(topic=rumor, order=1, opinion=_op("사퇴설 출처부터 밝혀라"))]
    text = heuristic_script(plan)
    assert "#TODO: 사실 확인 — 기사 없음" in text and "사퇴설 출처부터 밝혀라." in text
    plan.items[0].opinion = None
    assert "#TODO: 커뮤니티 의견 없음" in heuristic_script(plan)


def test_validate_catches_violations():
    plan = _plan()
    bad = (
        '## 국방위 국감 질타\n충격적인 소식입니다.\n지지율이 99%입니다.\n"지어낸 말"이라고 했습니다.\n#출처: https://not.collected/x\n'
        + "이" * 30
        + "\n## 정리\n끝.\n"
    )
    warns = " | ".join(validate_script(bad, plan))
    for frag in (
        "금지 표현",
        "수집 기사에 없는 수치",
        "수집 기사에 없는 인용",
        "출처·주석 줄 1개",
        "25자 초과",
        "고른 주제가 대본에 없음: '본회의 표결'",
    ):
        assert frag in warns
    assert "헤더 없음" in " ".join(validate_script("헤더 없는 대본\n", plan))
    assert normalize_script("```\n줄1\n\n\n줄2\n```") == "줄1\n\n줄2\n"
    # 3분 단독에는 '정리' 단락도 없어야 한다
    short = _plan("short")
    assert any(
        "계획에 없는 단락: '정리'" in w
        for w in validate_script("## 국방위 국감 질타\n가.\n## 정리\n나.\n", short)
    )


def test_llm_script_gets_opinion_format_and_falls_back():
    plan = _plan("long")
    seen = {}

    class LLM:
        def text(self, task, system, prompt, role="script", **kw):
            seen.update(system=system, prompt=prompt)
            return "```\n## 국방위 국감 질타\n탐지가 늦었습니다.\n## 본회의 표결\n가결됐습니다.\n```"

    assert (
        llm_script(LLM(), plan) == "## 국방위 국감 질타\n탐지가 늦었습니다.\n## 본회의 표결\n가결됐습니다.\n"
    )
    assert (
        "10분 묶음 규칙" in seen["system"]
        and "진보 진영 편" in seen["system"]
        and "재미가 첫째" in seen["system"]
    )
    assert "출처·URL·편집 주석" in seen["system"]
    assert "사실을 또렷하게" in seen["system"] and "예고편처럼 감추지" in seen["system"]
    assert (
        '"stance": "안보 예산 깎은 건 새한당이다"' in seen["prompt"]
        and '"target_chars": [2700, 3300]' in seen["prompt"]
    )
    assert '"link": "국감과 본회의' in seen["prompt"] and "https://n.example/p1" in seen["prompt"]
    llm_script(LLM(), _plan("short"))
    assert "3분 단독 규칙" in seen["system"]

    class Broken:
        def text(self, *a, **k):
            from studio_core.llm import LLMError

            raise LLMError("x")

    assert draft_script(plan, Broken()).startswith("## ")


def test_script_follows_plan_structure_and_retries():
    from topicradar.script import section_heading, structure_problems

    plan = _plan()
    good = "\n".join(f"## {section_heading(it, plan)}\n문장입니다." for it in plan.items) + "\n## 정리\n끝.\n"
    assert structure_problems(good, plan) == []
    off = "## 금융권 해킹\n금융사가 뚫렸습니다.\n## 국방위 국감 질타\n질타가 이어졌습니다.\n"
    probs = structure_problems(off, plan)
    assert any("계획에 없는 단락: '금융권 해킹'" in p for p in probs)
    assert sum("고른 주제가 대본에 없음" in p for p in probs) == 1
    assert any("계획과 다름" in w for w in validate_script(off, plan))
    calls = []

    class Drifting:  # 처음엔 계획과 다른 대본, 고쳐 달라고 하면 맞게
        def text(self, task, system, prompt, role="script", **kw):
            calls.append(task)
            assert "구성 지키기" in system and '"heading"' in prompt
            return off if task == "script" else good

    assert llm_script(Drifting(), plan) == good and calls == ["script", "script_retry"]


def test_report_slack_and_board_text(tmp_path):
    from topicradar.models import Board, BoardTopic, Bundle

    plan = _plan()
    md = render_report(plan, tmp_path / "script.txt", ["글자 수 부족"])
    assert (
        "| 순서 | 주제 | 카테고리 | 반응도 | 커뮤니티 반응 | 커뮤니티 주장 | 분량 | 근거 기사 | 리스크 |"
        in md
    )
    assert "**안보 예산 깎은 건 새한당이다**" in md and "  - 회의록에 다 나온다" in md
    assert "주제를 잇는 고리: 국감과 본회의" in md and "⚠️ 글자 수 부족" in md and "[법적]" in md
    assert "## 제목 3안" in md and "썸네일 문구" in md and "보배드림1 (댓글 58 · 공감 66)" in md
    st = slack_text(plan, tmp_path)
    assert st.startswith("*10분 묶음*") and "→ 안보 예산 깎은 건 새한당이다" in st
    board = Board(
        id="b",
        created_at=NOW,
        window_start=NOW - timedelta(hours=24),
        window_end=NOW,
        topics=[
            BoardTopic(
                topic_id="h",
                name="국방위 국감",
                category="외교안보",
                score=90,
                reason="",
                opinion=_op("깎은 건 새한당"),
            ),
            BoardTopic(
                topic_id="p",
                name="본회의 표결",
                category="국회",
                score=80,
                reason="",
                opinion=_op("기억하자"),
            ),
        ],
        bundles=[Bundle(topic_ids=["h", "p"], link="둘 다 새한당 이야기입니다.")],
    )
    bt = board_text(board)
    assert "1. 국방위 국감 (90, 글 2 · 댓글 120 · 공감 100) — 깎은 건 새한당" in bt
    assert "10분 묶음 추천: 국방위 국감 + 본회의 표결" in bt


def test_history_helpers():
    assert abs(pearson([1, 2, 3], [2, 4, 6]) - 1.0) < 1e-9
    assert pearson([1, 1, 1], [1, 2, 3]) is None and pearson([1, 2], [1, 2]) is None


def test_tone_presets_in_prompt_and_offline():
    from topicradar import prompts

    plan = _plan()
    seen = {}

    class LLM:
        def text(self, task, system, prompt, role="script", **kw):
            seen["system"] = system
            return "## 국방위 국감 질타\n결론부터 말씀드립니다.\n## 본회의 표결\n가.\n"

    llm_script(LLM(), plan, "확신형+입담형")
    assert "확신형 구어 + 예능 입담" in seen["system"] and "인신공격" in seen["system"]
    llm_script(LLM(), plan, "없는말투")
    assert prompts.TONES[prompts.DEFAULT_TONE] in seen["system"]
    assert "이 소식에 대한 반응은 분명합니다." in heuristic_script(plan, "확신형+입담형")
    assert "이 소식에 대한 반응은 분명합니다." not in heuristic_script(plan, "해설형")


def test_validate_tone_checks():
    plan = _plan()
    text = "## a\n그는 반대했다고 밝혔습니다.\n입장을 밝혔다.\n결과였다.\n존나 웃기죠.\n"
    warns = " | ".join(validate_script(text, plan))
    assert "기사체 종결 3줄" in warns and "비속어" in warns


def test_validate_vague_words():
    plan = _plan()
    vague = "## a\n이건 본질은 놔두고\n말꼬리만 잡는 공세입니다.\n전형적인 물타기죠.\n"
    assert any("뭉뚱그린 말 4번" in w for w in validate_script(vague, plan))
    ok = "## a\n국민의힘의 공세는\n이번이 두 번째입니다.\n"
    assert not any("뭉뚱그린" in w for w in validate_script(ok, plan))


def test_script_uses_only_articles_about_the_topic():
    from topicradar.script import _ranked_signals

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
            sig("대구 붕괴 대통령 골프 실화냐", "2026-10-08 11:00", kind="community", comments=90),
        ],
        keywords=["골프장", "대구", "붕괴사고"],
    )
    item = VideoItem(topic=mixed, order=1)
    plan = _plan("short")
    plan.items = [item]
    titles = [x.title for x in _ranked_signals(item, plan)]
    assert all("해킹" not in t for t in titles) and len(titles) == 2  # 커뮤니티 글도 사실 근거에서 뺀다


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
    assert any(
        "짧은 문장이 연달아" in w
        for w in validate_script("## 국방위 국감 질타\n" + "\n".join(choppy), _plan("short"))
    )


def test_write_script_reports_why_it_is_a_skeleton():
    from studio_core.llm import LLMError
    from topicradar.script import OFFLINE_NOTE, write_script

    plan = _plan("short")
    text, mode, note = write_script(plan, None)
    assert mode == "offline" and note == OFFLINE_NOTE and text.startswith("## ")

    class Rejected:
        def text(self, *a, **k):
            raise LLMError("script: Claude 호출 실패 — 키가 맞지 않습니다(오타이거나 삭제·만료된 키)")

    _t, mode, note = write_script(plan, Rejected())
    assert mode == "offline" and "Claude 대본 실패" in note and "삭제·만료" in note
    seen = {}

    class Good:
        def text(self, task, system, prompt, role="script", **kw):
            seen["max_tokens"] = kw.get("max_tokens")
            return "## 국방위 국감 질타\n가.\n"

    assert write_script(plan, Good())[1:] == ("claude", None)
    assert seen["max_tokens"] == 32000  # 깊게 생각해도 잘리지 않게
    md = render_report(plan.model_copy(update={"script_mode": "offline", "script_note": "이유"}), "s.txt", [])
    assert "- 대본: ⚠️ 오프라인 골격 — 이유" in md


def test_clean_sentence_drops_photo_captions_and_cut_text():
    from topicradar.script import clean_sentence

    caption = (
        "박원순 전 시장과의 일화 소개하며 SNS 상 악성 댓글 비판 이재명 대통령이 8일 국방과학연구소의 "
        "극초음속 활공비행체(HGV) 최초 시험발사 성공 모습을 참관하며 박수를 치고 있다."
    )
    assert clean_sentence(caption) is None  # 사진 설명
    assert clean_sentence("뉴시스(청와대 제공) 이재명 대통령은...") is None  # 사진 출처 + 잘린 문장
    assert (
        clean_sentence("[서울=뉴시스] 홍길동 기자 = 대통령은 11일 일화를 소개했다.")
        == "대통령은 11일 일화를 소개했다."
    )
    assert clean_sentence("(서울=연합뉴스) 국회는 8일 본회의를 열었다.") == "국회는 8일 본회의를 열었다."
    assert clean_sentence("짧다.") is None
    head, _ = _topics()
    head.signals[0].summary = caption + " 뉴시스(청와대 제공) 이재명 대통령은..."
    plan = _plan("short")
    plan.items[0].topic = head
    text = heuristic_script(plan)
    assert "박수를 치고" not in text and "#확인: 기사 제목뿐" in text  # 쓸 문장이 없으면 제목 + 확인 표시
