"""제작실(claude.ai 아티팩트)의 클라우드 작업.

아티팩트 화면은 인터넷에 나갈 수 없다. 화면이 '요청'을 남기면 Claude Code 세션(작업자)이 이 모듈을 돌리고,
결과를 아티팩트의 데이터(db)·자산(assets)에 올린다. 생각하는 일(주제 묶기·의견 정리·대본·제목)은 화면이
보는 사람의 Claude(sample)로 하고, 여기서는 인터넷이 필요한 일만 한다. 절차: docs/workroom.md

- collect: 커뮤니티·뉴스 수집 → 최근 24시간 커뮤니티 글(본문·공감 많은 댓글)과 기사(관련 기사는 본문까지)를
  db 문서 크기(256 KiB) 안으로 나눈 JSON 묶음으로.
- sources: 화면이 보낸 대본 + 음성 분석값(길이·쉼 구간) → 자막 맞추기 → 장면별 사진·영상 → timeline.xml.
  timeline.xml의 파일 경로는 `file://localhost/__STUDIO_BASE__/…`로 두고, 화면이 내려받을 때 사용자가 고른
  폴더 경로로 바꾼다(프리미어에서 다시 연결할 필요 없게).

실행: uv run python -m studio_app.workroom collect --out DIR
      uv run python -m studio_app.workroom sources --project 요청.json --out DIR
"""

from __future__ import annotations

import argparse
import asyncio
import html as htmllib
import json
import re
import wave
from datetime import datetime, timedelta
from pathlib import Path
from urllib.parse import quote

from studio_core.config import KST, load_channel, load_settings, now_kst
from studio_core.http import make_client, request
from studio_core.text import tokenize

DOC_LIMIT = 240_000  # db 문서 하나에 넣을 최대 바이트(한도 256 KiB보다 여유 있게)
BASE = "__STUDIO_BASE__"
POST_BODY = 300
COMMENTS = 5
COMMENT_LEN = 150
ARTICLE_SUMMARY = 220
ARTICLE_BODY = 1500
BODY_ARTICLES = 60  # 본문까지 받을 관련 기사 수


# ---- collect ---------------------------------------------------------------------------------


def _iso(dt: datetime) -> str:
    return dt.astimezone(KST).isoformat(timespec="minutes")


def article_body(page: str) -> str:
    """기사 HTML → 본문 글. 연합뉴스는 페이지 안 CONTENT_DATA.BODY, 그 밖은 긴 <p> 문단."""
    m = re.search(r'"BODY":"(.*?)","', page, re.S)
    if m:
        try:
            raw = json.loads('"' + m.group(1) + '"')
        except json.JSONDecodeError:
            raw = ""
        text = htmllib.unescape(re.sub(r"<[^>]+>", "\n", raw))
    else:
        paras = [
            htmllib.unescape(re.sub(r"<[^>]+>", "", p)).strip()
            for p in re.findall(r"<p[^>]*>(.*?)</p>", page, re.S)
        ]
        text = "\n".join(p for p in paras if len(p) >= 30)
    lines = [ln.strip() for ln in text.splitlines() if ln.strip()]
    lines = [ln for ln in lines if "@" not in ln and not ln.startswith("[") and "무단 전재" not in ln]
    return "\n".join(lines)[:ARTICLE_BODY]


def related_articles(posts: list[dict], articles: list[dict], generic: set[str], n: int) -> list[str]:
    """커뮤니티 글 제목과 고유한 말이 2개 이상 겹치는 기사 ID(겹침 많은 순)."""
    from collections import Counter

    from topicradar.cluster import GENERIC_TOKENS

    stop = GENERIC_TOKENS | generic
    df: Counter[str] = Counter()
    for p in posts:
        df.update({t for t in tokenize(p["title"]) if t not in stop})
    scored = []
    for a in articles:
        toks = {t for t in tokenize(a["title"]) if t not in stop}
        shared = [t for t in toks if df[t]]
        if len(shared) >= 2:
            scored.append((sum(min(df[t], 3) for t in shared), a["id"]))
    scored.sort(reverse=True)
    return [i for _s, i in scored[:n]]


def chunk(items: list[dict], key: str, limit: int = DOC_LIMIT) -> list[dict]:
    """목록을 db 문서 크기 안으로 나눈다: [{key: [...]}, ...]."""
    out: list[list[dict]] = [[]]
    size = 0
    for it in items:
        b = len(json.dumps(it, ensure_ascii=False).encode("utf-8")) + 1
        if out[-1] and size + b > limit:
            out.append([])
            size = 0
        out[-1].append(it)
        size += b
    return [{key: part} for part in out if part]


