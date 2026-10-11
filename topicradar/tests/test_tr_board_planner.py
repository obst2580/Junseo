"""주제판(100% 커뮤니티 반응) · 커뮤니티 의견 · 10분 묶음 추천 · 영상 계획 · 제목/고리 · 리스크."""

from datetime import timedelta

import pytest

from studio_core.config import ChannelConfig
from studio_core.llm import LLMError
from topicradar.board import BoardContext, allocate_seconds, build_board, heuristic_bundles, is_hot, make_plan
from topicradar.db import Store
from topicradar.models import VideoItem, VideoPlan
from topicradar.opinion import LLMOpinions, heuristic_opinion, post_weight
from topicradar.planner import heuristic_link, heuristic_titles, link_sentence, thumbnail_for
from topicradar.risk import plan_risks, title_problems, topic_risks

from trh import kst, sig, topic

NOW = kst("2026-10-08 18:00")
CFG = ChannelConfig.model_validate({"clustering": {"generic_tokens": ["새한당", "한빛당"]}})


def _c(title, at, comments=0, votes=None, site="다모앙", replies=()):
    metric = {"site": site, "comments": comments}
    if votes is not None:
        metric["votes"] = votes
    if replies:
        metric["top_comments"] = [{"t": t, "v": v} for t, v in replies]
    return sig(title, at, kind="community", url=f"https://c.example/{abs(hash((title, at)))}", **metric)


def _news(title, at, summary=""):
    return sig(title, at, summary or f"{title}했다.", url=f"https://n.example/{abs(hash((title, at)))}")


def _signals():
    return [
        _news(
            "플랫폼노동자 기본법 본회의 가결",
            "2026-10-08 14:50",
            "국회는 8일 본회의에서 플랫폼노동자 기본법을 가결했다.",
        ),
        _news("플랫폼노동자 기본법 본회의 통과…새한당 불참", "2026-10-08 15:30"),
        _c(
            "플랫폼노동자 기본법 본회의 통과 축하합니다",
            "2026-10-08 15:00",
            88,
            140,
            replies=[("끝까지 반대한 새한당 기억합시다", 63)],
        ),
        _c(
            "플랫폼노동자 기본법 통과됐다 최소보수",
            "2026-10-08 15:20",
            57,
            site="더쿠",
            replies=[("라이더분들 다행이다", 34)],
        ),
        _news("새한당 기본법 헌법소원 검토", "2026-10-08 16:40"),
        _c("새한당 기본법 헌법소원 검토 졸속이라니", "2026-10-08 16:30", 49, 57, site="보배드림"),
        _c("새한당 기본법 헌법소원 사유가 뭔가요", "2026-10-08 16:50", 12, 5, site="뽐뿌"),
        _news("환율 1,430원 돌파", "2026-10-08 10:00"),
        _news("환율 1,430원 넘어 외환당국 경계", "2026-10-08 11:00"),
        _c("점심 메뉴 추천 국회 식당", "2026-10-08 12:00", 1),
        _c("예전 글 국정감사 막말", "2026-10-06 12:00", 99),  # 판 구간(24시간) 밖
    ]


def _store(tmp_path):
    store = Store(tmp_path / "b.db")
    store.upsert_signals(_signals())
    return store


def test_build_board_is_community_first(tmp_path):
    store = _store(tmp_path)
    board = build_board(BoardContext(store=store, cfg=CFG), NOW)
    names = [t.name for t in board.topics]
    assert names and all(t.opinion.posts >= 1 for t in board.topics)
    assert not any("환율" in n for n in names)  # 기사가 많아도 커뮤니티 반응이 없으면 판에 없다
    assert not any("막말" in n for n in names)  # 구간 밖
    assert "점심 메뉴" not in " ".join(names)  # 글 1개·반응 작음
    top = board.topics[0]
    assert "기본법" in top.name and top.articles >= 2 and top.opinion.posts == 2
    assert top.opinion.stance == "플랫폼노동자 기본법 본회의 통과 축하합니다"  # 공감 많은 글
    assert any(s.kind == "comment" and s.text.startswith("끝까지 반대한") for s in top.opinion.samples)
    assert any("반응이 작은 화제" in n for n in board.notes) and any(
        "커뮤니티 반응이 없는 주제" in n for n in board.notes
    )
    assert store.latest_board().id == board.id == "2026-10-08_180000"
    assert board.llm_mode == "offline"


