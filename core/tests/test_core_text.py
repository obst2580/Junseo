from studio_core.text import (
    count_chars,
    extract_keywords,
    has_batchim,
    idf_table,
    jaccard,
    josa,
    normalize_title,
    numbers_in,
    quotes_in,
    short_name,
    split_sentences,
    strip_html,
    to_polite,
    tokenize,
    wrap_subtitle,
)


def test_strip_html_and_title():
    assert strip_html("<b>국회</b>&amp;정부") == "국회 &정부"
    assert normalize_title("[속보] 법안 가결") == "법안 가결"
    assert strip_html(None) == ""


def test_tokenize_strips_particles_and_numbers():
    toks = tokenize("국회는 8일 본회의를 열고 법안을 처리했다 현지시간 1,430원")
    assert "국회" in toks and "본회의" in toks and "법안" in toks
    assert "8일" not in toks and "현지시간" not in toks
    # '회의'로 끝나는 말은 '의'를 조사로 떼지 않는다
    assert "국무회의" in tokenize("국무회의에서")
    assert "tf" in tokenize("TF가 출범")


def test_keywords_and_similarity():
    assert extract_keywords("플랫폼노동자 기본법 플랫폼노동자 국회", k=1) == ["플랫폼노동자"]
    assert jaccard({"a", "b"}, {"b", "c"}) == 1 / 3
    assert jaccard(set(), {"a"}) == 0.0
    idf = idf_table([{"a"}, {"a", "b"}])
    assert idf["b"] > idf["a"]


def test_to_polite_endings():
    cases = {
        "국회는 본회의를 연다.": "국회는 본회의를 엽니다.",
        "법안이 가결됐다.": "법안이 가결됐습니다.",
        "가결이 유력하다.": "가결이 유력합니다.",
        "이번 주가 고비다.": "이번 주가 고비입니다.",
        "사실이 아니다.": "사실이 아닙니다.",
        "의자에 앉는다.": "의자에 앉습니다.",
        '"반대한다"고 말했다.': '"반대한다"고 말했습니다.',
        "이미 했습니다.": "이미 했습니다.",
        "질문인가?": "질문인가?",
    }
    for src, want in cases.items():
        assert to_polite(src) == want


def test_wrap_subtitle_limit():
    s = "새한당은 플랫폼노동자 기본법이 기업 부담을 키운다며 반대 입장을 밝혔습니다."
    lines = wrap_subtitle(s, 25)
    assert all(len(x) <= 25 for x in lines)
    assert "".join(lines).replace(" ", "") == s.replace(" ", "")
    assert wrap_subtitle("짧은 문장", 25) == ["짧은 문장"]
    assert wrap_subtitle("", 25) == []
    long_word = "가" * 60
    assert all(len(x) <= 25 for x in wrap_subtitle(long_word, 25))


def test_josa_and_short_name():
    assert josa("본회의", "이/가") == "본회의가"
    assert josa("법안", "을/를") == "법안을"
    assert josa("서울", "으로/로") == "서울로"
    assert josa("법", "으로/로") == "법으로"
    assert has_batchim("법안") and not has_batchim("국회")
    assert short_name("국회 본회의 — 플랫폼노동자 기본법 표결") == "플랫폼노동자 기본법 표결"
    assert (
        short_name("새한당, 플랫폼노동자 기본법 표결 연기 요구…법사위 공전") == "새한당 플랫폼노동자 기본법"
    )


def test_numbers_quotes_sentences():
    assert numbers_in("환율 1,430원, 82%") == ["1430", "82"]
    assert quotes_in('그는 "반드시 처리"라고 했다') == ["반드시 처리"]
    assert split_sentences("첫 문장이다. 둘째 문장이다.") == ["첫 문장이다.", "둘째 문장이다."]
    assert count_chars(["## 헤더", "#출처: x", "가나다", ""]) == 3
