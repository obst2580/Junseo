"""AI 음성 + 대본 → 자막 자동 생성(쉼 맞추기)."""

import math
import shutil
import struct
import wave

import pytest

from newsclip.autosub import AudioInfo, align, analyze, make_srt, narration_text, script_sentences

SCRIPT = """## 금융권 해킹
금융보안원이 2주 전에 이미
위험을 알렸는데도
금융사들이 그대로 뚫렸습니다.
#출처: https://n.example/1
이유가 뭘까요?
#TODO: 원인 보강
외부에 노출된 자산을
그대로 뒀기 때문입니다.

## 내일 쟁점: 1심 선고
내일은 1심 선고가 나옵니다.
결과에 따라 다음 수순이 갈립니다.
"""


def test_sentences_and_narration_text():
    sents = script_sentences(SCRIPT)
    assert [len(s) for s in sents] == [3, 1, 2, 1, 1]
    text = narration_text(SCRIPT)
    assert "#" not in text and "https" not in text
    assert text.startswith("금융보안원이 2주 전에 이미 위험을 알렸는데도 금융사들이 그대로 뚫렸습니다.")
    assert "\n\n내일은 1심 선고가 나옵니다." in text  # 주제마다 단락


def _tone_wav(path, parts, rate=16000):
    """parts: [('t', 초) 소리 / ('s', 초) 무음]."""
    frames = bytearray()
    for kind, sec in parts:
        for i in range(int(sec * rate)):
            v = int(9000 * math.sin(2 * math.pi * 220 * i / rate)) if kind == "t" else 0
            frames += struct.pack("<h", v)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(bytes(frames))


@pytest.mark.skipif(shutil.which("ffmpeg") is None, reason="ffmpeg 필요")
def test_make_srt_snaps_to_pauses(tmp_path):
    sents = script_sentences(SCRIPT)
    parts, starts, t = [("s", 0.4)], [], 0.4
    for n, s in enumerate(sents):
        dur = 0.11 * sum(len(x) for x in s)
        starts.append(t)
        if n == 0:  # 문장 안 짧은 쉼(쉼표) — 경계로 쓰면 안 됨
            parts += [("t", dur / 2), ("s", 0.25), ("t", dur / 2)]
            dur += 0.25
        else:
            parts.append(("t", dur))
        gap = 0.9 if n == 2 else 0.5  # 주제가 바뀌는 곳은 더 길게 쉼
        parts.append(("s", gap))
        t += dur + gap
    wav = tmp_path / "narration.wav"
    _tone_wav(wav, parts)
    cues, info, matched = make_srt(SCRIPT, wav)
    assert len(cues) == sum(len(s) for s in sents) and matched == len(sents) - 1
    firsts, i = [], 0
    for s in sents:
        firsts.append(cues[i].start)
        i += len(s)
    assert all(abs(a - b) < 0.12 for a, b in zip(firsts, starts, strict=True)), (firsts, starts)
    assert all(c.end > c.start for c in cues) and all(
        a.end <= b.start for a, b in zip(cues, cues[1:], strict=False)
    )
    assert info.duration == pytest.approx(t, abs=0.05)


def test_align_without_pauses_falls_back_to_length():
    sents = [["가나다라마바사."], ["가나다라마바사아자차카타파하."]]
    cues, matched = align(sents, AudioInfo(10.0, []))
    assert (
        matched == 0 and cues[0].start == 0 and cues[1].start == pytest.approx(10 * 7.4 / 21.8, abs=0.01)
    )  # 읽는 길이 비율(한글 1, 마침표 0.4)


def test_analyze_wav_without_ffmpeg(tmp_path, monkeypatch):
    wav = tmp_path / "a.wav"
    _tone_wav(wav, [("t", 1.0)])
    monkeypatch.setattr(shutil, "which", lambda _n: None)
    info = analyze(wav)
    assert info.duration == pytest.approx(1.0) and info.silences == []


def test_phrases_cut_at_sentence_and_quote_not_in_the_middle():
    """장면 = 말 덩어리: 문장 끝, 쉼표 뒤, 인용이 시작하는 곳 앞에서만 자르고 인용·낱말 나열은 쪼개지 않는다."""
    from newsclip.autosub import script_phrases, split_phrases

    old = (  # 25자 자막 줄로 쓴 예전 대본도 줄바꿈과 상관없이 같은 곳에서 나뉜다
        '국민의힘이 10월 11일 일요일\n이재명 대통령의 SNS 메시지를 두고\n"소통이 아니라 지지율 방어용\n'
        "'왼쪽' 챙기기\"라고 했습니다.\n비판한다고 차단하지는 않는다\"고 못\n박았습니다. 비판하는 손님은 들이고\n"
        "마당에 오물 뿌리는 사람만 내보내겠다는 거죠. 이걸 문제 삼으면 욕설할 권리를 달라는 말밖에 안 됩니다.\n"
    )
    flat = [p for s in script_phrases(old) for p in s]
    assert flat[:2] == [
        "국민의힘이 10월 11일 일요일 이재명 대통령의 SNS 메시지를 두고",
        "\"소통이 아니라 지지율 방어용 '왼쪽' 챙기기\"라고 했습니다.",
    ]
    assert "비판하는 손님은 들이고 마당에 오물 뿌리는 사람만 내보내겠다는 거죠." in flat
    assert flat[-1] == "이걸 문제 삼으면 욕설할 권리를 달라는 말밖에 안 됩니다."
    assert split_phrases("폭언, 욕설, 비아냥, 반말, 멸칭, 가짜뉴스 유포를 하면 잠깐 시원할지 몰라도") == [
        "폭언, 욕설, 비아냥, 반말, 멸칭, 가짜뉴스 유포를 하면 잠깐 시원할지 몰라도"
    ]
    # 한 줄 한 문장으로 쓴 대본은 글쓴이 줄바꿈도 자르는 곳
    good = (
        "여러분, 솔직히 웃기잖아요?\n말장난 한 글자에 정색하는 쪽이 품격을 따지고 있으니까요.\n"
        "국민의힘이 단어 하나에서 '왼쪽'을 찾는 동안\n대통령은 욕 대신 설득으로 이기자고 했습니다.\n"
    )
    assert script_phrases(good)[-1] == [
        "국민의힘이 단어 하나에서 '왼쪽'을 찾는 동안",
        "대통령은 욕 대신 설득으로 이기자고 했습니다.",
    ]


