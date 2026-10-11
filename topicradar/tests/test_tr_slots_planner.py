from topicradar.db import Store
from topicradar.models import ScoreDetail, SlotItem, SlotPlan
from topicradar.planner import (
    fill_plan_text,
    heuristic_chains,
    heuristic_expected,
    heuristic_titles,
    judge_hit,
    outcome_class,
    thumbnail_for,
)
from topicradar.risk import plan_risks, title_problems, topic_risks
from topicradar.slots import allocate_seconds, balance_categories, select_evening, select_morning, window_for

from trh import kst, sig, topic

DAY = kst("2026-10-08 00:00").date()


def scored(t, total, persistence=60.0):
    t.total = total
    t.scores = {"persistence": ScoreDetail(score=persistence, reason="")}
    return t


def test_windows():
    s, e = window_for("morning", DAY)
    assert (s, e) == (kst("2026-10-07 17:00"), kst("2026-10-08 05:30"))
    assert window_for("evening", DAY) == (kst("2026-10-08 05:30"), kst("2026-10-08 17:00"))
    ws, we = window_for("weekly", kst("2026-10-11 00:00").date())
    assert (ws, we) == (kst("2026-10-05 00:00"), kst("2026-10-10 17:00"))


def test_balance_categories_replace_and_remove():
    econ = [
        scored(topic(f"e{i}", f"경제{i}", [sig(f"e{i}", "2026-10-08 01:00")], category="경제"), 50 + i)
        for i in range(3)
    ]
    pol = scored(topic("p", "국회", [sig("p", "2026-10-08 01:00")], category="국회"), 40)
    items = [SlotItem(topic=t, order=0, role="서브") for t in econ]
    notes = balance_categories(items, lambda _it: [*econ, pol], replaceable={"서브"})
    cats = [i.topic.category for i in items]
    assert cats.count("경제") <= len(items) / 2 and "국회" in cats and "교체" in notes[0]
    items2 = [SlotItem(topic=t, order=0, role="서브") for t in econ]
    notes2 = balance_categories(items2, lambda _it: [], replaceable={"서브"})
    assert len(items2) == 1 or sum(i.topic.category == "경제" for i in items2) <= len(items2) / 2
    assert "제외" in notes2[0]


def test_morning_dedupe_and_points():
    s, e = window_for("morning", DAY)
    ov = scored(
        topic(
            "ov",
            "미 하원 임시예산안 가결",
            [sig("미 하원 임시예산안 가결", "2026-10-08 02:30", kind="overseas")],
            category="국제",
            kind="overseas",
        ),
        70,
    )
    en_only = scored(
        topic(
            "en",
            "Wall Street",
            [sig("Wall Street tumbles", "2026-10-07 22:00", kind="overseas")],
            category="국제",
            kind="overseas",
        ),
        90,
    )
    prev = scored(topic("prev", "막말 설전 이틀째", [sig("막말 설전 이틀째", "2026-10-07 20:40")]), 80)
    p1 = scored(
        topic(
            "p1",
            "본회의 표결",
            [sig("본회의 표결 예정", "2026-10-07 19:30")],
            scheduled_at=kst("2026-10-08 14:00"),
        ),
        85,
    )
    p2 = scored(
        topic(
            "p2",
            "국무회의",
            [sig("국무회의 상정", "2026-10-07 21:00")],
            scheduled_at=kst("2026-10-08 10:00"),
            category="정부",
        ),
        80,
    )
    p3 = scored(
        topic(
            "p3",
            "1심 선고",
            [sig("선고 예정", "2026-10-07 21:30")],
            scheduled_at=kst("2026-10-08 14:00"),
            category="사법",
        ),
        75,
    )
    covered_followup = scored(
        topic(
            "cf",
            "지역의료",
            [sig("지역의료", "2026-10-07 21:00")],
            scheduled_at=kst("2026-10-08 10:00"),
            category="복지",
        ),
        60,
    )
    items, notes, flags = select_morning(
        [ov, en_only, prev, p1, p2, p3, covered_followup],
        DAY,
        s,
        e,
        prev_evening_ids={"prev", "cf"},
        allow_foreign=False,
    )
    roles = [(i.role, i.topic.id) for i in items]
    assert roles[0] == ("헤드라인", "ov")
    assert [t for r, t in roles if r == "오늘의포인트"] == ["p1", "p2", "p3"]
    assert any("전날 저녁에 다룸" in n for n in notes) and any("영문 기사뿐" in n for n in notes)
    assert "prev" not in {t for _, t in roles}
    assert not [f for f in flags if "포인트" in f.message]


