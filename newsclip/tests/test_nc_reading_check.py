"""음성 읽기 검수: 타입캐스트 낱말 시각과 들린 말(음성 인식)을 같은 시각끼리 견준다."""

from newsclip.reading_check import check_reading, number_issues, sentence_issues


def test_symbol_between_digits_read_as_word_is_flagged():
    tc = [["대구지법", 14.0, 14.9], ["형사항소2-3부는", 14.94, 16.01], ["극우", 16.1, 16.4]]
    asr = [("대구지법", 14.0, 14.8), ("형상소", 14.9, 15.3), ("2대3부는", 15.3, 16.0), ("그구", 16.1, 16.4)]
    got = number_issues(tc, asr)
    assert [g["kind"] for g in got] == ["reading_symbol"] and got[0]["word"] == "형사항소2-3부는"


def test_correct_numbers_and_ranges_pass():
    tc = [["10월", 2.19, 2.38], ["20일", 2.41, 2.62], ["3~4명이", 3.0, 3.6], ["70대는", 4.0, 4.4]]
    asr = [
        ("10월", 2.2, 2.4),
        ("20일", 2.4, 2.6),
        ("3에서", 3.0, 3.3),
        ("4명이", 3.3, 3.6),
        ("70대는", 4.0, 4.4),
    ]
    assert number_issues(tc, asr) == []
    # '20일'(이십 일)을 인식이 21로 받아 적는 건 소리가 같아서 — 실수가 아니다
    assert number_issues([["20일", 21.0, 21.4]], [("21정례", 21.0, 21.6)]) == []
    wrong = [("10월", 2.2, 2.4), ("12일", 2.4, 2.6)]
    assert [g["kind"] for g in number_issues(tc[:2], wrong)] == ["reading_number"]


def test_skipped_sentence_is_flagged():
    tc = [
        [w, 1.0 + 0.4 * i, 1.35 + 0.4 * i]
        for i, w in enumerate("대법원이 허위라고 본 전두환의 폭동을 따라 쓰면 의견이라고 본 겁니다.".split())
    ]
    asr = [("대법원이", 1.0, 1.4)]  # 한 낱말만 들림 — 건너뜀
    assert [g["kind"] for g in sentence_issues(tc, asr)] == ["reading_skip"]
    full = [(w, s, e) for w, s, e in tc]
    assert check_reading(tc, full) == []
