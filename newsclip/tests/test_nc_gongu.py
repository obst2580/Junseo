"""공유마당(공공누리·자유이용) 사진: 이용조건 거르기, 원본 주소, 한국어 검색어, 순위, 출처 기록."""

import httpx
import respx

from newsclip.license_log import attribution_text, write_credits
from newsclip.models import SceneSet
from newsclip.pipeline import apply_queries, query_for
from newsclip.rank import rank
from newsclip.sources import gongu
from studio_core.config import ChannelConfig

from nch import asset, scene

HEALTH = ChannelConfig.model_validate(
    {"images": {"sources": ["pexels_video", "gongu_image", "pexels_image"], "orientation": "portrait"}}
)


def _item(sn: str, title: str, alt: str, tag: str = "작가") -> str:
    return f"""<li> <div class="bg_box"> <div class="img"></div>
      <div class="licsCon photoCon"> <a href="/gongu/wrt/wrt/view.do?wrtSn={sn}&amp;menuNo=200018">
      <div class="photoCon_head"> {title} </div> </a> <p class="tag"> {tag} </p>
      <div class="photoCon_bottom"><a href="#"><img src="/static/gongu/img/common/img_x.png" alt="{alt}" class="img_cc" /></a></div>
      </div> </div> </li>"""


LIST = (
    "<ul>"
    + "".join(
        [
            _item("1", "중년 여성 걷기 운동", "공공누리 공공저작물 자유이용허락 1유형 출처표시"),
            _item("2", "다이어트 식단", "CC BY[저작권정보 표시] "),
            _item("3", "헬스장", "CC BY-NC-SA[저작권정보 표시-비영리-동일조건변경허락] "),
            _item("4", "혈당 측정", "공공누리 공공저작물 자유이용허락 4유형 출처표시,상업적 이용금지"),
            _item("5", "\t", "CC BY[저작권정보 표시] "),
            _item("6", "산책", "기증저작물 자유이용 "),
        ]
    )
    + "</ul>"
)


def _view(sn: str) -> str:
    return (
        f'<img src="/gongu/wrt/cmmn/wrtFileImageView.do?wrtSn={sn}&amp;filePath=QUJD&amp;thumbAt=Y&amp;'
        f'thumbSe=b_tbumb&amp;wrtTy=10006"> <a>키워드 저작물</a> <h3 class="section-title">키워드</h3>'
        f'<ul><li><a href="#">걷기</a></li><li><a href="#">건강</a></li></ul> 상세정보'
        f" 저작(권)자 한국건강증진개발원 (저작물 120 건)"
    )


def test_list_keeps_only_commercial_use_licenses():
    items = gongu.parse_list(LIST)
    assert [i["sn"] for i in items] == ["1", "2", "6"]  # 비영리·4유형·제목 없는 것은 뺀다
    assert items[0]["license"] == "공공누리 제1유형(출처표시)" and items[1]["license"] == "CC BY"
    assert gongu.license_name("CC BY-ND[저작권정보 표시-변경금지]") is None


def test_view_gives_original_url_and_owner():
    url, owner, keys = gongu.parse_view(_view("1"), "1")
    assert url.startswith(gongu.BASE) and "thumbSe=b_tbumb" in url and "&amp;" not in url
    assert owner == "한국건강증진개발원" and keys == "걷기 건강"
    assert gongu.parse_view("<html></html>", "1") is None


@respx.mock
async def test_search_opens_detail_pages():
    respx.get(url__startswith=gongu.LIST_URL).mock(return_value=httpx.Response(200, text=LIST))
    for sn in ("1", "2", "6"):
        respx.get(url__startswith=f"{gongu.VIEW_URL}?wrtSn={sn}&").mock(
            return_value=httpx.Response(200, text=_view(sn))
        )
    async with httpx.AsyncClient() as c:
        found = await gongu.GonguSource(c).search("걷기 운동", "broll")
    assert [a.provider_id for a in found] == ["1", "2", "6"]
    a = found[0]
    assert a.source == "gongu_image" and a.author_or_channel == "한국건강증진개발원"
    assert a.license == "공공누리 제1유형(출처표시)" and "b_tbumb" in a.download_url
    assert attribution_text(a) == "사진: 한국건강증진개발원 / 공유마당 / 공공누리 제1유형(출처표시)"