def test_morning_flags_when_few_points():
    s, e = window_for("morning", DAY)
    only = scored(topic("x", "국회", [sig("국회 소식", "2026-10-08 01:00")]), 50)
    items, notes, flags = select_morning([only], DAY, s, e, set())
    assert any("해외 헤드라인 없음" in f.message for f in flags)
    assert any("포인트가 0개" in f.message for f in flags)


def test_evening_recall_and_exclusions(tmp_path):
    store = Store(tmp_path / "t.db")
    s, e = window_for("evening", DAY)
    point = topic(
        "p1",
        "본회의 표결",
        [
            sig("본회의 표결 예정", "2026-10-07 19:30"),
            sig("본회의서 법안 가결", "2026-10-08 14:50", "국회는 8일 본회의에서 법안을 가결했다."),
        ],
        scheduled_at=kst("2026-10-08 14:00"),
    )
    morning_sub = scored(
        topic(
            "ms",
            "국제유가",
            [sig("국제유가 급등", "2026-10-08 03:00"), sig("유가 추가 상승", "2026-10-08 09:00")],
        ),
        70,
    )
    head = scored(
        topic(
            "h",
            "국방위 국감",
            [sig("국방위 국감 질타", "2026-10-08 10:20"), sig("감시자산 예산", "2026-10-08 13:20")],
            category="국회",
        ),
        88,
    )
    subs = [
        scored(
            topic(f"s{i}", f"서브{i}", [sig(f"서브 기사 {i}", "2026-10-08 11:00")], category="선거"), 60 - i
        )
        for i in range(5)
    ]
    tomorrow = scored(
        topic(
            "tm",
            "내일 선고",
            [sig("9일 선고 예정", "2026-10-08 12:00")],
            scheduled_at=kst("2026-10-09 14:00"),
            category="사법",
        ),
        55,
    )
    morning = SlotPlan(
        date=DAY,
        slot="morning",
        items=[
            SlotItem(topic=morning_sub, order=1, role="헤드라인"),
            SlotItem(topic=point, order=2, role="오늘의포인트", expected="가결이 유력하다는 전망입니다."),
        ],
    )
    items, notes, flags = select_evening(
        [point, morning_sub, head, *subs, tomorrow], DAY, s, e, morning, store
    )
    roles = [i.role for i in items]
    assert roles[0] == "헤드라인" and items[0].topic.id == "h"
    assert 2 <= roles.count("서브") <= 4 and roles.count("포인트회수") == 1 and roles[-1] == "내일쟁점"
    assert items[-1].topic.id == "tm"
    plan = SlotPlan(date=DAY, slot="evening", items=items, window_start=s, window_end=e)
    fill_plan_text(plan, None)
    rec = plan.items_by_role("포인트회수")[0]
    assert rec.hit == "hit" and "가결" in rec.actual
    # 아침 서브(국제유가)는 새 신호 1건뿐이라 제외
    assert any("국제유가" in n for n in notes)


def test_outcome_and_hit_judgement():
    assert outcome_class("가결이 유력하다") == "pass"
    assert outcome_class("선고를 연기했다") == "fail"
    assert outcome_class("공방이 예상된다") is None
    assert judge_hit("가결 전망", "가결됐다")[0] == "hit"
    assert judge_hit("유죄 선고 가능성", "선고를 연기했다")[0] == "miss"
    assert judge_hit("가결 전망", None)[0] == "pending"
    assert judge_hit("공방 예상", "공방이 이어졌다")[0] == "pending"


