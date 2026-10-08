"""터미널 화면: 줄 바꿈, 색, 능력치 막대, 기다리는 동안 도는 표시."""
from __future__ import annotations

import itertools
import sys
import threading
import unicodedata
from contextlib import contextmanager

from .game import CRITICAL, STATS, Life, span_text

COLS = 72
_COLOR = sys.stdout.isatty()


def _c(code: str, text: str) -> str:
    return f"\033[{code}m{text}\033[0m" if _COLOR else text


def dim(text: str) -> str: return _c("2", text)
def bold(text: str) -> str: return _c("1", text)
def accent(text: str) -> str: return _c("36", text)
def warn(text: str) -> str: return _c("33", text)


def width(text: str) -> int:
    """화면에 차지하는 칸 수 (한글은 두 칸)."""
    return sum(2 if unicodedata.east_asian_width(ch) in "WF" else 1 for ch in text)


def wrap(text: str, cols: int = COLS) -> list[str]:
    lines: list[str] = []
    for paragraph in text.splitlines() or [""]:
        line = ""
        for word in paragraph.split():
            candidate = f"{line} {word}" if line else word
            if width(candidate) <= cols:
                line = candidate
                continue
            if line:
                lines.append(line)
            line = word
            while width(line) > cols:  # 띄어쓰기 없이 긴 낱말은 글자 단위로 자른다
                cut = 0
                while width(line[: cut + 1]) <= cols:
                    cut += 1
                lines.append(line[:cut])
                line = line[cut:]
        lines.append(line)
    return lines


def say(text: str = "", style=lambda s: s) -> None:
    for line in wrap(text):
        print(style(line))


def rule(title: str = "") -> None:
    label = f" {title} " if title else ""
    print(dim("─" * 2) + bold(label) + dim("─" * max(0, COLS - 2 - width(label))))


def bar(value: int, size: int = 10) -> str:
    filled = round(value / 100 * size)
    return accent("█" * filled) + dim("░" * (size - filled))


def status(life: Life) -> None:
    rule(f"{life.name} · {life.age}살 · {life.when}")
    for key, label in STATS.items():
        print(f"  {label} {bar(getattr(life, key))} {getattr(life, key):>3}")
    print(f"  돈   {life.money:,}만원")
    print(f"  학력 {life.education}  /  직업 {life.job}")


def people(life: Life) -> None:
    rule("주변 사람")
    if not life.people:
        print(dim("  아무도 없어요."))
    for p in life.people:
        print(f"  {p['name']} ({p['relation']}) {bar(p['closeness'], 5)}")


def chronicle(life: Life) -> None:
    rule("인생 기록")
    for line in life.chronicle:
        say(line)


def scene(life: Life) -> None:
    print()
    rule(f"{life.age}살 · {life.when}")
    say(life.scene)
    if life.suggestions:
        say("예: " + " / ".join(life.suggestions), dim)
    if life.health < CRITICAL:
        say(f"건강이 {life.health}로 위독해요. 쉬거나 병원에 가는 등 몸을 돌보면 회복할 수 있어요.", warn)


def result(text: str, before: dict | None = None, after: dict | None = None, span: int | None = None) -> None:
    print()
    if span is not None:
        print(accent(span_text(span)))
    say(text)
    if before and after:
        labels = {**STATS, "money": "돈"}
        diffs = [
            f"{labels[k]} {after[k] - before[k]:+,}" + ("만원" if k == "money" else "")
            for k in labels if after[k] != before[k]
        ]
        if diffs:
            print(dim("  " + " · ".join(diffs)))


def snapshot(life: Life) -> dict:
    return {key: getattr(life, key) for key in (*STATS, "money")}


@contextmanager
def waiting(message: str = "이야기를 쓰는 중"):
    """API 응답을 기다리는 동안 한 줄에서 도는 표시."""
    if not _COLOR:
        print(dim(message + "…"))
        yield
        return
    done = threading.Event()

    def spin():
        for frame in itertools.cycle("⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"):
            if done.wait(0.1):
                break
            sys.stdout.write(f"\r{dim(frame + ' ' + message)}")
            sys.stdout.flush()
        sys.stdout.write("\r" + " " * (width(message) + 4) + "\r")
        sys.stdout.flush()

    thread = threading.Thread(target=spin, daemon=True)
    thread.start()
    try:
        yield
    finally:
        done.set()
        thread.join()
