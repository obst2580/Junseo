"""커뮤니티 정치 게시판: 파싱·robots·수집·검색어 도출·관심도·편성 관문·사실 근거 배제."""

from pathlib import Path

import httpx
import respx

from studio_core.config import ChannelConfig, CommunitySpec
from topicradar.collectors.community import (
    CommunityCollector,
    RobotsCache,
    is_political,
    parse_board,
    parse_time,
)
from topicradar.db import Store
from topicradar.models import ScoreDetail, SlotItem, SlotPlan
from topicradar.pipeline import derive_queries, probe_communities
from topicradar.report import buzz_cell, render_report
from topicradar.score import score_topics
from topicradar.script import heuristic_script, validate_script
from topicradar.slots import select_evening, select_morning, window_for

from trh import kst, sig, topic

NOW = kst("2026-10-08 15:00")
DAY = NOW.date()

BOARD = """<html><body><ul>
<li><a href="/free/1">[공지] 게시판 이용 규칙</a> <span>10.01</span></li>
<li><a href="/free/101">법사위 플랫폼법 통과했네요 [12]</a> <span>14:20</span></li>
<li><a href="/free/102?page=1#c">본회의 표결 언제죠</a><a href="/free/102#comment">7</a> <span>5분 전</span></li>
<li><a href="https://other.example/x">외부 링크</a></li>
<li><a href="/free/103">점심 뭐 먹지</a> <span>어제 12:10</span></li>
<li><a href="/free/104">ㅋㅋ</a></li>
<li><a href="/free/105">국감 막말 정리</a> <em class="cnt">33</em> <span>2026.10.06</span></li>
</ul></body></html>"""


def _spec(**kw):
    base = {
        "name": "테스트",
        "list_url": "https://board.example/free?page={page}",
        "post_pattern": r"board\.example/free/\d+",
    }
    return CommunitySpec(**{**base, **kw})


def test_parse_time_formats():
    assert parse_time("3분 전", NOW) == kst("2026-10-08 14:57")
    assert parse_time("2시간 전", NOW) == kst("2026-10-08 13:00")
    assert parse_time("어제 21:05", NOW) == kst("2026-10-07 21:05")
    assert parse_time("어제", NOW) == kst("2026-10-07 12:00")
    assert parse_time("2026.10.06 09:30", NOW) == kst("2026-10-06 09:30")
    assert parse_time("26/10/05 08:00:11", NOW) == kst("2026-10-05 08:00")
    assert parse_time("2026-10-04", NOW) == kst("2026-10-04 12:00")  # 날짜만 있으면 정오
    assert parse_time("10.07 22:10", NOW) == kst("2026-10-07 22:10")
    assert parse_time("12.30", NOW) == kst("2025-12-30 12:00")  # 미래 월일은 작년
    assert parse_time("14:20", NOW) == kst("2026-10-08 14:20")
    assert parse_time("23:50", NOW) == kst("2026-10-07 23:50")  # 지금보다 늦은 시각은 어제
    assert parse_time("2026.13.40", NOW) is None and parse_time("13.40", NOW) is None
    assert parse_time("조회 120", NOW) is None


def test_page_url_placeholders():
    assert _spec().page_url(2) == "https://board.example/free?page=2"
    assert _spec(list_url="https://c.example/park?po={page0}").page_url(1) == "https://c.example/park?po=0"


def test_is_political():
    assert is_political("법사위 플랫폼법 통과") and is_political("본회의 표결 언제죠")
    assert not is_political("플랫폼법 통과했네요")  # 기관·절차·정당 어휘가 없으면 정치글로 보지 않는다
    assert not is_political("점심 뭐 먹지")
    assert is_political("한빛당 또 이러네", extra=["한빛당"])


