from pathlib import Path

import yaml

from studio_core.config import append_past_title, find_root, load_channel, load_settings, now_kst


def _root(tmp_path: Path, text: str) -> Path:
    (tmp_path / "channel.yaml").write_text(text, encoding="utf-8")
    return tmp_path


def test_find_root_env_and_walk(tmp_path, monkeypatch):
    monkeypatch.delenv("STUDIO_ROOT", raising=False)
    root = _root(tmp_path, "channel: {}\n")
    sub = root / "a" / "b"
    sub.mkdir(parents=True)
    assert find_root(sub) == root
    monkeypatch.setenv("STUDIO_ROOT", str(root))
    assert find_root(Path("/")) == root


def test_load_settings_and_channel_defaults(tmp_path, monkeypatch):
    root = _root(tmp_path, "channel:\n  competitors: [UCx]\n")
    (root / ".env").write_text("STUDIO_TEST_KEY=hello\n", encoding="utf-8")
    monkeypatch.setenv("STUDIO_ROOT", str(root))
    monkeypatch.delenv("STUDIO_TEST_KEY", raising=False)
    s = load_settings()
    assert s.db_path.parent.exists() and s.env("STUDIO_TEST_KEY") == "hello"
    cfg = load_channel(s)
    assert cfg.channel.competitors == ["UCx"]
    assert cfg.scoring.weights["interest"] == 40.0
    assert cfg.channel.categories[0] == "국회"


def test_append_past_title_keeps_comments(tmp_path, monkeypatch):
    root = _root(
        tmp_path,
        '# 머리 주석\nchannel:\n  past_titles: []            # 업로드 후 자동 추가\n  slack_webhook: ""\n',
    )
    monkeypatch.setenv("STUDIO_ROOT", str(root))
    s = load_settings()
    assert append_past_title(s, "표결을 미룬 진짜 이유")
    assert not append_past_title(s, "표결을 미룬 진짜 이유")
    assert append_past_title(s, '"따옴표" 제목')
    text = (root / "channel.yaml").read_text(encoding="utf-8")
    assert "# 머리 주석" in text and "# 업로드 후 자동 추가" in text
    data = yaml.safe_load(text)
    assert data["channel"]["past_titles"] == ["표결을 미룬 진짜 이유", '"따옴표" 제목']


def test_append_past_title_block_list(tmp_path, monkeypatch):
    root = _root(tmp_path, "channel:\n  past_titles:\n    - 하나\n    - 둘\n  stance: x\n")
    monkeypatch.setenv("STUDIO_ROOT", str(root))
    s = load_settings()
    append_past_title(s, "셋")
    data = yaml.safe_load((root / "channel.yaml").read_text(encoding="utf-8"))
    assert data["channel"]["past_titles"] == ["하나", "둘", "셋"] and data["channel"]["stance"] == "x"


def test_now_kst_override(monkeypatch):
    monkeypatch.setenv("STUDIO_NOW", "2026-10-08T05:30")
    assert now_kst().hour == 5 and now_kst().utcoffset().total_seconds() == 9 * 3600
