from datetime import timedelta
from types import SimpleNamespace

from topicradar.cluster import (
    LLMCluster,
    LLMTopic,
    attach_competitors,
    categorize,
    cluster_signals,
    find_follow_up,
    find_schedule,
    heuristic_groups,
    infer_kind,
    window_title,
)
from topicradar.models import ScoreDetail
from topicradar.score import (
    Coverage,
    coverage_adjust,
    gap_score,
    heuristic_judge,
    interest_score,
    score_topics,
    total_score,
)

from trh import kst, sig, topic

A1 = sig(
    "법사위, 플랫폼노동자 기본법 의결…새한당 퇴장",
    "2026-10-07 11:20",
    "법사위가 플랫폼노동자 기본법을 의결했다.",
)
A2 = sig(
    "플랫폼노동자 기본법 법사위 통과…8일 본회의 표결",
    "2026-10-07 12:05",
    "본회의 표결은 8일 오후 2시로 잡혔다.",
)
B1 = sig(
    "국감 첫날부터 여야 대변인 막말 설전",
    "2026-10-07 09:30",
    "국정감사 첫날 여야 대변인이 막말 설전을 벌였다.",
)
C1 = sig(
    "법무부 국감, 검찰 특활비 자료 공방",
    "2026-10-07 11:50",
    "국정감사에서 검찰 특수활동비 자료 제출을 놓고 맞섰다.",
)


def test_groups_link_related_not_generic():
    groups, _ = heuristic_groups([A1, A2, B1, C1])
    titles = sorted(sorted(s.title for s in g) for g in groups)
    assert [A1.title, A2.title] in [sorted(g) for g in titles]
    # '국정감사·첫날' 같은 일반어만 겹치는 B1·C1은 묶이지 않는다
    assert all(not ({B1.title, C1.title} <= set(g)) for g in titles)


def test_cluster_merges_into_existing_topic():
    existing = topic("t1", "플랫폼노동자 기본법", [A1], keywords=["플랫폼노동자", "기본법"])
    out = cluster_signals([A2, B1], [existing], kst("2026-10-07 00:00").date(), ["국회", "정부"])
    merged = next(t for t in out if t.id == "t1")
    assert {s.id for s in merged.signals} == {A1.id, A2.id}
    assert merged.scheduled_at == kst("2026-10-08 14:00")
    # 이미 배정된 신호는 그대로 유지
    out2 = cluster_signals([A1], [existing], kst("2026-10-07 00:00").date(), ["국회"], assigned={A1.id: "t1"})
    assert [t.id for t in out2] == ["t1"]


def test_find_schedule_variants():
    assert find_schedule(sig("x", "2026-10-06 16:00", "선고가 8일 오후 2시 열린다.")) == kst(
        "2026-10-08 14:00"
    )
    assert find_schedule(sig("내일 본회의 표결 예정", "2026-10-07 19:00")) == kst("2026-10-08 00:00")
    assert find_schedule(sig("오늘 오전 10시 국무회의 개최할 예정", "2026-10-08 05:00")) == kst(
        "2026-10-08 10:00"
    )
    assert find_schedule(sig("x", "2026-10-28 10:00", "다음 일정은 3일 열린다.")) == kst("2026-11-03 00:00")
    assert find_schedule(sig("법안을 의결했다", "2026-10-07 10:00")) is None
    s = sig("본회의 일정", "2026-10-07 18:00", kind="schedule", scheduled_at="2026-10-08T14:00:00+09:00")
    assert find_schedule(s) == kst("2026-10-08 14:00")