def test_parse_board_generic_links():
    spec = _spec(comment_pattern=r'class="cnt">(\d+)<')
    posts = parse_board(BOARD, spec, "https://board.example/free?page=1", NOW)
    by_title = {p.title: p for p in posts}
    assert "[공지] 게시판 이용 규칙" not in by_title and "게시판 이용 규칙" not in by_title
    assert "외부 링크" not in by_title and "ㅋㅋ" not in by_title  # 패턴 불일치·너무 짧은 제목
    first = by_title["법사위 플랫폼법 통과했네요"]
    assert first.comments == 12 and first.posted_at == kst("2026-10-08 14:20")
    assert first.url == "https://board.example/free/101"
    # 제목 링크와 댓글 링크가 따로 있어도 한 글로 묶고, '#…'은 떼어 낸다
    second = by_title["본회의 표결 언제죠"]
    assert second.url == "https://board.example/free/102?page=1" and second.posted_at == kst(
        "2026-10-08 14:55"
    )
    assert second.comments == 7  # 같은 글로 가는 숫자 링크 = 댓글 수
    assert by_title["점심 뭐 먹지"].posted_at == kst("2026-10-07 12:10")
    third = by_title["국감 막말 정리"]
    assert third.comments == 33 and third.posted_at == kst("2026-10-06 12:00")


def test_parse_board_without_comment_pattern_reads_brackets():
    spec = _spec()
    posts = parse_board(BOARD, spec, "https://board.example/free?page=1", NOW)
    assert {p.title: p.comments for p in posts}["국감 막말 정리"] == 0
    assert len(posts) == 4


@respx.mock
async def test_robots_cache():
    respx.get("https://a.example/robots.txt").mock(
        return_value=httpx.Response(200, text="User-agent: *\nDisallow: /free\n")
    )
    respx.get("https://b.example/robots.txt").mock(return_value=httpx.Response(404))
    respx.get("https://c.example/robots.txt").mock(side_effect=httpx.ConnectError("x"))
    async with httpx.AsyncClient() as c:
        rc = RobotsCache(c)
        assert not await rc.allowed("https://a.example/free?page=1")
        assert await rc.allowed("https://a.example/hot")
        assert await rc.allowed("https://b.example/free") and await rc.allowed("https://c.example/free")
    assert respx.calls.call_count == 3  # 호스트마다 한 번만 읽는다


@respx.mock
async def test_community_collector_filters_and_isolates(monkeypatch):
    respx.get("https://board.example/free?page=1").mock(return_value=httpx.Response(200, text=BOARD))
    respx.get("https://board.example/free?page=2").mock(
        return_value=httpx.Response(200, text='<a href="/free/201">대통령실 브리핑 요약</a> 13:00')
    )
    respx.get("https://down.example/list").mock(return_value=httpx.Response(403))
    respx.get("https://moved.example/list").mock(
        return_value=httpx.Response(200, text="<p>게시판이 이동했습니다</p>")
    )
    respx.get("https://deny.example/robots.txt").mock(
        return_value=httpx.Response(200, text="User-agent: *\nDisallow: /\n")
    )
    respx.get(url__regex=r"/robots\.txt$").mock(return_value=httpx.Response(404))  # 나머지는 robots.txt 없음
    specs = [
        _spec(pages=2, politics_filter=True),
        CommunitySpec(name="막힘", list_url="https://down.example/list", post_pattern=r"/v/\d+"),
        CommunitySpec(name="금지", list_url="https://deny.example/list", post_pattern=r"/v/\d+"),
        CommunitySpec(name="이동", list_url="https://moved.example/list", post_pattern=r"/v/\d+"),
        CommunitySpec(name="끔", list_url="https://off.example/list", post_pattern=r"/v/\d+", enabled=False),
    ]
    monkeypatch.setenv("STUDIO_NOW", NOW.isoformat())
    async with httpx.AsyncClient() as c:
        col = CommunityCollector(specs, c)
        sigs = await col.fetch(kst("2026-10-07 00:00"))
    titles = {s.title for s in sigs}
    # 정치 필터: 잡담 제외 / since 이전(10-06) 제외 / 2쪽까지 읽음
    assert titles == {"법사위 플랫폼법 통과했네요", "본회의 표결 언제죠", "대통령실 브리핑 요약"}
    s0 = next(s for s in sigs if s.title == "본회의 표결 언제죠")
    assert s0.kind == "community" and s0.source == "community:테스트" and s0.summary == ""
    assert s0.metric == {"site": "테스트", "comments": 7}
    assert "막힘" in col.site_errors and "robots" in col.site_errors["금지"]
    assert "post_pattern" in col.site_errors["이동"]
    assert col.site_counts["테스트"] == 3 and "끔" not in col.site_counts