async def collect(out: Path, hours: int = 24) -> dict:
    """수집 → out/meta.json, posts-N.json, articles-N.json. 반환: meta."""
    from topicradar import handoff, pipeline

    settings = load_settings()
    cfg = load_channel(settings)
    now = now_kst()
    with pipeline.open_store(settings) as store:
        last = store.get_meta("last_collect")
        since = now - timedelta(hours=hours + 2)
        if last and datetime.fromisoformat(last) > since:
            since = datetime.fromisoformat(last) - timedelta(hours=1)
        report = await pipeline.run_collect(settings, cfg, store, since)
        start = now - timedelta(hours=hours)
        community = store.signals_between(start, now + timedelta(minutes=1), kinds={"community"})
        news = store.signals_between(
            now - timedelta(hours=hours + 6), now + timedelta(minutes=1), kinds={"domestic", "press"}
        )
    posts = [
        {
            "id": s.id,
            "site": s.metric.get("site", ""),
            "time": _iso(s.published_at),
            "title": s.title,
            "body": (s.summary or "")[:POST_BODY],
            "comments": int(s.metric.get("comments", 0)),
            "votes": int(s.metric.get("votes") or 0),
            "top_comments": [
                {"text": c.get("t", "")[:COMMENT_LEN], "votes": int(c.get("v", 0))}
                for c in s.metric.get("top_comments", [])[:COMMENTS]
                if c.get("t")
            ],
            "url": s.url,
        }
        for s in sorted(community, key=lambda s: s.published_at, reverse=True)
    ]
    articles = [
        {
            "id": s.id,
            "source": s.source.replace("rss:", ""),
            "time": _iso(s.published_at),
            "title": s.title,
            "summary": (s.summary or "")[:ARTICLE_SUMMARY],
            "url": s.url,
        }
        for s in sorted(news, key=lambda s: s.published_at, reverse=True)
        if s.url
    ]
    # 커뮤니티 화제와 겹치는 기사는 본문까지 받는다(대본의 사실 근거)
    want = set(related_articles(posts, articles, set(cfg.clustering.generic_tokens), BODY_ARTICLES))
    sem = asyncio.Semaphore(4)
    async with make_client() as client:

        async def fill(a: dict) -> None:
            async with sem:
                try:
                    resp = await request(client, "GET", a["url"], retries=1)
                    body = article_body(resp.text)
                except Exception:  # 기사 하나 실패는 요약으로 대신
                    body = ""
            if len(body) > len(a["summary"]):
                a["body"] = body

        await asyncio.gather(*(fill(a) for a in articles if a["id"] in want))
    out.mkdir(parents=True, exist_ok=True)
    for kind, items in (("posts", posts), ("articles", articles)):
        for n, doc in enumerate(chunk(items, kind), start=1):
            (out / f"{kind}-{n}.json").write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
    meta = {
        "kind": "studio-raw",
        "version": 1,
        "collected_at": _iso(now),
        "window": [_iso(now - timedelta(hours=hours)), _iso(now)],
        "posts": len(posts),
        "articles": len(articles),
        "article_bodies": sum(1 for a in articles if a.get("body")),
        "post_docs": len(list(out.glob("posts-*.json"))),
        "article_docs": len(list(out.glob("articles-*.json"))),
        "sites": {
            s.name: {
                "enabled": s.enabled,
                "posts": sum(1 for p in posts if p["site"] == s.name),
                "error": report.errors.get(f"community:{s.name}", "") if s.enabled else "꺼 둠",
            }
            for s in cfg.communities
        },
        **handoff.channel_rules(cfg),
    }
    (out / "meta.json").write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")
    return meta


# ---- sources ---------------------------------------------------------------------------------


def silent_wav(path: Path, seconds: float, rate: int = 8000) -> None:
    """길이만 맞춘 무음 wav(timeline.xml의 내레이션 길이용 — 실제 음성은 사용자가 가진 파일)."""
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(b"\x00\x00" * int(seconds * rate))


def rebase_xml(xml: str, project: Path) -> str:
    """timeline.xml 안의 이 서버 경로 → file://localhost/__STUDIO_BASE__ (화면이 사용자 폴더로 바꾼다)."""
    from newsclip.fcpxml import path_url

    return xml.replace(path_url(project), f"file://localhost/{BASE}")