def test_categorize_kind_follow_up():
    assert categorize(["국회 본회의 표결"]) == "국회"
    assert categorize(["환율 금리 증시"]) == "경제"
    assert categorize(["아무 말"], categories=["정부", "국회"]) == "정부"
    assert categorize(["무관"], hints=["노동"]) == "노동"
    ov = [sig("US", "2026-10-07 01:00", kind="overseas")]
    assert infer_kind(ov, None) == "overseas"
    assert infer_kind([sig("발표", "2026-10-08 01:00", kind="press")], None) == "announcement"
    assert infer_kind([A1], kst("2026-10-08 14:00")) == "scheduled"
    assert find_follow_up([A2]) is None
    assert find_follow_up(
        [sig("x", "2026-10-07 10:00", "정부는 다음 주 법안을 제출할 계획이다.")]
    ).startswith("정부는")


def test_attach_competitors_and_window_title():
    t = topic("t1", "막말 설전", [B1], keywords=["막말", "설전", "대변인"])
    comp = sig("여야 대변인 막말 설전 총정리", "2026-10-07 18:00", kind="competitor")
    other = sig("환율 이유", "2026-10-07 18:00", kind="competitor")
    attach_competitors([t], [comp, other])
    assert comp in t.signals and other not in t.signals
    assert window_title(t, kst("2026-10-07 00:00"), kst("2026-10-08 00:00")) == B1.title
    assert window_title(t, kst("2026-10-01 00:00"), kst("2026-10-02 00:00")) == "막말 설전"


def test_llm_cluster_path_and_fallback():
    class FakeLLM:
        def json(self, task, system, prompt, output, **kw):
            assert task == "cluster" and "새한당" in prompt
            return LLMCluster(
                topics=[
                    LLMTopic(
                        existing_id=None,
                        name="플랫폼법",
                        keywords=["플랫폼"],
                        category="국회",
                        signal_ids=[A1.id, "unknown"],
                        competitor_ids=[],
                        kind="event",
                        scheduled_at="2026-10-08T14:00",
                        follow_up="본회의 표결",
                    )
                ]
            )

    out = cluster_signals([A1, A2], [], kst("2026-10-07 00:00").date(), ["국회"], llm=FakeLLM())
    # A2는 응답에서 빠졌지만 휴리스틱 보충 단계에서 같은 주제로 붙는다
    assert [t.name for t in out] == ["플랫폼법"]
    t = out[0]
    assert {s.id for s in t.signals} == {A1.id, A2.id}
    assert t.scheduled_at == kst("2026-10-08 14:00") and t.follow_up == "본회의 표결"


def test_interest_and_trend_boundaries():
    r0 = interest_score(0, 0)
    assert r0.score == 30.0 and r0.trend == "→"
    up = interest_score(5, 1)
    assert up.trend == "↑" and up.rise == 3.0  # 상승률 상한 3
    down = interest_score(1, 4)
    assert down.trend == "↓"
    sat = interest_score(25, 25)
    assert sat.score == 70.0  # 기사 수 포화(100×0.4) + 상승률 0(50×0.6)
    assert interest_score(1, 1, trend_ratio=1.0).rise == 1.0


def test_gap_boundaries():
    assert gap_score(0, 40.0).score == 100.0
    assert gap_score(0, 39.9).score == 69.9
    assert gap_score(1, 90).score == 60.0
    assert gap_score(4, 90).score == 0.0
    assert "미설정" in gap_score(0, 10, has_competitors=False).reason


def test_judge_and_coverage_adjust():
    happening = topic("h", "막말 설전", [B1], category="국회")
    p, f = heuristic_judge(happening)
    assert p.score <= 30 and "단순 발언" in f.reason
    econ = topic(
        "e",
        "환율 급등",
        [sig("환율 1,430원 돌파", "2026-10-07 09:00", "외국인 매도가 겹쳤다.")],
        category="경제",
    )
    _, fe = heuristic_judge(econ)
    assert "정치 쟁점" in fe.reason
    base = ScoreDetail(score=60, reason="기본")
    t = topic("t", "플랫폼", [A1, A2])
    day = kst("2026-10-08 00:00").date()
    old = Coverage("2026-10-07_evening", kst("2026-10-07 00:00").date(), kst("2026-10-07 17:00"))
    assert coverage_adjust(t, base, [old], day).score == 35  # 새 전개 없음 −25
    early = Coverage("2026-10-07_morning", kst("2026-10-07 00:00").date(), kst("2026-10-07 05:30"))
    assert coverage_adjust(t, base, [early], day).score == 70  # 이후 기사 2건 → 시리즈 +10
    far = Coverage("2026-09-20_evening", kst("2026-09-20 00:00").date(), None)
    assert coverage_adjust(t, base, [far], day).score == 60
    assert (
        total_score(
            {"interest": ScoreDetail(score=100, reason=""), "gap": ScoreDetail(score=0, reason="")},
            {"interest": 50, "gap": 50},
        )
        == 50.0
    )


