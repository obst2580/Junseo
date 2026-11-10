from topicradar.cluster import (
    LLMCluster,
    LLMTopic,
    attach_competitors,
    categorize,
    cluster_signals,
    find_follow_up,
    find_schedule,
    heuristic_cluster,
    heuristic_groups,
    infer_kind,
    window_title,
)
from topicradar.score import community_score, heat_stats, score_topics

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


def test_community_score_is_community_only():
    """반응도 = 커뮤니티 글·댓글·공감만(기사·경쟁 채널은 점수에 안 들어감)."""
    s, e = kst("2026-10-07 18:00"), kst("2026-10-08 18:00")
    hot = topic(
        "hot",
        "본회의 가결",
        [
            sig("본회의 가결", "2026-10-08 14:50", url="https://a.example/1"),
            sig("가결 축하", "2026-10-08 15:10", kind="community", site="다모앙", comments=200, votes=150),
            sig("드디어 통과", "2026-10-08 15:20", kind="community", site="보배드림", comments=100),
            sig("[해설] 본회의 가결", "2026-10-08 16:00", kind="competitor"),
        ],
    )
    news_only = topic("n", "세수 결손", [sig(f"세수 결손 {i}", "2026-10-08 10:00") for i in range(30)])
    d, rise = community_score(hot, s, e, prev_posts=0)
    assert d.reason.startswith("커뮤니티 글 2건 · 댓글 300 · 공감 150") and "직전 대비" in d.reason
    assert rise > 0 and d.score > 80
    n, _ = community_score(news_only, s, e, prev_posts=0)
    assert n.score == 0.0  # 기사 30건이어도 커뮤니티 반응이 없으면 0
    assert heat_stats(hot.buzz()) == (2, 300, 150)
    # 공감이 많을수록 높다(댓글이 같아도)
    liked = topic("l", "x", [sig("가결 축하", "2026-10-08 15:10", kind="community", comments=20, votes=300)])
    plain = topic("p", "y", [sig("가결 축하", "2026-10-08 15:11", kind="community", comments=20)])
    assert community_score(liked, s, e, 0)[0].score > community_score(plain, s, e, 0)[0].score
    # 글이 하나뿐이면 급상승 가점은 없다
    assert "직전 대비" not in community_score(plain, s, e, 0)[0].reason
    out = score_topics([news_only, plain, hot], window_start=s, window_end=e, prev_counts={"hot": 4})
    assert [t.id for t in out] == ["hot", "p", "n"] and set(out[0].scores) == {"heat"}
    assert out[0].trend == "↓"  # 직전 4건 → 지금 2건


def test_no_chaining_on_realistic_volume():
    """하루치 규모(사건 24개, 기사 200여 건, 여러 사건을 섞은 커뮤니티 글 40건)에서 사건이 한 주제로 뭉치지 않는다."""
    from stress_corpus import STORIES, build, purity_report

    sigs, truth = build()
    topics = heuristic_cluster(sigs, [], kst("2026-10-10 00:00").date())
    r = purity_report(topics, truth)
    assert not r["impure"], r["impure"][:3]
    assert r["max_size"] <= 14 and len(STORIES) <= r["topics"] <= len(STORIES) + 4
    # 다음 날 후속 기사는 같은 사건의 기존 주제로 붙는다
    later = [
        sig(
            "금융보안원 경고 무시한 금융사 해킹 후속 조사",
            "2026-10-11 09:00",
            "금융보안원 경고 이후 금융사 해킹 피해 조사가 이어졌다.",
        ),
        sig(
            "자포리자 공습 사망자 늘어…젤렌스키 규탄",
            "2026-10-11 09:30",
            "러시아 자포리자 활공폭탄 공습 사망자가 늘었다.",
        ),
    ]
    groups, matches = heuristic_groups(later, topics)
    by_id = {t.id: t for t in topics}
    picked = {
        g[0].title[:6]: truth[by_id[matches[i]].signals[0].id] for i, g in enumerate(groups) if i in matches
    }
    assert picked == {"금융보안원 ": "hack", "자포리자 공": "ukraine"}