async def sources(req: dict, out: Path, per_scene: int = 2) -> dict:
    """대본 + 음성 분석값 → 자막·장면·자료·timeline.xml. 반환: manifest(올릴 파일 목록과 장면 요약)."""
    from newsclip.autosub import AudioInfo, align, script_sentences
    from newsclip.pipeline import run_project
    from newsclip.srt import write_srt

    settings = load_settings()
    cfg = load_channel(settings)
    pid = re.sub(r"[^0-9A-Za-z_-]", "_", req["id"])
    script = req["script"].replace("\r\n", "\n")
    audio = req["audio"]
    proj = settings.projects_dir / pid
    proj.mkdir(parents=True, exist_ok=True)
    (proj / "script.txt").write_text(script if script.endswith("\n") else script + "\n", encoding="utf-8")
    info = AudioInfo(float(audio["duration"]), [(float(a), float(b)) for a, b in audio.get("silences", [])])
    cues, matched = align(script_sentences(script), info)
    (proj / "narration.srt").write_text(write_srt(cues), encoding="utf-8")
    silent_wav(proj / "narration.wav", info.duration)
    sset, report = await run_project(settings, cfg, proj, per_scene=per_scene)
    xml = rebase_xml((proj / "timeline.xml").read_text(encoding="utf-8"), proj)
    out.mkdir(parents=True, exist_ok=True)
    files: list[dict] = []
    scenes = []
    for sc in sset.scenes:
        names = []
        for a in sc.candidates:
            if not a.local_path:
                continue
            p = Path(a.local_path)
            p = p if p.is_absolute() else settings.root / p
            if not p.exists():
                continue
            rel = p.relative_to(proj).as_posix() if p.is_relative_to(proj) else f"assets/{p.name}"
            if rel not in {f["name"] for f in files}:
                files.append({"name": rel, "path": str(p), "media": a.media})
            names.append(rel)
        chosen = Path(sc.chosen.local_path).name if sc.chosen and sc.chosen.local_path else None
        scenes.append(
            {
                "id": sc.id,
                "start": round(sc.start_sec, 2),
                "end": round(sc.end_sec, 2),
                "text": sc.text[:80],
                "topic": sc.topic,
                "chosen": next((n for n in names if chosen and n.endswith(chosen)), None),
                "attribution": sc.chosen.attribution_text if sc.chosen else "",
                "files": names,
            }
        )
    (out / "timeline.xml.txt").write_text(xml, encoding="utf-8")
    (out / "narration.srt.txt").write_text(
        (proj / "narration.srt").read_text(encoding="utf-8"), encoding="utf-8"
    )
    manifest = {
        "project": pid,
        "made_at": _iso(now_kst()),
        "scenes": scenes,
        "files": files,
        "srt_matched": matched,
        "cues": len(cues),
        "report": {
            "scenes": report.scenes,
            "with_asset": report.with_asset,
            "downloaded": report.downloaded,
            "by_source": report.by_source,
            "skipped_sources": report.skipped_sources,
            "errors": report.errors + report.xml_errors,
        },
        "audio_name": "narration" + (audio.get("ext") or ".wav"),
        "base_token": f"file://localhost/{BASE}",
    }
    (out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    return manifest


def folder_url(folder: str) -> str:
    """사용자 폴더 → file://localhost/… (화면의 JS와 같은 규칙, 테스트용)."""
    p = folder.replace("\\", "/").rstrip("/")
    if not p.startswith("/"):
        p = "/" + p
    return "file://localhost" + quote(p, safe="/:")


def main() -> None:
    """CLI."""
    ap = argparse.ArgumentParser(prog="studio_app.workroom")
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("collect")
    c.add_argument("--out", type=Path, required=True)
    c.add_argument("--hours", type=int, default=24)
    s = sub.add_parser("sources")
    s.add_argument("--project", type=Path, required=True)
    s.add_argument("--out", type=Path, required=True)
    s.add_argument("--per-scene", type=int, default=2)
    a = ap.parse_args()
    if a.cmd == "collect":
        meta = asyncio.run(collect(a.out, a.hours))
        print(
            json.dumps(
                {k: meta[k] for k in ("posts", "articles", "article_bodies", "sites")}, ensure_ascii=False
            )
        )
    else:
        req = json.loads(a.project.read_text(encoding="utf-8"))
        if "script" not in req and isinstance(req.get("data"), dict):  # ArtifactData out_dir 파일이 감싼 경우
            req = req["data"]
        man = asyncio.run(sources(req, a.out, a.per_scene))
        print(json.dumps({"files": len(man["files"]), **man["report"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
