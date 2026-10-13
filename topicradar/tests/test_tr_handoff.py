"""Claude API 없이 쓰기: 주제판 내보내기 → (대본실) → 대본 가져오기."""

import json

import pytest
from typer.testing import CliRunner

from topicradar.cli import app

runner = CliRunner()


def _cli(monkeypatch, now, *args, code=0):
    monkeypatch.setenv("STUDIO_NOW", now)
    res = runner.invoke(app, list(args))
    assert res.exit_code == code, res.output
    return res.output


@pytest.fixture
def board_env(demo_env, monkeypatch):
    _cli(monkeypatch, "2026-10-10T17:00", "collect", "--since", "2026-10-05T00:00")
    _cli(monkeypatch, "2026-10-08T18:00", "board")
    from studio_core.config import load_channel, load_settings
    from topicradar import pipeline

    settings = load_settings()
    return settings, load_channel(settings), pipeline.open_store(settings)


def _script_file(data, ids, fmt="long", **extra):
    by_id = {t["id"]: t for t in data["topics"]}
    parts = []
    for tid in ids:
        t = by_id[tid]
        url = t["articles"][0]["url"] if t["articles"] else None
        parts += [f"## {t['heading']}", "이건 대본실에서 쓴 문장인데요,", "말하듯 이어지는 줄입니다."]
        if url:
            parts.append(f"#출처: {url}")
    if fmt == "long":
        parts += ["## 정리", "결국 하나의 이야기입니다."]
    return {
        "kind": "studio-script",
        "version": 1,
        "board_id": data["board_id"],
        "format": fmt,
        "topic_ids": ids,
        "script": "\n".join(parts),
        **extra,
    }


def test_export_has_everything_the_page_needs(board_env):
    from topicradar import pipeline

    _settings, cfg, store = board_env
    data, name = pipeline.run_export(cfg, store)
    assert data["kind"] == "studio-board" and data["version"] == 1 and name.endswith(".json")
    assert data["formats"]["long"]["target_chars"] == [2700, 3300]
    assert data["formats"]["short"]["target_chars"] == [810, 990]
    rules = data["rules"]
    assert "편집 데스크" in rules["identity"] and "## 정리" in rules["format_rules"]["long"]
    assert "말투" in rules["tone_rules"] and rules["line_limit"] == 25
    lead = data["topics"][0]
    assert lead["heading"] and lead["opinion"]["stance"] and lead["heat"].startswith("글 ")
    # 의견 근거(글·본문·공감 많은 댓글)와 사실 근거(기사)가 따로 간다
    assert lead["posts"] and any(p["top_comments"] for p in lead["posts"])
    assert all("nick" not in json.dumps(p, ensure_ascii=False) for p in lead["posts"])
    assert any(t["articles"] for t in data["topics"])
    assert all(
        not a["url"].startswith("https://www.bobaedream") for t in data["topics"] for a in t["articles"]
    )
    assert data["bundles"] and all(2 <= len(b["topic_ids"]) <= 3 for b in data["bundles"])
    json.dumps(data, ensure_ascii=False)  # 그대로 파일로 저장된다


def test_import_makes_project_without_api(board_env, monkeypatch):
    from topicradar import pipeline

    settings, cfg, store = board_env
    data, _ = pipeline.run_export(cfg, store)
    ids = data["bundles"][0]["topic_ids"]
    sf = _script_file(
        data,
        ids,
        opinions={
            ids[0]: {"stance": "대본실에서 다듬은 주장입니다", "points": ["근거 하나"], "minority": None}
        },
        link="두 주제는 같은 사람의 이야기입니다.",
        titles=["대본실 제목 1안", "대본실 제목 2안"],
        thumbnail_text="썸네일은열자를넘기면잘린다",
    )
    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T18:30")
    res = pipeline.run_import(settings, cfg, store, json.dumps(sf, ensure_ascii=False).encode())
    plan = res.plan
    assert res.project.name == "2026-10-08_1830_long" and plan.board_id == data["board_id"]
    assert plan.script_mode == "claude" and plan.script_note == "claude.ai 대본실에서 씀"
    assert (
        plan.items[0].opinion.stance == "대본실에서 다듬은 주장입니다"
        and plan.items[0].opinion.by == "claude"
    )
    assert plan.items[0].opinion.posts >= 1  # 숫자·대표 글은 판의 것 그대로
    assert plan.link == "두 주제는 같은 사람의 이야기입니다."
    assert plan.titles[:2] == ["대본실 제목 1안", "대본실 제목 2안"] and len(plan.titles) == 3
    assert plan.thumbnail_text == "썸네일은열자를넘기면"
    script = (res.project / "script.txt").read_text(encoding="utf-8")
    assert script.startswith("## ") and "대본실에서 쓴 문장" in script
    report = (res.project / "report.md").read_text(encoding="utf-8")
    assert "- 대본: Claude (claude.ai 대본실에서 씀)" in report and "오프라인 골격" not in report
    assert not [w for w in res.warnings if "계획과 다름" in w]
    assert any("글자 수" in w for w in res.warnings)  # 짧은 대본은 점검에 걸린다
    assert all(plan.id in store.get_topic(i).covered_in for i in ids)