def test_score_topics_with_llm_and_competitors():
    t = topic("t", "막말 설전", [B1, sig("x", "2026-10-07 16:00", kind="competitor")])

    class FakeLLM:
        def json(self, task, system, prompt, output, **kw):
            return output(
                judgements=[
                    {
                        "topic_id": "t",
                        "persistence": 20,
                        "persistence_reason": "단발",
                        "fit": 10,
                        "fit_reason": "설전",
                    }
                ]
            )

    out = score_topics(
        [t],
        window_start=kst("2026-10-07 05:30"),
        window_end=kst("2026-10-07 17:00"),
        prev_counts={},
        target_day=kst("2026-10-07 00:00").date(),
        llm=FakeLLM(),
    )
    assert out[0].scores["fit"].reason == "설전" and out[0].competitor_hits == 1
    assert out[0].scores["gap"].score == 60.0
    assert out[0].total == round(
        (out[0].scores["interest"].score * 40 + 60 * 20 + 20 * 10 + 10 * 30) / 100, 1
    )
    _ = SimpleNamespace, timedelta


def test_issue_score_hottest_mode():
    from datetime import date

    from topicradar.score import Coverage, issue_score

    s, e = kst("2026-10-08 05:30"), kst("2026-10-08 17:00")
    hot = topic(
        "hot",
        "본회의 가결",
        [
            sig("본회의 가결", "2026-10-08 14:50", url="https://a.example/1"),
            sig("본회의서 법안 통과", "2026-10-08 15:00", url="https://b.example/2"),
            sig("가결 축하", "2026-10-08 15:10", kind="community", site="다모앙", comments=200),
            sig("드디어 통과", "2026-10-08 15:20", kind="community", site="보배드림", comments=100),
            sig("[해설] 본회의 가결", "2026-10-08 16:00", kind="competitor"),
        ],
    )
    quiet = topic("q", "세수 결손", [sig("세수 결손 전망", "2026-10-08 10:00", url="https://a.example/3")])
    d, rise = issue_score(hot, s, e, 0, 1, True)
    assert (
        "커뮤니티 글 2건(댓글 300)" in d.reason
        and "기사 2건(언론 2곳)" in d.reason
        and "다른 채널 1편" in d.reason
    )
    assert rise > 0 and "직전 대비" in d.reason
    q, _ = issue_score(quiet, s, e, 0, 0, True)
    assert d.score > q.score and q.reason.startswith("커뮤니티 글 0건")
    n, _ = issue_score(quiet, s, e, 0, 0, False)  # 커뮤니티 데이터 없으면 보도량만
    assert "커뮤니티" not in n.reason and n.score > q.score
    out = score_topics(
        [hot, quiet],
        window_start=s,
        window_end=e,
        prev_counts={},
        target_day=date(2026, 10, 8),
        coverage={
            "q": [
                Coverage(
                    plan_id="2026-10-07_evening", day=date(2026, 10, 7), window_end=kst("2026-10-07 17:00")
                )
            ]
        },
        community_mode=True,
        mode="hottest",
    )
    assert out[0].id == "hot" and set(out[0].scores) == {"issue"} and out[0].trend == "↑"
    assert out[1].scores["repeat"].score == -25 and out[1].total == max(0.0, q.score - 25)
