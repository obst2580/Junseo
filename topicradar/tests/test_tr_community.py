"""커뮤니티 정치 게시판: 목록·글 상세(본문·댓글·공감) 파싱, robots, 수집, 다시 읽기 규칙, 검색어 도출,
그리고 '커뮤니티 글은 주장의 근거일 뿐 사실 근거가 아니다'."""

from datetime import timedelta
from pathlib import Path

import httpx
import respx

from studio_core.config import ChannelConfig, CommunitySpec
from topicradar.collectors.community import (
    CommunityCollector,
    RobotsCache,
    apply_detail,
    decode_html,
    is_political,
    parse_board,
    parse_post,
    parse_time,
)
from topicradar.db import Store
from topicradar.models import Opinion, VideoItem, VideoPlan
from topicradar.opinion import heuristic_opinion
from topicradar.pipeline import derive_queries, needs_detail, probe_communities
from topicradar.report import buzz_cell, render_report
from topicradar.script import heuristic_script, validate_script

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
<li><a href="/free/105">국감 막말 정리</a> <em class="cnt">33</em> <b class="rec">1,204</b> <span>2026.10.06</span></li>
</ul></body></html>"""

# 그누보드형 글 상세: 본문 #bo_v_con, 댓글 article(.cmt_contents)과 옆 칸의 공감, 메뉴·인기글 상자
POST_GNU = """<html><head><title>사이트 제목</title></head><body><nav id="gnb"><a href="/">홈</a> 메뉴</nav>
<article id="bo_v"><header><h2>본회의 표결 언제죠</h2></header>
<section id="bo_v_atc"><div id="bo_v_con"><p>본회의 표결이 내일이라는데 새한당은 또 불참한다네요.</p><p>이번엔 꼭 통과돼야 합니다.</p></div>
<div id="bo_v_act"><a class="bo_v_good">추천 <strong>87</strong></a> <a class="bo_v_nogood">비추천 <strong>2</strong></a></div></section>
<section id="bo_vc"><h2>댓글목록</h2>
<article id="c_1"><header><span class="member">홍길동</span><time>10-08 09:00</time></header><div class="cmt_contents"><p>불참하면 그냥 통과입니다</p></div><div class="bo_vc_act"><span class="cmt_good">공감 30</span></div></article>
<article id="c_2"><header><span class="member">임꺽정</span></header><div class="cmt_contents"><p>표결 결과 보고 얘기합시다</p></div><div class="bo_vc_act"><span class="cmt_good">공감 5</span></div></article>
<article id="c_3"><header><span class="member">일지매</span></header><div class="cmt_contents"><p>새한당 작년 공약은 뭐였죠</p></div><div class="bo_vc_act"><span class="cmt_good">공감 41</span></div></article>
</section></article><aside class="side_best"><a href="/free/9">인기글 하나</a><span class="good">999</span></aside>
<footer class="footer">이용약관 회사소개</footer></body></html>"""

# 댓글 상자 하나 안에 li가 반복되는 사이트(class 이름에 comment가 상자에만 있음)
POST_BOX = """<html><body><div class="content02"><div class="writer"><span>추천 <em>12</em></span></div>
<div class="bodyCont"><p>감시자산 예산 삭감 누가 했는지 회의록에 다 나옵니다.</p></div></div>
<div class="commentArea"><ul class="basiclist">
<li><dl><dt><span class="author">닉1</span><span class="date">10.08 14:51</span></dt><dd class="txt">회의록 링크 부탁드려요</dd><dd><button class="good">추천 <span>3</span></button></dd></dl></li>
<li><dl><dt><span class="author">닉2</span><span class="date">10.08 14:52</span></dt><dd class="txt">국정감사에서 끝까지 물어야 합니다</dd><dd><button class="good">추천 <span>19</span></button></dd></dl></li>
</ul></div></body></html>"""


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
    spec = _spec(comment_pattern=r'class="cnt">(\d+)<', vote_pattern=r'class="rec">([\d,]+)<')
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
    assert third.votes == 1204 and first.votes is None  # 목록 추천 수(쉼표 포함), 없으면 None


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
        _spec(pages=2, politics_filter=True, detail_posts=0),
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


def test_parse_post_generic_gnuboard():
    d = parse_post(POST_GNU, _spec())
    assert d.body == "본회의 표결이 내일이라는데 새한당은 또 불참한다네요. 이번엔 꼭 통과돼야 합니다."
    assert d.votes == 87  # 비추천·인기글 상자 숫자가 아니라 글 추천
    # 공감 많은 순, 닉네임·시각은 빼고 내용만
    assert [(c.votes, c.text) for c in d.comments] == [
        (41, "새한당 작년 공약은 뭐였죠"),
        (30, "불참하면 그냥 통과입니다"),
        (5, "표결 결과 보고 얘기합시다"),
    ]


def test_parse_post_comment_box_rows_and_selectors():
    d = parse_post(POST_BOX, _spec())
    assert d.body == "감시자산 예산 삭감 누가 했는지 회의록에 다 나옵니다."
    assert d.votes == 12 and [(c.votes, c.text) for c in d.comments] == [
        (19, "국정감사에서 끝까지 물어야 합니다"),
        (3, "회의록 링크 부탁드려요"),
    ]
    sel = _spec(body_selector="div.bodyCont", comment_selector="ul.basiclist li", vote_selector="button.good")
    d2 = parse_post(POST_BOX, sel)
    assert d2.body == d.body and [c.votes for c in d2.comments] == [19, 3]
    # 본문만 있고 댓글이 없는 글(댓글을 따로 불러오는 사이트)
    d3 = parse_post(
        "<html><body><div class='rd_body'><p>짧은 글</p></div><footer>약관</footer></body></html>", _spec()
    )
    assert d3.body == "짧은 글" and d3.comments == [] and d3.votes is None


def test_decode_html_meta_charset():
    html = "<html><head><meta charset='euc-kr'></head><body>국회 본회의</body></html>".encode("euc-kr")
    resp = httpx.Response(200, content=html, headers={"content-type": "text/html"})
    assert "국회 본회의" in decode_html(resp)
    utf = httpx.Response(200, content="정치".encode(), headers={"content-type": "text/html; charset=utf-8"})
    assert decode_html(utf) == "정치"


@respx.mock
async def test_collector_reads_details_of_hot_posts(monkeypatch, tmp_path):
    respx.get(url__regex=r"/robots\.txt$").mock(return_value=httpx.Response(404))
    respx.get("https://board.example/free?page=1").mock(return_value=httpx.Response(200, text=BOARD))
    post = respx.get("https://board.example/free/102?page=1").mock(
        return_value=httpx.Response(200, text=POST_GNU)
    )
    respx.get(url__regex=r"board\.example/free/\d+$").mock(return_value=httpx.Response(200, text=POST_BOX))
    monkeypatch.setenv("STUDIO_NOW", NOW.isoformat())
    store = Store(tmp_path / "t.db")
    async with httpx.AsyncClient() as c:
        col = CommunityCollector(
            [_spec(politics_filter=True, detail_posts=1)], c, need_detail=needs_detail(store)
        )
        sigs = await col.fetch(kst("2026-10-07 00:00"))
    # 반응이 가장 큰 글(댓글 12) 하나만 상세를 읽는다
    by = {s.title: s for s in sigs}
    hot = by["법사위 플랫폼법 통과했네요"]
    assert hot.summary.startswith("감시자산") and hot.metric["votes"] == 12
    assert hot.metric["top_comments"][0] == {"t": "국정감사에서 끝까지 물어야 합니다", "v": 19}
    assert hot.metric["detail_comments"] == 12 and col.detail_counts["테스트"] == 1
    assert by["본회의 표결 언제죠"].summary == "" and not post.called
    # 다시 읽기 규칙: 처음 보는 글 / 댓글이 5개·30% 이상 늘었을 때만
    store.upsert_signals(sigs)
    check = needs_detail(store)
    assert not check(hot)
    grown = hot.model_copy(deep=True)
    grown.metric["comments"] = 17
    assert check(grown)
    grown.metric["comments"] = 15
    assert not check(grown)
    assert check(by["본회의 표결 언제죠"])
    # 저장소: 나중에 읽은 본문은 덮어쓰고, 목록만 다시 읽으면 댓글 내용은 남는다
    again = by["법사위 플랫폼법 통과했네요"].model_copy(
        update={"summary": "", "metric": {"site": "테스트", "comments": 20}}
    )
    store.upsert_signals([again])
    kept = store.get_signal(hot.id)
    assert (
        kept.summary.startswith("감시자산") and kept.metric["comments"] == 20 and kept.metric["top_comments"]
    )


def test_apply_detail_keeps_list_votes():
    s = sig("글", "2026-10-08 10:00", kind="community", site="x", comments=3, votes=50)
    apply_detail(s, parse_post(POST_GNU, _spec()), NOW)
    assert s.metric["votes"] == 50 and s.metric["comments_read"] == 3 and s.metric["detail_at"]


def _buzz(title, at, comments=0, site="다모앙", **metric):
    return sig(
        title,
        at,
        kind="community",
        url=f"https://c.example/{abs(hash((title, at)))}",
        site=site,
        comments=comments,
        **metric,
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


def test_script_and_report_never_cite_community():
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
            _buzz(
                "가결 배후에 300억 거래 있었다는 소문",
                "2026-10-08 15:00",
                500,
                top_comments=[{"t": "출처도 없는 소문 퍼뜨리지 맙시다", "v": 80}],
            ),
            _buzz("드디어 통과 축하합니다", "2026-10-08 15:10", 40, votes=200),
        ],
    )
    head.total = 80
    op = heuristic_opinion(head)
    assert op.stance == "드디어 통과 축하합니다" and op.votes == 200  # 공감이 많은 글이 다수 의견
    plan = VideoPlan(
        id="2026-10-08_1530_short",
        format="short",
        created_at=NOW,
        items=[VideoItem(topic=head, order=1, opinion=op)],
        target_chars=(810, 990),
        titles=["가결이 바꾸는 것"],
        thumbnail_text="가결 왜?",
        window_start=NOW - timedelta(hours=24),
        window_end=NOW,
    )
    text = heuristic_script(plan)
    community_url = head.buzz()[0].url
    assert community_url not in text and "https://" not in text  # 대본에는 출처·주소 줄이 없다
    assert (
        "드디어 통과 축하합니다" in text and "#확인: 커뮤니티 주장" in text
    )  # 주장은 커뮤니티에서, 다듬을 곳 표시
    warns = " | ".join(validate_script(text + f"\n300억 거래가 있었습니다.\n#출처: {community_url}\n", plan))
    assert "출처·주석 줄 1개" in warns  # 출처 줄은 대본에 남기지 않는다
    assert "수집 기사에 없는 수치" in warns and ": 300" in warns  # 커뮤니티 글 속 숫자도 기사에 없으면 경고
    assert "커뮤니티 글·댓글을 그대로 옮긴 곳" in warns
    assert (
        buzz_cell(head) == "다모앙2 (댓글 540 · 공감 200)"
        and buzz_cell(topic("x", "x", [sig("a", "2026-10-08 10:00")])) == "-"
    )
    report = render_report(plan, Path("script.txt"), [])
    assert "커뮤니티 반응" in report and "드디어 통과 축하합니다" in report and "출처도 없는 소문" in report
    assert community_url not in report
    assert Opinion(stance="x", posts=2, comments=1200, votes=0).heat_label == "글 2 · 댓글 1,200"


@respx.mock
async def test_probe_communities(monkeypatch):
    monkeypatch.delenv("STUDIO_FIXTURES", raising=False)
    respx.get("https://board.example/robots.txt").mock(return_value=httpx.Response(404))
    respx.get("https://board.example/free?page=1").mock(return_value=httpx.Response(200, text=BOARD))
    respx.get("https://board.example/free/101").mock(return_value=httpx.Response(200, text=POST_GNU))
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
    t = res["테스트"]  # 댓글 가장 많은 정치글 하나의 상세까지 읽어 본다
    assert t.detail_url == "https://board.example/free/101" and t.body_chars > 20
    assert t.comments_found == 3 and t.comment_votes == 3 and t.post_votes == 87 and not t.detail_error
    assert not res["금지"].allowed and "500" in res["오류"].error
    only = await probe_communities(cfg, "테스트")
    assert [r.name for r in only] == ["테스트"]


def test_fetch_error_hints_and_vote_cap():
    from topicradar.collectors.community import MAX_VOTES, board_hints, explain_fetch_error

    resp = httpx.Response(403, request=httpx.Request("GET", "https://damoang.net/free?page=1"))
    e = httpx.HTTPStatusError("403 Forbidden", request=resp.request, response=resp)
    assert "프로그램 접속을 막았습니다(403" in explain_fetch_error(e)
    assert explain_fetch_error(ValueError("x")) == "ValueError: x"
    from studio_core.http import HttpError

    blocked = explain_fetch_error(HttpError("403 https://theqoo.net/hot: <!DOCTYPE html>…", status=403))
    assert "보안 검사" in blocked and "DOCTYPE" not in blocked  # 수집 결과에 사이트 HTML을 늘어놓지 않는다
    menu = (
        '<a href="zboard.php?id=freeboard">자유게시판</a><a href="zboard.php?id=issue">정치/시사</a>'
        '<a href="/zboard/zboard.php?id=issue">정치/시사</a><a href="x">로그인</a>'
    )
    hints = board_hints(menu, "https://www.ppomppu.co.kr/zboard/zboard.php?id=politics")
    assert hints == [("정치/시사", "https://www.ppomppu.co.kr/zboard/zboard.php?id=issue")]
    huge = POST_GNU.replace("<strong>87</strong>", f"<strong>{MAX_VOTES * 12}</strong>")
    assert parse_post(huge, _spec()).votes is None  # 글 번호·조회수를 공감으로 잘못 읽은 것


# ---- 사이트별 설정: 제목만 집기·'?' 떼기·닉네임 빼기·댓글 글자 속 공감 수 -----------------------

NEWS_BOARD = """<ul class="board_list"><li>
<a href="/zboard/view.php?id=news_pol&no=501&type=&page=1">
  <i class="Bbox"></i><strong><img src="/hot.jpg" alt="hot"> 법사위, 기본법 통과<span class="list_comment2">47</span></strong>
  <small>요약 문장 123명이 참석했다</small>
  <span class="mini_view">가상일보 | 조회수 : 4149 | 15:48:01 <span class="goodbad"><span class="gcon up"><img src="/up.png">5</span><span class="gcon down"><img src="/down.png">0</span></span></span>