def _buzz(title, at, comments=0, site="다모앙"):
    return sig(
        title,
        at,
        kind="community",
        url=f"https://c.example/{abs(hash((title, at)))}",
        site=site,
        comments=comments,
    )


def test_derive_queries():
    community = [
        _buzz("플랫폼노동자 기본법 법사위 통과", "2026-10-07 11:00", 80),
        _buzz("플랫폼노동자 기본법 다음은 본회의", "2026-10-07 12:00", 10),
        _buzz("기본법 통과되면 플랫폼노동자 뭐가 달라지나요", "2026-10-07 12:30"),
        _buzz("정치자금 재판 선고 내일", "2026-10-07 13:00", 5),
        _buzz("정치자금 재판 유죄 나올까", "2026-10-07 13:10"),
        _buzz("한빛당 한빛당 한빛당", "2026-10-07 13:20"),
        _buzz("오늘 날씨 좋네요", "2026-10-07 13:30"),
    ]
    q = derive_queries(community, 5, {"한빛당"})
    assert q[0] == "플랫폼노동자 기본법"  # 댓글 가중
    assert "정치자금 재판" in q and not any("한빛당" in x for x in q)
    assert len(q) == len({frozenset(x.split()) for x in q})  # 순서만 다른 중복 없음
    assert not any(x.endswith(("요", "까", "네요")) for x in " ".join(q).split())
    assert derive_queries(community, 5, set(), exclude={"플랫폼노동자 기본법"})[0] == "정치자금 재판"
    assert derive_queries(community, 0, set()) == [] and derive_queries([], 3, set()) == []


def test_score_interest_from_community():
    s, e = window_for("evening", DAY)
    hot = topic(
        "hot",
        "본회의 표결",
        [
            sig("본회의 가결", "2026-10-08 14:50", "국회는 8일 본회의에서 법안을 가결했다."),
            _buzz("본회의 가결 축하", "2026-10-08 15:00", 120),
            _buzz("드디어 통과", "2026-10-08 15:10", 60, site="보배드림"),
        ],
    )
    cold = topic(
        "cold",
        "환율",
        [sig("환율 상승", "2026-10-08 10:00"), sig("환율 또 상승", "2026-10-08 11:00")],
        category="경제",
    )
    score_topics(
        [hot, cold],
        window_start=s,
        window_end=e,
        prev_counts={"hot": 0},
        target_day=DAY,
        has_competitors=False,
        community_mode=True,
    )
    assert hot.scores["interest"].score > cold.scores["interest"].score
    assert "커뮤니티 글 2건(댓글 180)" in hot.scores["interest"].reason
    assert (
        "기사 2건" in cold.scores["interest"].reason and "커뮤니티 글 0건" in cold.scores["interest"].reason
    )
    assert "진보 커뮤니티" not in hot.scores["fit"].reason  # 글 3건 미만은 가점 없음


def _scored(t, total):
    t.total = total
    t.scores = {"persistence": ScoreDetail(score=60, reason="")}
    return t


def test_evening_gate_and_fallback(tmp_path):
    store = Store(tmp_path / "t.db")
    s, e = window_for("evening", DAY)
    loud = _scored(
        topic(
            "loud",
            "국감 막말",
            [sig("국감 막말 설전", "2026-10-08 10:00"), _buzz("막말 정리", "2026-10-08 09:00", 40)],
        ),
        60,
    )
    big = _scored(topic("big", "국방위 국감", [sig("국방위 국감 질타", "2026-10-08 11:00")]), 90)
    late = _scored(
        topic(
            "late",
            "재판",
            [sig("재판 시작", "2026-10-08 12:00"), _buzz("재판 어제 얘기", "2026-10-07 20:00", 3)],
        ),
        50,
    )
    rumor = _scored(topic("rumor", "대표 사퇴설", [_buzz("대표 사퇴설 돈다", "2026-10-08 10:00", 200)]), 95)
    items, notes, flags = select_evening([loud, big, late, rumor], DAY, s, e, None, store, gate=True)
    body = [i for i in items if i.role in ("헤드라인", "서브")]
    assert [i.topic.id for i in body] == ["loud", "late"]  # 12시간 전 반응까지 인정, 뉴스 없는 소문은 제외
    assert any("커뮤니티 반응 없는 주제 1건" in n for n in notes)
    # 반응 있는 주제가 하나도 없으면 뉴스 기준으로 되돌리고 표시
    items2, _n, flags2 = select_evening([big], DAY, s, e, None, store, gate=True)
    assert items2[0].topic.id == "big" and any("뉴스 기준으로 편성" in f.message for f in flags2)
    assert any("서브 0개뿐" in f.message for f in flags2)