def test_board_with_llm_opinions_and_bundles(tmp_path):
    store = _store(tmp_path)
    seen = {}

    class LLM:
        def json(self, task, system, prompt, output, **kw):
            if task == "cluster":
                raise LLMError("군집은 휴리스틱으로")
            assert task == "opinion" and output is LLMOpinions
            seen["prompt"] = prompt
            ids = [t["topic_id"] for t in __import__("json").loads(prompt.split("\n", 1)[1])]
            return LLMOpinions(
                opinions=[
                    {
                        "topic_id": ids[0],
                        "stance": "기본법 통과는 시작일 뿐이다",
                        "points": ["시행령 감시"],
                        "minority": None,
                        "questions": [],
                    },
                    {
                        "topic_id": "unknown",
                        "stance": "무시",
                        "points": [],
                        "minority": None,
                        "questions": [],
                    },
                ],
                bundles=[
                    {"topic_ids": ids[:2], "link": "통과와 헌법소원은 한 이야기입니다."},
                    {"topic_ids": ids[:1], "link": "하나뿐"},
                ],
            )

    board = build_board(BoardContext(store=store, cfg=CFG, llm=LLM()), NOW)
    assert (
        board.llm_mode == "claude" and '"votes": 140' in seen["prompt"] and '"top_comments"' in seen["prompt"]
    )
    top = board.topics[0]
    assert top.opinion.stance == "기본법 통과는 시작일 뿐이다" and top.opinion.by == "claude"
    assert top.opinion.votes == 140 and top.opinion.samples  # 숫자·대표 글은 실제 데이터 그대로
    assert board.topics[1].opinion.by == "heuristic"  # Claude가 빠뜨린 주제
    assert [b.link for b in board.bundles] == ["통과와 헌법소원은 한 이야기입니다."]  # 1개짜리 묶음은 버림


def test_heuristic_bundles_need_distinct_shared_words():
    a = topic(
        "a",
        "플랫폼노동자 기본법 본회의 통과",
        [_c("기본법 통과", "2026-10-08 15:00", 50)],
        keywords=["플랫폼노동자", "기본법", "본회의"],
    )
    b = topic(
        "b",
        "새한당 기본법 헌법소원",
        [_c("헌법소원", "2026-10-08 16:00", 40)],
        keywords=["기본법", "헌법소원", "플랫폼노동자"],
    )
    c = topic(
        "c",
        "새한당 지지율 하락",
        [_c("지지율", "2026-10-08 11:00", 30)],
        keywords=["새한당", "지지율", "하락"],
    )
    bundles = heuristic_bundles([a, b, c], {"새한당"})
    assert [x.topic_ids for x in bundles] == [["a", "b"]]  # 정당명만 겹치는 c는 묶지 않는다
    assert bundles[0].link.endswith("이야기입니다.") and "기본법" in bundles[0].link


def test_opinion_weights_votes_over_comments():
    t = topic(
        "t",
        "x",
        [
            _c("논란 글", "2026-10-08 10:00", comments=300),
            _c(
                "동의 많은 글",
                "2026-10-08 11:00",
                comments=20,
                votes=200,
                replies=[("맞는 말", 10), ("아니죠", 2)],
            ),
            _c("정말 이래도 되나요?", "2026-10-08 12:00", comments=5),
        ],
    )
    assert post_weight(t.signals[1]) > post_weight(t.signals[0])
    op = heuristic_opinion(t)
    assert (
        op.stance == "동의 많은 글" and "맞는 말" in op.points and "아니죠" not in op.points
    )  # 공감 3 미만 댓글은 근거로 안 씀
    assert op.questions == ["정말 이래도 되나요?"] and (op.posts, op.comments, op.votes) == (3, 325, 200)
    assert heuristic_opinion(topic("n", "기사만", [_news("기사", "2026-10-08 10:00")])).posts == 0