</a></li><li>
<a href="/zboard/view.php?id=news_pol&no=502&type=&page=1"><strong>댓글 없는 기사 제목입니다</strong>
  <span class="mini_view">가상일보 | 조회수 : 10 | 09:05:00</span></a></li></ul>"""


def test_parse_board_title_pattern_and_canonical_url():
    spec = _spec(
        post_pattern=r"view\.php\?id=news_pol.*?no=\d+",
        title_pattern=r'<strong>(.*?)(?:<span class="list_comment2">|</strong>)',
        comment_pattern=r'class="list_comment2">(\d+)<',
        vote_pattern=r'gcon up"><img[^>]*>(\d+)',
        canonical_url=True,
    )
    posts = parse_board(NEWS_BOARD, spec, "https://board.example/zboard/zboard.php?id=news_pol", NOW)
    a, b = posts
    # 링크 안의 요약·조회수·시각은 제목에서 빠지고, 댓글 수·공감·시각은 링크 안에서 읽는다
    assert a.title == "법사위, 기본법 통과" and a.comments == 47 and a.votes == 5
    assert (
        a.posted_at.strftime("%H:%M") == "15:48"
        and a.url == "https://board.example/zboard/view.php?id=news_pol&no=501"
    )
    assert (
        b.title == "댓글 없는 기사 제목입니다"
        and b.comments == 0
        and b.posted_at.strftime("%H:%M") == "09:05"
    )
    # canonical_url이 없으면 주소 꼬리(&type=&page=1)까지 그대로
    keep = parse_board(
        NEWS_BOARD, spec.model_copy(update={"canonical_url": False}), "https://board.example/", NOW
    )
    assert keep[0].url.endswith("no=501&type=&page=1")


POST_NICK = """<html><body><div class="post_article"><p>본문입니다. 오늘 표결이 있었습니다.</p></div>
<div class="comment_row"><div class="comment_info"><span class="nickname"><span>가상닉네임</span></span>
  <form><label>1</label><label>2</label></form><button>입력</button><div class="comment_ip">IP 1.♡.2.3</div></div>
  <div class="comment_content_symph"><button class="comment_symph"><strong>15</strong></button></div>
  <div class="comment_content"><div class="comment_view">진짜 고집 하나는 인정합니다</div></div></div>
