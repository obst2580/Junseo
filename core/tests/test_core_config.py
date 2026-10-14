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
    assert cfg.board.window_hours == 24 and cfg.script.chars_per_minute == 300
    assert cfg.channel.categories[0] == "국회"
    # 예전 설정 파일(selection·scoring)도 그대로 읽힌다(모르는 칸은 무시)
    (tmp_path / "old").mkdir()
    old = _root(tmp_path / "old", "selection:\n  mode: hottest\nscoring:\n  weights: {interest: 40}\n")
    monkeypatch.setenv("STUDIO_ROOT", str(old))
    assert load_channel(load_settings()).board.min_posts == 2


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


def test_kst_fallback_without_tz_database():
    from datetime import datetime, timedelta
    from zoneinfo import ZoneInfoNotFoundError

    from studio_core.config import load_kst

    def missing(_key):
        raise ZoneInfoNotFoundError("No time zone found with key Asia/Seoul")

    tz = load_kst(missing)  # type: ignore[arg-type]
    assert tz.utcoffset(None) == timedelta(hours=9)
    assert load_kst().utcoffset(datetime(2026, 7, 1)) == timedelta(hours=9)


def test_second_channel_uses_its_own_files(tmp_path, monkeypatch):
    """STUDIO_CHANNEL=stock이면 channel.stock.yaml·data-stock/·projects-stock/ — 정치 채널과 섞이지 않는다."""
    from pathlib import Path

    from studio_core.config import ChannelConfig, Settings

    monkeypatch.delenv("STUDIO_CHANNEL", raising=False)
    s = Settings(root=tmp_path)
    assert (s.channel_path.name, s.data_dir.name, s.projects_dir.name) == ("channel.yaml", "data", "projects")
    monkeypatch.setenv("STUDIO_CHANNEL", "Stock")
    assert (s.channel_path.name, s.data_dir.name, s.projects_dir.name) == (
        "channel.stock.yaml",
        "data-stock",
        "projects-stock",
    )
    monkeypatch.setenv("STUDIO_CHANNEL", "politics")  # 제작실이 정치 채널 요청에 적는 이름 = 기본 채널
    assert (s.channel_path.name, s.data_dir.name) == ("channel.yaml", "data")
    monkeypatch.setenv("STUDIO_CHANNEL", "../x")  # 경로 문자 제거
    assert s.channel == "x"
    import yaml

    repo = Path(__file__).resolve().parents[2]
    cfg = ChannelConfig.model_validate(
        yaml.safe_load((repo / "channel.stock.yaml").read_text(encoding="utf-8"))
    )
    assert (
        cfg.channel.profile == "stock"
        and cfg.channel.name
        and not cfg.communities
        and len(cfg.sources.rss) >= 10
    )
    assert "투자 권유가 아니며" in cfg.script.outro


def test_conservative_channel_config():
    """자유뉴스365: 보수 언론 사설·칼럼(kind: opinion)과 수집을 허용한 조갑제닷컴만. 막힌 곳·일베는 넣지 않는다."""
    from pathlib import Path

    import yaml

    from studio_core.config import ChannelConfig

    repo = Path(__file__).resolve().parents[2]
    cfg = ChannelConfig.model_validate(
        yaml.safe_load((repo / "channel.right.yaml").read_text(encoding="utf-8"))
    )
    assert cfg.channel.profile == "conservative" and cfg.channel.name == "자유뉴스365"
    opinion = [f.name for f in cfg.sources.rss if f.kind == "opinion"]
    assert len(opinion) >= 5 and "펜앤드마이크 칼럼" in opinion
    assert not any("자유일보" in n for n in opinion)
    assert len([f for f in cfg.sources.rss if f.kind == "domestic"]) >= 4  # 사실은 기사로
    assert [(c.name, c.enabled) for c in cfg.communities] == [
        ("조갑제닷컴", False)
    ]  # 강경 보수와 결이 달라 꺼 둠
    urls = " ".join(c.list_url for c in cfg.communities)
    assert not any(x in urls for x in ("ilbe", "fmkorea", "dcinside", "mlbpark"))
    assert "자유뉴스365" in cfg.script.outro and "자유뉴스365" in cfg.script.shorts_outro
    assert (
        cfg.shorts.style == "navy_red" and ChannelConfig().shorts.style == "default"
    )  # 쇼츠 디자인은 채널마다