def test_is_hot():
    s, e = NOW - timedelta(hours=24), NOW
    one = topic("o", "x", [_c("하나", "2026-10-08 10:00", 3)])
    one.total = 20
    assert not is_hot(one, s, e, 2)
    one.total = 60
    assert is_hot(one, s, e, 2)  # 글 하나라도 반응도가 크면
    two = topic("t", "y", [_c("a", "2026-10-08 10:00"), _c("b", "2026-10-08 11:00")])
    two.total = 5
    assert is_hot(two, s, e, 2) and not is_hot(topic("n", "z", [_news("기사", "2026-10-08 10:00")]), s, e, 1)


def test_make_plan_long_and_short(tmp_path):
    store = _store(tmp_path)
    ctx = BoardContext(store=store, cfg=CFG)
    board = build_board(ctx, NOW)
    ids = [t.topic_id for t in board.topics[:2]]
    plan = make_plan(ctx, board, ids, "long", "2026-10-08_1805_long", NOW)
    assert [i.topic.id for i in plan.items] == ids and plan.target_chars == (2700, 3300)
    assert plan.items[0].est_seconds > plan.items[1].est_seconds and plan.link  # 고리 문장
    assert plan.items[0].opinion.stance == board.topics[0].opinion.stance
    assert len(plan.titles) == 3 and plan.thumbnail_text and len(plan.thumbnail_text) <= 10
    assert plan.window_start == board.window_start and plan.board_id == board.id
    short = make_plan(ctx, board, ids[:1], "short", "2026-10-08_1806_short", NOW)
    assert short.link is None and short.target_chars == (810, 990) and short.items[0].est_seconds == 180
    with pytest.raises(ValueError, match="하나만"):
        make_plan(ctx, board, ids, "short", "x", NOW)
    with pytest.raises(ValueError, match="long"):
        make_plan(ctx, board, ids, "weekly", "x", NOW)


def test_titles_link_and_thumbnail():
    a = topic("a", "플랫폼노동자 기본법 본회의 통과", [_c("기본법 통과 축하", "2026-10-08 15:00", 50, 100)])
    b = topic("b", "새한당 헌법소원 검토", [_c("헌법소원 졸속", "2026-10-08 16:00", 40)])
    plan = VideoPlan(
        id="x_long",
        format="long",
        created_at=NOW,
        items=[
            VideoItem(topic=a, order=1, opinion=heuristic_opinion(a)),
            VideoItem(topic=b, order=2, opinion=heuristic_opinion(b)),
        ],
    )
    titles, thumb = heuristic_titles(plan)
    assert titles[0].startswith("기본법 통과 축하") and len(titles) == 3 and len(thumb) <= 10
    assert all(not title_problems(x) for x in titles)
    assert len(thumbnail_for("플랫폼노동자 기본법 표결")) <= 10
    assert link_sentence(["A", "B"], ["예산"]) == "A 그리고 B, 모두 예산으로 이어지는 이야기입니다."
    assert link_sentence(["A", "B", "C"], ["국회의"]) == "A, B 그리고 C, 모두 국회의로 이어지는 이야기입니다."
    assert link_sentence(["A", "B"], ["감시"]).endswith("감시로 이어지는 이야기입니다.")
    assert "지금 커뮤니티가 가장 뜨겁게" in link_sentence(["A", "B"], [])
    assert "모두 새한당" not in heuristic_link([a, b], {"새한당"})  # 일반어(정당명)는 고리 말로 쓰지 않는다
    assert title_problems("10월 11일 이슈 정리") == ["날짜형"] and "감탄형" in title_problems("충격 결과")
    assert not title_problems("기본법 통과에 새한당 발칵")  # 사건을 말하는 동사는 허용


def test_risks_and_seconds():
    t = topic("r", "검찰 수사 착수", [sig("여론조사 지지율 하락", "2026-10-08 10:00")])
    kinds = {f.kind for f in topic_risks(t, "사망 사건")}
    assert {"노란딱지", "법적", "여론조사"} <= kinds
    items = [VideoItem(topic=t, order=1), VideoItem(topic=t, order=2), VideoItem(topic=t, order=3)]
    plan = VideoPlan(id="x_long", format="long", created_at=NOW, items=items, titles=["사망 이유"])
    assert len(plan_risks(plan)) == 3
    allocate_seconds(plan.items, 10)
    secs = [i.est_seconds for i in plan.items]
    assert secs[0] > secs[1] == secs[2] and abs(sum(secs) - 600) <= 10