def test_morning_skips_community_only_topics():
    s, e = window_for("morning", DAY)
    rumor = topic("r", "사퇴설", [_buzz("대표 사퇴설", "2026-10-08 01:00", 90)])
    rumor.total = 99
    real = topic(
        "n", "본회의", [sig("8일 본회의 표결", "2026-10-07 22:00")], scheduled_at=kst("2026-10-08 14:00")
    )
    real.total = 50
    items, _notes, _flags = select_morning([rumor, real], DAY, s, e, set(), prefer_buzz=True)
    assert [i.topic.id for i in items] == ["n"]


def test_script_and_report_never_cite_community():
    s, e = window_for("evening", DAY)
    head = topic(
        "h",
        "본회의 표결",
        [
            sig(
                "플랫폼노동자 기본법 본회의 가결",
                "2026-10-08 14:50",
                "국회는 8일 본회의를 열고 플랫폼노동자 기본법을 가결했다. 새한당은 표결에 불참했다. 법은 공포 후 6개월 뒤 시행된다.",
                url="https://n.example/p1",
            ),
            _buzz("가결 배후에 300억 거래 있었다는 소문", "2026-10-08 15:00", 500),
        ],
    )
    head.total = 80
    head.scores = {k: ScoreDetail(score=80, reason="r") for k in ("interest", "gap", "persistence", "fit")}
    plan = SlotPlan(
        date=DAY,
        slot="evening",
        items=[SlotItem(topic=head, order=1, role="헤드라인")],
        titles=["가결이 바꾸는 것"],
        thumbnail_text="가결 왜?",
        window_start=s,
        window_end=e,
    )
    text = heuristic_script(plan)
    community_url = head.buzz()[0].url
    assert community_url not in text and "300억" not in text and "소문" not in text
    assert "https://n.example/p1" in text
    warns = " | ".join(validate_script(text + f"\n#출처: {community_url}\n", plan))
    assert "수집되지 않은 출처 URL 1개" in warns  # 커뮤니티 주소는 근거로 인정하지 않는다
    assert (
        buzz_cell(head) == "다모앙1 (댓글 500)"
        and buzz_cell(topic("x", "x", [sig("a", "2026-10-08 10:00")])) == "-"
    )
    report = render_report(plan, Path("script.txt"), [])
    assert "커뮤니티 반응" in report and "다모앙1 (댓글 500)" in report
    assert community_url not in report


@respx.mock
async def test_probe_communities(monkeypatch):
    monkeypatch.delenv("STUDIO_FIXTURES", raising=False)
    respx.get("https://board.example/robots.txt").mock(return_value=httpx.Response(404))
    respx.get("https://board.example/free?page=1").mock(return_value=httpx.Response(200, text=BOARD))
    respx.get("https://deny.example/robots.txt").mock(
        return_value=httpx.Response(200, text="User-agent: *\nDisallow: /\n")
    )
    respx.get("https://err.example/robots.txt").mock(return_value=httpx.Response(404))
    respx.get("https://err.example/list").mock(return_value=httpx.Response(500))
    cfg = ChannelConfig(
        communities=[
            _spec(politics_filter=True),
            CommunitySpec(name="금지", list_url="https://deny.example/list", post_pattern=r"/v/\d+"),
            CommunitySpec(name="오류", list_url="https://err.example/list", post_pattern=r"/v/\d+"),
        ]
    )
    res = {r.name: r for r in await probe_communities(cfg)}
    assert len(res["테스트"].posts) == 4 and res["테스트"].kept == 3
    assert not res["금지"].allowed and "500" in res["오류"].error
    only = await probe_communities(cfg, "테스트")
    assert [r.name for r in only] == ["테스트"]