def test_segment_phrases_one_scene_each_but_not_too_short():
    from newsclip.scenes import segment_phrases
    from newsclip.srt import Cue

    cues = [
        Cue(1, 0, 3, "a,"),
        Cue(2, 3, 4, "b,"),
        Cue(3, 4, 7, "c."),
        Cue(4, 7, 8, "d,"),
        Cue(5, 8, 9.2, "e"),
    ]
    assert segment_phrases(cues, ["A", "A", "A", "B", "B"]) == [[0], [1, 2], [3, 4]]
    assert segment_phrases(cues[:2], ["A", "A"]) == [[0, 1]]  # 짧은 꼬리는 앞 장면에
    # 짧아도 문장이 끝나면 제 장면(다음 문장과 묶지 않는다)
    short = [Cue(1, 0, 1.2, "보시죠."), Cue(2, 1.2, 6, '"진실에 기초해…"라고 했고요,')]
    assert segment_phrases(short, ["A", "A"]) == [[0], [1]]


def test_subtitle_lines_even_and_not_after_short_adverbs():
    from newsclip.autosub import subtitle_cues, subtitle_lines
    from newsclip.srt import Cue

    assert subtitle_lines("결론부터 말씀드리면 이건 글은 안 읽고 단어 하나만 붙잡은 겁니다.") == [
        "결론부터 말씀드리면 이건 글은 안 읽고",
        "단어 하나만 붙잡은 겁니다.",
    ]
    lines = subtitle_lines("대통령이 엑스에 '좌아하게'라는 말을 쓴 게 한쪽 진영만 본다는 비판인데요,")
    assert len(lines) == 2 and all(len(x) <= 22 for x in lines)  # 외톨이 '비판인데요,' 없이 고르게
    subs = subtitle_cues(
        [Cue(1, 0.0, 4.0, "결론부터 말씀드리면 이건 글은 안 읽고 단어 하나만 붙잡은 겁니다.")]
    )
    assert len(subs) == 2 and subs[0].start == 0.0 and subs[-1].end <= 4.0


def test_spoken_length_and_comma_pause_snapping():
    from newsclip.autosub import spoken_len, subtitle_cues

    assert spoken_len("SNS") == pytest.approx(4.8) and spoken_len("10월") == pytest.approx(3.6)
    # 한 문장 안 두 덩어리: 쉼표 뒤 짧은 쉼(3.1~3.3초)에 덩어리 경계를 맞춘다(글자 비율로는 2.9초쯤)
    sents = [["가나다라마바사아자차카타파,", "가나다라마바사아자차카타파하거너더."]]
    cues, _ = align(sents, AudioInfo(7.0, [(3.1, 3.3)]))
    assert cues[0].end == pytest.approx(3.05, abs=0.01) and cues[1].start == pytest.approx(3.3, abs=0.01)
    # 자막 줄도 그 안의 쉼에 맞추고, 짧은 틈은 이어 띄운다
    subs = subtitle_cues(cues, limit=8, info=AudioInfo(7.0, [(3.1, 3.3)]))
    assert all(a.end <= b.start for a, b in zip(subs, subs[1:], strict=False)) and subs[-1].end <= 7.0
    assert all(b.start - a.end < 0.1 for a, b in zip(subs, subs[1:], strict=False))


def test_units_and_subtitles_follow_comma_pauses():
    from newsclip.autosub import subtitle_cues, subtitle_lines, units_of

    assert units_of('"품격 없다, 권위 없다" 비난이 나와도 계속하겠다고 했거든요.') == [
        ('"품격 없다,', "comma"),
        ('권위 없다" 비난이 나와도 계속하겠다고 했거든요.', "sent"),
    ]
    assert (
        subtitle_lines("그런데 여당 의원들이 바로 이 김 위원을 정면으로 겨냥했습니다.")[0].endswith("이")
        is False
    )
    # 한 문장, 쉼표 둘: 문장 끝은 긴 쉼(5.0~5.6), 쉼표는 짧은 숨(1.9~2.1, 3.6~3.8)에 맞는다
    sents = [
        ["가나다라마바사아자차카, 타파하가나다라마바사아자, 차카타파하가나다라마바사."],
        ["아자차카타파하가나다라."],
    ]
    cues, matched = align(sents, AudioInfo(8.0, [(1.9, 2.1), (3.6, 3.8), (5.0, 5.6)]))
    assert matched == 1 and [round(x, 2) for _t, x, _y in cues[0].units] == [0.0, 2.1, 3.8]
    subs = subtitle_cues(cues, limit=12)
    assert any(abs(s.start - 2.1) < 0.01 for s in subs) and any(abs(s.start - 5.6) < 0.01 for s in subs)