def test_korean_query_only_when_worker_wrote_ko():
    sc, other = scene("s001", 0, 3), scene("s002", 3, 6)
    apply_queries(
        [sc, other], {"scenes": {"s001": {"q": "woman walking park", "ko": "걷기 운동"}, "s002": "salad"}}
    )
    assert (
        query_for(sc, "gongu_image") == "걷기 운동" and query_for(sc, "pexels_image") == "woman walking park"
    )
    assert query_for(other, "gongu_image") == ""  # 한국어 검색어가 없으면 공유마당은 찾지 않는다


def test_rank_matches_korean_title_for_gongu():
    sc = scene("s001", 0, 3, query="woman walking park", query_ko="걷기 운동")
    hit = asset("gongu_image", "https://gongu/1.jpg", title="중년 여성 걷기 운동", width=1600, height=1200)
    miss = asset("gongu_image", "https://gongu/2.jpg", title="EBS_사람_0083", width=1600, height=1200)
    ranked = rank(sc, [miss, hit], HEALTH)
    assert ranked[0].url == hit.url
    assert any("제목 일치" in n for n in ranked[0].score_notes)


def test_credits_list_gongu_with_license(tmp_path):
    sc = scene("s001", 0, 3)
    sc.candidates = [
        asset(
            "gongu_image",
            "https://gongu/1.jpg",
            provider_id="1",
            author_or_channel="한국건강증진개발원",
            license="공공누리 제1유형(출처표시)",
            source_page_url="https://gongu.copyright.or.kr/gongu/wrt/wrt/view.do?wrtSn=1",
            local_path=str(tmp_path / "a.jpg"),
        )
    ]
    assert write_credits(tmp_path / "credits.txt", SceneSet(project="p", scenes=[sc])) == 1
    text = (tmp_path / "credits.txt").read_text(encoding="utf-8")
    assert "공유마당 사진 #1" in text and "한국건강증진개발원" in text and "공공누리 제1유형" in text


@respx.mock
async def test_two_words_with_no_hit_retry_first_word():
    route = respx.get(url__startswith=gongu.LIST_URL).mock(
        side_effect=[httpx.Response(200, text="<ul></ul>"), httpx.Response(200, text=LIST)]
    )
    for sn in ("1", "2", "6"):
        respx.get(url__startswith=f"{gongu.VIEW_URL}?wrtSn={sn}&").mock(
            return_value=httpx.Response(200, text=_view(sn))
        )
    async with httpx.AsyncClient() as c:
        found = await gongu.GonguSource(c).search("걷기 체중", "broll")
    assert len(route.calls) == 2 and "%EA%B1%B7%EA%B8%B0" in str(route.calls[1].request.url)
    assert found[0].title == "중년 여성 걷기 운동 걷기 건강"  # 상세 키워드를 제목 뒤에


def test_rank_pushes_down_old_and_overseas_gongu_photos():
    sc = scene("s001", 0, 3, query="pharmacy", query_ko="약국")
    old = asset(
        "gongu_image", "https://gongu/1.jpg", title="1971년_종로 약국 옛날사진", width=1600, height=1200
    )
    abroad = asset(
        "gongu_image", "https://gongu/2.jpg", title="해외_유럽_스페인_0034 상점 약국", width=1600, height=1200
    )
    icon = asset(
        "gongu_image", "https://gongu/4.jpg", title="아이콘_라인2_의학_10 약국", width=1600, height=1200
    )
    now = asset("gongu_image", "https://gongu/3.jpg", title="동네 약국 약사", width=1600, height=1200)
    ranked = rank(sc, [old, abroad, icon, now], HEALTH)
    assert ranked[0].url == now.url
    assert all(any("옛날·해외" in n or "그림" in n for n in a.score_notes) for a in ranked[1:])
