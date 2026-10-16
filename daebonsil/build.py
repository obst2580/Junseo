"""claude.ai 아티팩트 페이지 만들기.

- 대본실: page.html + 예시(가상) 주제판 → dist/daebonsil.html
- 제작실: workroom.html + worker.json(요청을 보낼 작업자 세션) → dist/workroom.html


예시는 시연 데이터(docs/demo/studio-주제판-예시.json, 가상의 정당·인물)에서 주소를 example.com으로 바꿔 넣는다
(실제 커뮤니티·언론사 주소로 이어지지 않게).

실행: uv run python scripts/demo.py && uv run python daebonsil/build.py
"""

from __future__ import annotations

import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
SAMPLE = ROOT / "docs" / "demo" / "studio-주제판-예시.json"
OUT = HERE / "dist" / "daebonsil.html"


def sanitize(board: dict) -> dict:
    """예시 주제판: 기사 주소는 example.com, 커뮤니티 글 주소는 지운다."""
    n = 0
    for t in board["topics"]:
        for a in t.get("articles", []):
            n += 1
            a["url"] = f"https://example.com/demo/article-{n}"
        for x in t.get("post_links", []):
            x["url"] = ""
    board["notes"] = [*board.get("notes", []), "예시(가상) 주제판: 정당·인물·기사·글은 모두 지어낸 것입니다"]
    return board


def build() -> Path:
    page = (HERE / "page.html").read_text(encoding="utf-8")
    board = sanitize(json.loads(SAMPLE.read_text(encoding="utf-8")))
    blob = json.dumps(board, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    marker = "/*__SAMPLE__*/null"
    assert page.count(marker) == 1, "page.html에 예시 자리가 없습니다"
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(page.replace(marker, blob), encoding="utf-8")
    return OUT


def build_workroom() -> Path:
    """제작실 페이지: workroom.html + 작업자 기본값(worker.json: 요청을 보낼 세션·아티팩트 주소)."""
    page = (HERE / "workroom.html").read_text(encoding="utf-8")
    worker = json.loads((HERE / "worker.json").read_text(encoding="utf-8"))
    marker = "/*__WORKER__*/{}"
    assert page.count(marker) == 1, "workroom.html에 작업자 자리가 없습니다"
    out = HERE / "dist" / "workroom.html"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(page.replace(marker, json.dumps(worker, ensure_ascii=False)), encoding="utf-8")
    return out


if __name__ == "__main__":
    for out in (build(), build_workroom()):
        print(f"{out} ({out.stat().st_size / 1024:.0f} KB)")