<div class="comment_row"><div class="comment_info"><span class="nickname"><span>다른닉</span></span></div>
  <div class="comment_content_symph"><button class="comment_symph"><strong>3</strong></button></div>
  <div class="comment_content"><div class="comment_view">회의록 보면 다 나옵니다</div></div></div>
<div class="hot_comment_list"><a href="javascript:;"><b>가상닉네임</b> / 김 후보가 우리끼리에 들어가나요? <img src="/up.png"> 30 <img src="/down.png"> 0</a>
<a href="javascript:;"><b>또다른닉</b> / 같은 편 말도 좀 들어라 <img src="/up.png"> 1,204 <img src="/down.png"> 2</a></div>
</body></html>"""


def test_parse_post_strips_nicknames_and_reads_vote_text():
    rows = _spec(
        comment_selector="div.comment_row",
        comment_strip_selector=".comment_info",
        vote_selector=".comment_symph",
    )
    d = parse_post(POST_NICK, rows)
    assert [(c.votes, c.text) for c in d.comments] == [
        (15, "진짜 고집 하나는 인정합니다"),
        (3, "회의록 보면 다 나옵니다"),
    ]
    hot = _spec(
        comment_selector=".hot_comment_list a",
        comment_strip_selector="b",
        comment_vote_regex=r"(\d[\d,]*)\s+\d+\s*$",
    )
    d2 = parse_post(POST_NICK, hot)
    assert [(c.votes, c.text) for c in d2.comments] == [
        (1204, "같은 편 말도 좀 들어라"),
        (30, "김 후보가 우리끼리에 들어가나요?"),
    ]
    # 글쓴이 정보는 어디에도 남지 않는다
    assert not any("닉" in c.text for c in d.comments + d2.comments)


def test_parse_post_best_comments_deduped_and_post_votes_kept():
    html = """<html><body><div class="bodyCont"><p>본문입니다. 영화 이야기입니다.</p></div>
    <div class="btn_recom"><a>추천</a> <span id="tempPublic">944</span></div>
    <ul id="best_cmt_reply"><li class="best01"><dl><dt><span class="author">가상A</span><span class="date">26.10.05 10:59</span></dt>
      <dd>금융치료가 답</dd></dl><div class="updownbox"><dl><dd class="fl first"><a>추천 338</a></dd><dd class="fl last"><a>반대 19</a></dd></dl></div></li></ul>
    <ul id="cmt_reply"><li class="re"><dl><dt><span class="author">가상A</span></dt><dd>금융치료가 답</dd></dl>
      <div class="updownbox"><dl><dd class="fl first"><a>추천 338</a></dd></dl></div></li>
    <li class="re"><dl><dt><span class="author">가상B</span></dt><dd>별 내용도 없던데요</dd></dl>
      <div class="updownbox"><dl><dd class="fl first"><a>추천 10</a></dd></dl></div></li></ul></body></html>"""
    spec = _spec(
        comment_selector="#best_cmt_reply li, #cmt_reply li.re",
        comment_strip_selector="dt, .updownbox",
        vote_selector=".updownbox .first",
    )
    d = parse_post(html, spec)
    assert [(c.votes, c.text) for c in d.comments] == [(338, "금융치료가 답"), (10, "별 내용도 없던데요")]
    assert d.votes == 944  # 댓글용 vote_selector와 별개로 글 공감 수를 찾는다


def test_parse_board_prefers_link_matching_title_pattern():
    """같은 글로 가는 링크가 제목(strong)·요약(p) 두 개면, 더 긴 요약이 아니라 제목 정규식에 맞는 링크에서 제목을 읽는다(조갑제닷컴)."""
    html = """
    <li><a href="/board/column/view.asp?C_IDX=105751&C_CC=BC"><strong>[칼럼]'지휘관 병' 말기의 대통령</strong></a>
    <a href="/board/column/view.asp?C_IDX=105751&C_CC=BC"><p>직언하는 측근들은 하나둘 떠나가고, 주변에는 칭송하는 사람만 남아 병세를 더할 뿐</p></a>
    <p>2026-10-08&nbsp;14:13 | 회원</p></li>
    """
    spec = _spec(post_pattern=r"(?i)view\.asp\?c_idx=\d+&c_cc=\w+", title_pattern=r"<strong>(.*?)</strong>")
    (post,) = parse_board(html, spec, "https://www.chogabje.com/board/board_alllist.asp?cpage=1", NOW)
    assert "지휘관 병" in post.title and "측근" not in post.title
    assert post.url == "https://www.chogabje.com/board/column/view.asp?C_IDX=105751&C_CC=BC"
