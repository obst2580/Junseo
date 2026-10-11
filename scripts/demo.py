"""end-to-end 시연: fixtures(가상 뉴스·커뮤니티) → 주제판 → 10분 묶음·3분 단독 대본 → made/history → newsclip → timeline.xml.

네트워크·API 키 없이 돈다:
- STUDIO_FIXTURES: 모든 HTTP 요청을 fixtures/demo/routes.yaml 규칙으로 대체
- STUDIO_LLM=offline: Claude 대신 결정적 휴리스틱(키가 있으면 이 변수를 빼고 돌리면 Claude 경로)
- STUDIO_NOW: 단계마다 '현재 시각'을 고정해 실제 운영 순서를 재현

실행: uv run python scripts/demo.py   (결과: demo_root/, 요약 복사본: docs/demo/)
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEMO = ROOT / "demo_root"
FIX = ROOT / "fixtures" / "demo"
OUT = ROOT / "docs" / "demo"

BASE_ENV = {
    "STUDIO_ROOT": str(DEMO),
    "STUDIO_FIXTURES": str(FIX),
    "STUDIO_LLM": "offline",
    "NAVER_CLIENT_ID": "demo",
    "NAVER_CLIENT_SECRET": "demo",
    "YOUTUBE_API_KEY": "demo",
    "STUDIO_NEWSCLIP_FAKE_YTDLP": "1",
    "STUDIO_CONTACT": "demo@example.com",
    "COLUMNS": "120",
}

log_lines: list[str] = []


def run(args: list[str], now: str) -> str:
    """명령 실행 + 로그."""
    env = {**os.environ, **BASE_ENV, "STUDIO_NOW": now}
    env.pop("ANTHROPIC_API_KEY", None)
    cmd = [sys.executable, "-m", *args]
    shown = " ".join(args).replace("topicradar.cli", "topicradar").replace("newsclip.cli", "newsclip")
    print(f"\n$ STUDIO_NOW={now} {shown}")
    proc = subprocess.run(cmd, env=env, cwd=ROOT, capture_output=True, text=True)
    out = (proc.stdout + proc.stderr).strip()
    print(out)
    log_lines.extend([f"### `{shown}`  (STUDIO_NOW={now})", "", "```", out, "```", ""])
    if proc.returncode != 0:
        raise SystemExit(f"실패: {shown} (exit {proc.returncode})")
    return out


def prepare() -> None:
    """fixture 빌드 + 깨끗한 시연 루트."""
    subprocess.run([sys.executable, str(FIX / "build.py")], check=True, cwd=ROOT)
    if DEMO.exists():
        shutil.rmtree(DEMO)
    (DEMO / "data" / "schedule").mkdir(parents=True)
    shutil.copy(FIX / "channel.yaml", DEMO / "channel.yaml")
    for f in (FIX / "schedule").glob("*.yaml"):
        shutil.copy(f, DEMO / "data" / "schedule" / f.name)


BOARD_AT = "2026-10-08T18:00"  # 이 시각의 주제판(최근 24시간 커뮤니티 글)


def topicradar_flow() -> None:
    """수집 → 주제판 → 10분 묶음(추천 묶음 1) + 3분 단독 2편(판 1·2위) → made → history."""
    run(["topicradar.cli", "collect", "--since", "2026-10-05T00:00"], "2026-10-10T17:00")
    run(["topicradar.cli", "board", "--notify"], BOARD_AT)
    run(["topicradar.cli", "video", "--format", "long", "--bundle", "1"], "2026-10-08T18:05")
    run(["topicradar.cli", "video", "--format", "short", "--pick", "1"], "2026-10-08T18:10")
    run(["topicradar.cli", "video", "--format", "short", "--pick", "3"], "2026-10-08T18:15")
    made = (
        ("2026-10-08_1805_long", "demo_long1008"),
        ("2026-10-08_1810_short", "demo_short1008a"),
        ("2026-10-08_1815_short", "demo_short1008b"),
    )
    for pid, vid in made:
        run(
            ["topicradar.cli", "made", "--project", f"demo_root/projects/{pid}", "--video-id", vid],
            "2026-10-08T21:00",
        )
    run(["topicradar.cli", "history", "--weeks", "2"], "2026-10-11T21:00")


def newsclip_flow() -> None:
    """10분 묶음 대본 + 샘플 SRT → newsclip run → timeline.xml."""
    proj = DEMO / "projects" / "2026-10-08_1805_long"
    env = {**os.environ, **BASE_ENV}
    subprocess.run(
        [sys.executable, str(ROOT / "scripts" / "make_sample_srt.py"), str(proj)], check=True, env=env
    )
    run(
        ["newsclip.cli", "run", "--project", str(proj.relative_to(ROOT)), "--per-scene", "3"],
        "2026-10-08T18:30",
    )
    run(
        ["newsclip.cli", "xml", "--project", str(proj.relative_to(ROOT)), "--audio", "narration.wav"],
        "2026-10-08T18:40",
    )


def export() -> None:
    """docs/demo/에 결과 복사(바이너리 제외)."""
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    for proj in sorted((DEMO / "projects").iterdir()):
        dst = OUT / proj.name
        dst.mkdir()
        for name in (
            "report.md",
            "script.txt",
            "narration.srt",
            "scenes.json",
            "assets.json",
            "timeline.xml",
        ):
            if (proj / name).exists():
                shutil.copy(proj / name, dst / name)
    shutil.copy(DEMO / "data" / "history.md", OUT / "history.md")
    shutil.copy(DEMO / "channel.yaml", OUT / "channel.after_made.yaml")
    (OUT / "demo_log.md").write_text("# 시연 실행 로그\n\n" + "\n".join(log_lines), encoding="utf-8")


def main() -> None:
    """전체 시연."""
    prepare()
    topicradar_flow()
    if "--no-newsclip" not in sys.argv:
        newsclip_flow()
    export()
    print(f"\n완료 → {DEMO} (요약: {OUT})")


if __name__ == "__main__":
    main()