def test_expected_titles_thumbnail_chains():
    t = topic(
        "p",
        "본회의 표결",
        [sig("x", "2026-10-07 19:30", "가결이 유력하다는 전망이 나온다. 통과되면 80만 명이 보장받는다.")],
    )
    e, i = heuristic_expected(t)
    assert e.startswith("가결이 유력") and "통과되면" in i
    sched = topic(
        "s", "본회의", [sig("일정", "2026-10-07 18:00", kind="schedule", expected="가결 전망입니다.")]
    )
    assert heuristic_expected(sched)[0] == "가결 전망입니다."
    plan = SlotPlan(date=DAY, slot="morning", items=[SlotItem(topic=t, order=1, role="오늘의포인트")])
    titles, thumb = heuristic_titles(plan)
    assert len(titles) == 3 and all(not {"날짜형", "감탄형"} & set(title_problems(x)) for x in titles)
    assert len(thumb) <= 10 and len(thumbnail_for("플랫폼노동자 기본법 표결")) <= 10
    assert title_problems("10월 11일 이슈 정리") == ["날짜형", "인과형 표지 없음"]
    assert "감탄형" in title_problems("충격 결과")
    a = scored(
        topic(
            "a",
            "플랫폼노동자 기본법",
            [sig("플랫폼노동자 기본법 의결", "2026-10-07 11:00")],
            keywords=["플랫폼노동자", "기본법"],
        ),
        90,
        persistence=90,
    )
    b = scored(
        topic(
            "b",
            "택배노조 파업",
            [sig("택배노조, 플랫폼노동자 기본법 처리 촉구", "2026-10-06 09:00")],
            keywords=["택배노조"],
        ),
        70,
        persistence=70,
    )
    c = scored(
        topic("c", "환율", [sig("환율 1,430원", "2026-10-07 09:00")], keywords=["환율"]), 60, persistence=60
    )
    chains = heuristic_chains([a, b, c])
    assert chains and set(chains[0].topic_ids) == {"a", "b"}


def test_risks_and_seconds():
    t = topic("r", "검찰 수사 착수", [sig("여론조사 지지율 하락", "2026-10-08 10:00")])
    kinds = {f.kind for f in topic_risks(t, "사망 사건")}
    assert {"노란딱지", "법적", "여론조사"} <= kinds
    plan = SlotPlan(
        date=DAY,
        slot="evening",
        items=[SlotItem(topic=t, order=1, role="헤드라인"), SlotItem(topic=t, order=2, role="서브")],
        titles=["사망 이유"],
    )
    assert len(plan_risks(plan)) == 3
    allocate_seconds(plan.items, "evening")
    assert plan.items[0].est_seconds > plan.items[1].est_seconds


def test_evening_hottest_body_covers_several_issues(tmp_path):
    store = Store(tmp_path / "h.db")
    s, e = window_for("evening", DAY)

    def ev(i, total, cat="국회"):
        return scored(
            topic(f"e{i}", f"사건{i}", [sig(f"사건 {i} 기사", "2026-10-08 11:00")], category=cat), total
        )

    many = [
        ev(0, 90),
        ev(1, 60, "선거"),
        ev(2, 30, "사법"),
        ev(3, 25, "외교안보"),
        ev(4, 21, "노동"),
        ev(5, 10),
    ]
    items, _n, _f = select_evening(many, DAY, s, e, None, store, body=5)
    body = [i.topic.id for i in items if i.role in ("헤드라인", "서브")]
    assert body == ["e0", "e1", "e2", "e3", "e4"]  # 헤드라인 + 이슈도 20 이상 서브 최대 4
    few = [ev(0, 90), ev(5, 10), ev(6, 5, "선거")]
    items, _n, flags = select_evening(few, DAY, s, e, None, store, body=5)
    assert [i.topic.id for i in items if i.role == "서브"] == ["e5", "e6"]  # 조용한 날도 최소 2개