def test_import_cli_and_short(board_env, monkeypatch, tmp_path):
    from topicradar import pipeline

    _settings, cfg, store = board_env
    out = _cli(monkeypatch, "2026-10-08T18:31", "export", "--out", str(tmp_path / "board.json"))
    assert "대본실용 파일" in out
    data = json.loads((tmp_path / "board.json").read_text(encoding="utf-8"))
    assert data == json.loads(json.dumps(pipeline.run_export(cfg, store)[0], ensure_ascii=False))
    f = tmp_path / "script.json"
    f.write_text(json.dumps(_script_file(data, [data["topics"][0]["id"]], "short"), ensure_ascii=False))
    out = _cli(monkeypatch, "2026-10-08T18:32", "import-script", str(f))
    assert "2026-10-08_1832_short" in out


@pytest.mark.parametrize(
    ("mutate", "msg"),
    [
        (lambda d, sf: d, "주제판 파일"),  # 내보낸 파일을 그대로 넣음
        (lambda d, sf: {**sf, "script": "  "}, "대본이 비어"),
        (lambda d, sf: {**sf, "kind": "x"}, "대본실에서 저장한 파일이 아닙"),
        (lambda d, sf: {**sf, "version": 9}, "버전"),
        (lambda d, sf: {k: v for k, v in sf.items() if k != "topic_ids"}, "topic_ids"),
        (lambda d, sf: "{not json", "JSON 형식"),
        (lambda d, sf: {**sf, "topic_ids": sf["topic_ids"][:1]}, "2~3개"),
    ],
)
def test_import_rejects_bad_files(board_env, mutate, msg):
    from topicradar import pipeline

    settings, cfg, store = board_env
    data, _ = pipeline.run_export(cfg, store)
    sf = _script_file(data, data["bundles"][0]["topic_ids"])
    with pytest.raises(ValueError, match=msg):
        pipeline.run_import(settings, cfg, store, mutate(data, sf))


def test_import_unknown_board_or_topic(board_env):
    from topicradar import pipeline

    settings, cfg, store = board_env
    data, _ = pipeline.run_export(cfg, store)
    sf = _script_file(data, data["bundles"][0]["topic_ids"])
    with pytest.raises(FileNotFoundError, match="주제판"):
        pipeline.run_import(settings, cfg, store, {**sf, "board_id": "1999-01-01_000000"})
    with pytest.raises(KeyError):
        pipeline.run_import(settings, cfg, store, {**sf, "topic_ids": ["nope", sf["topic_ids"][0]]})


def test_stock_profile_rules():
    """증시 채널 규칙: 기사로 주제를 정하고, 애널리스트 말투, 투자 권유 표현을 점검한다."""
    from studio_core.config import ChannelConfig
    from topicradar import prompts
    from topicradar.handoff import channel_rules

    cfg = ChannelConfig.model_validate(
        {
            "channel": {"profile": "stock", "name": "증시뉴스"},
            "script": {"tone": "애널리스트형", "outro": "끝"},
        }
    )
    r = channel_rules(cfg)
    assert r["profile"] == "stock" and r["channel_name"] == "증시뉴스"
    rules = r["rules"]
    assert rules["identity"] == prompts.STOCK_IDENTITY and "기사로 정한다" in rules["group_task"]
    assert "숫자가 먼저" in rules["script_rules"] and "체크포인트" in rules["format_rules"]["long"]
    assert rules["angles"] == ["시황", "종목", "정책거시", "해외"] and "애널리스트" in rules["tone_rules"]
    assert "목표가" in rules["risk"]["legal_words"] and rules["vague_words"] != prompts.PROFILES[
        "politics"
    ].get("vague")
    base = channel_rules(ChannelConfig())["rules"]  # 정치 채널은 그대로
    assert base["identity"] == prompts.IDENTITY and base["angles"][0] == "상대 비판"


def test_stock_rules_are_news_only():
    from topicradar import prompts

    for key in ("identity", "group_task", "script_rules"):
        text = prompts.PROFILES["stock"][key]
        assert "뉴스만" in text or "기사로" in text
    assert (
        "opinion.community" not in prompts.STOCK_SCRIPT_RULES
        and "게시판 글(posts)을 붙인다" not in prompts.STOCK_GROUP_TASK
    )


def test_shorts_format_only_for_politics():
    """세로 쇼츠(1분, 첫 10초 훅)는 정치 채널만. 증시 채널은 아직 쇼츠 없음."""
    from studio_core.config import ChannelConfig
    from topicradar.handoff import channel_rules

    cfg = ChannelConfig.model_validate({"script": {"outro": "긴 마무리 인사", "shorts_outro": "짧은 인사"}})
    r = channel_rules(cfg)
    spec = r["formats"]["shorts"]
    assert spec["vertical"] and spec["max_topics"] == 1 and spec["minutes"] == 1
    assert not r["formats"]["short"]["vertical"]
    rule = r["rules"]["format_rules"]["shorts"]
    assert "처음 10초" in rule and "자극적" in rule and "없는 말을 만들지 않는다" in rule
    assert "'충격'" in rule and "금지어 규칙은 쇼츠에 적용하지 않는다" in rule  # 쇼츠는 센 말 허용
    assert r["rules"]["shorts_outro"] == "짧은 인사" and r["rules"]["outro"] == "긴 마무리 인사"
    assert (
        channel_rules(ChannelConfig.model_validate({"script": {"outro": "끝"}}))["rules"]["shorts_outro"]
        == "끝"
    )
    stock = channel_rules(ChannelConfig.model_validate({"channel": {"profile": "stock"}}))
    assert "shorts" not in stock["formats"] and "shorts" not in stock["rules"]["format_rules"]
