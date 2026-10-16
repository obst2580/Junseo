from types import SimpleNamespace

from newsclip.scenes import (
    LLMScene,
    LLMScenes,
    align_topics,
    build_scenes,
    heuristic_keywords,
    heuristic_kind,
    parse_script,
    segment,
)
from newsclip.srt import Cue, fmt_time, parse_srt, write_srt

SRT = "﻿1\r\n00:00:00,600 --> 00:00:03,100\r\n국회는 8일 본회의를 열고\r\n\r\n00:00:03.300 --> 00:00:06,000\r\n<i>법안을 가결했습니다</i>\r\n\r\n3\r\n00:00:06,000 --> 00:00:05,000\r\n역전된 시각\r\n"


def test_parse_srt_variants_and_roundtrip():
    cues = parse_srt(SRT)
    assert [c.text for c in cues] == ["국회는 8일 본회의를 열고", "법안을 가결했습니다"]
    assert cues[1].start == 3.3 and cues[1].index == 2
    again = parse_srt(write_srt(cues))
    assert [(c.start, c.end, c.text) for c in again] == [(c.start, c.end, c.text) for c in cues]
    assert fmt_time(3723.456) == "01:02:03,456"


SCRIPT = """## 플랫폼노동자 기본법
국회는 8일 본회의를 열고
플랫폼노동자 기본법을 가결했습니다.
#출처: https://n.example/1
새한당은 표결에 불참했습니다.

## 아침 포인트 회수: 1심 선고
아침 예상과 달리, 재판부가
1심 선고를 연기했습니다.
그래서 다음은, 다음 달 선고입니다.
"""


def _cues():
    texts = [
        "국회는 8일 본회의를 열고 플랫폼노동자 기본법을 가결했습니다",
        "새한당은 표결에 불참했습니다",
        "아침 예상과 달리 재판부가",
        "1심 선고를 연기했습니다",
        "그래서 다음은 다음 달 선고입니다",
    ]
    out, t = [], 0.5
    for i, x in enumerate(texts, start=1):
        out.append(Cue(i, t, t + 3.0, x))
        t += 3.2
    return out


def test_parse_and_align_topics():
    lines = parse_script(SCRIPT)
    assert len(lines) == 6 and lines[0].topic == "플랫폼노동자 기본법"
    topics = align_topics(_cues(), lines)
    assert topics[:2] == ["플랫폼노동자 기본법"] * 2 and topics[2:] == ["아침 포인트 회수: 1심 선고"] * 3
    assert align_topics(_cues(), []) == ["본문"] * 5


def test_segment_rules():
    cues = _cues()
    topics = ["A", "A", "B", "B", "B"]
    groups = segment(cues, topics)
    assert all(len({topics[i] for i in g}) == 1 for g in groups)  # 주제 경계를 넘지 않음
    assert [len(g) for g in groups] == [2, 2, 1] or [len(g) for g in groups] == [2, 3]
    long = [Cue(i, i * 9.0, i * 9.0 + 9.0, "긴 문장") for i in range(1, 4)]
    assert segment(long, ["A"] * 3) == [[0], [1], [2]]  # 8초 초과면 한 큐씩


def test_keywords_and_kind():
    ko, en = heuristic_keywords("국회는 8일 본회의를 열고 법안을 가결", "아침 포인트 회수: 플랫폼노동자")
    assert ko[0] == "국회" and "본회의" in ko and "아침" not in ko and "National Assembly Korea" in en
    assert heuristic_kind("본회의 표결", ["국회"]) == "video"
    assert heuristic_kind("국회 전경", ["국회"]) == "photo"
    assert heuristic_kind("분위기", ["분위기"]) == "broll"


def test_build_scenes_contiguous_and_llm():
    scenes = build_scenes(_cues(), SCRIPT)
    assert scenes[0].start_sec == 0.0
    assert all(a.end_sec == b.start_sec for a, b in zip(scenes, scenes[1:], strict=False))
    assert scenes[-1].end_sec == _cues()[-1].end

    class Good:
        def json(self, task, system, prompt, output, **kw):
            return LLMScenes(
                scenes=[
                    LLMScene(cue_ids=[1, 2], kind="video", keywords_ko=["본회의"], keywords_en=["plenary"]),
                    LLMScene(
                        cue_ids=[3, 4, 5], kind="photo", keywords_ko=["서울중앙지법"], keywords_en=["court"]
                    ),
                ]
            )

    llm_scenes = build_scenes(_cues(), SCRIPT, Good())
    assert [s.kind for s in llm_scenes] == ["video", "photo"] and llm_scenes[1].keywords_ko == [
        "서울중앙지법"
    ]

    class Bad:
        def json(self, *a, **k):
            return LLMScenes(scenes=[LLMScene(cue_ids=[1, 3], kind="video", keywords_ko=[], keywords_en=[])])

    assert len(build_scenes(_cues(), SCRIPT, Bad())) == len(scenes)
    _ = SimpleNamespace
