"""제작실(claude.ai 아티팩트)의 클라우드 작업.

아티팩트 화면은 인터넷에 나갈 수 없다. 화면이 '요청'을 남기면 Claude Code 세션(작업자)이 이 모듈을 돌리고,
결과를 아티팩트의 데이터(db)·자산(assets)에 올린다. 생각하는 일(주제 묶기·의견 정리·대본·제목)은 화면이
보는 사람의 Claude(sample)로 하고, 여기서는 인터넷이 필요한 일만 한다. 절차: docs/workroom.md

- collect: 커뮤니티·뉴스 수집 → 최근 24시간 커뮤니티 글(본문·공감 많은 댓글)과 기사(관련 기사는 본문까지)를
  db 문서 크기(256 KiB) 안으로 나눈 JSON 묶음으로.
- sources: 화면이 보낸 대본 + 음성 분석값(길이·쉼 구간) → 자막 맞추기 → 장면별 사진·영상 → timeline.xml.
  timeline.xml의 파일 경로는 `file://localhost/__STUDIO_BASE__/…`로 두고, 화면이 내려받을 때 사용자가 고른
  폴더 경로로 바꾼다(프리미어에서 다시 연결할 필요 없게).

- render: 쇼츠 자료(result) + 음성 + 공통 배경음악 → 완성 mp4(자막 효과·강조 자막·효과음·컷 편집). 작업자가
  자산을 받아 프로젝트 폴더를 다시 꾸미고(render-ids로 받을 목록), 렌더한 mp4를 제작실에 올린다.

실행: uv run python -m studio_app.workroom collect --out DIR
      uv run python -m studio_app.workroom sources --project 요청.json --out DIR
"""

from __future__ import annotations

import argparse
import asyncio
import hashlib
import html as htmllib
import json
import os
import re
from datetime import datetime, timedelta
from pathlib import Path
from urllib.parse import quote

from studio_core.config import KST, load_channel, load_settings, now_kst
from studio_core.http import make_client, request
from studio_core.text import tokenize

DOC_LIMIT = 240_000  # db 문서 하나에 넣을 최대 바이트(한도 256 KiB보다 여유 있게)
BASE = "__STUDIO_BASE__"
POST_BODY = 300
OPINION_BODY = 600  # 사설·칼럼(kind: opinion)은 주장이 본문에 있어 조금 길게
COMMENTS = 5
COMMENT_LEN = 150
ARTICLE_SUMMARY = 220
ARTICLE_BODY = 1500
BODY_ARTICLES = 60  # 본문까지 받을 관련 기사 수
VIDEO_LINKS = 3  # 장면마다 남길 유튜브 영상 후보 수
RENDER_CRF = 20  # 완성본 화질 — 1분 1080×1920이 10MB 안팎
RENDER_MAX_BYTES = 14 * 1024 * 1024  # 제작실 자산으로 올릴 수 있게(한 파일 15MB 안쪽)


# ---- collect ---------------------------------------------------------------------------------


def _iso(dt: datetime) -> str:
    return dt.astimezone(KST).isoformat(timespec="minutes")


_BODY_END = re.compile(r"저작권자|송고$|무단 전재|재배포 금지")


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
        text = "\n".join(p for p in paras if len(p) >= 30 or _BODY_END.search(p))
    lines: list[str] = []
    for ln in (x.strip() for x in text.splitlines()):
        if _BODY_END.search(ln):  # 본문 끝(뒤는 관련 기사·광고)
            break
        if ln and "@" not in ln and not ln.startswith("["):
            lines.append(ln)
    return "\n".join(lines)[:ARTICLE_BODY]


# 사설·칼럼 본문 칸(매체마다 다름): 한국경제 #articletxt, 동아일보 .news_view, 그 밖의 흔한 이름
OPINION_BODY_SELECTOR = "#articletxt, #articleBody, #article-view-content-div, .news_view, .article_txt, .article-body, .article_body"
_OG_DESC = re.compile(r"<meta[^>]+property=[\"']og:description[\"'][^>]+content=[\"']([^\"']*)[\"']", re.I)


def opinion_body(page: str) -> str:
    """사설·칼럼 페이지 → 본문 앞부분. 본문 칸이 있으면 그 글(메뉴·추천 기사가 섞이지 않게), 없으면(조선일보처럼
    화면에서 그리는 곳) og:description(요지), 그것도 없으면 기사 본문 찾기(article_body)."""
    from topicradar.collectors.htmltree import parse_html

    try:
        node = parse_html(page).select_one(OPINION_BODY_SELECTOR)
    except Exception:  # 깨진 HTML
        node = None
    if node is not None:
        text = " ".join(node.text().split())
        if len(text) >= 100:
            return text[:ARTICLE_BODY]
    m = _OG_DESC.search(page)
    if m and len(m.group(1).strip()) >= 40:
        return htmllib.unescape(m.group(1)).strip()
    return article_body(page)


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


async def collect(out: Path, hours: int | None = None) -> dict:
    """수집 → out/meta.json, posts-N.json, articles-N.json. 반환: meta. hours를 비우면 채널 설정(board.window_hours)."""
    from topicradar import handoff, pipeline

    settings = load_settings()
    cfg = load_channel(settings)
    hours = hours or cfg.board.window_hours
    now = now_kst()
    with pipeline.open_store(settings) as store:
        last = store.get_meta("last_collect")
        since = now - timedelta(hours=hours + 2)
        if last and datetime.fromisoformat(last) > since:
            since = datetime.fromisoformat(last) - timedelta(hours=1)
        report = await pipeline.run_collect(settings, cfg, store, since)
        start = now - timedelta(hours=hours)
        community = store.signals_between(start, now + timedelta(minutes=1), kinds={"community"})
        # 지금 설정에 켜 둔 게시판 글만(커뮤니티를 안 쓰는 채널·꺼 둔 게시판의 예전 글이 섞이지 않게)
        sites = {c.name for c in cfg.communities if c.enabled}
        community = [s for s in community if s.metric.get("site", "") in sites]
        # 사설·칼럼 피드(kind: opinion — 보수 채널의 시선)는 게시판 글처럼 주제 묶기·의견 정리에 쓴다
        opinion_feeds = [f.name for f in cfg.sources.rss if f.kind == "opinion"]
        opinions = (
            store.signals_between(start, now + timedelta(minutes=1), kinds={"opinion"})
            if opinion_feeds
            else []
        )
        opinions = [s for s in opinions if s.metric.get("feed", "") in opinion_feeds]
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
    ] + [
        {
            "id": s.id,
            "site": s.metric.get("feed", ""),
            "kind": "opinion",
            "time": _iso(s.published_at),
            "title": s.title,
            "body": (s.summary or "")[:OPINION_BODY],
            "comments": 0,
            "votes": 0,
            "top_comments": [],
            "url": s.url,
        }
        for s in sorted(opinions, key=lambda s: s.published_at, reverse=True)
    ]
    articles = [
        {
            "id": s.id,
            "source": s.source.replace("rss:", ""),
            "time": _iso(s.published_at),
            "title": s.title,
            "summary": (s.summary or "")[:ARTICLE_SUMMARY],
            "url": s.url,
            **({"kind": "press"} if s.kind == "press" else {}),  # 공공기관 보도자료(건강 채널 근거의 첫째)
        }
        for s in sorted(news, key=lambda s: s.published_at, reverse=True)
        if s.url
    ]
    # 커뮤니티 화제와 겹치는 기사는 본문까지 받는다(대본의 사실 근거). 증시·건강 채널은 주제를 기사로 정하므로
    # 최근 기사도 본문을 받는다 — 숫자(지수·수급·환율, 기준값·연구 규모)는 본문에 있다.
    related = related_articles(posts, articles, set(cfg.clustering.generic_tokens), BODY_ARTICLES)
    if cfg.channel.profile in ("stock", "health"):
        want = set(related[: BODY_ARTICLES // 3]) | {a["id"] for a in articles[:BODY_ARTICLES]}
        want |= {a["id"] for a in articles if a.get("kind") == "press"}  # 보도자료는 오래돼도 본문까지
    else:
        want = set(related)
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

        async def fill_opinion(p: dict) -> None:
            """사설·칼럼은 RSS 요약이 짧아 본문 앞부분을 읽는다(주장은 본문에 있다)."""
            async with sem:
                try:
                    resp = await request(client, "GET", p["url"], retries=1)
                    body = opinion_body(resp.text)
                except Exception:  # 하나 실패는 요약으로 대신
                    body = ""
            if len(body) > len(p["body"]):
                p["body"] = body[:OPINION_BODY]

        await asyncio.gather(
            *(fill(a) for a in articles if a["id"] in want),
            *(fill_opinion(p) for p in posts if p.get("kind") == "opinion"),
        )
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
            **{
                s.name: {
                    "enabled": s.enabled,
                    "posts": sum(1 for p in posts if p["site"] == s.name),
                    "error": report.errors.get(f"community:{s.name}", "") if s.enabled else "꺼 둠",
                }
                for s in cfg.communities
            },
            **{  # 사설·칼럼 피드도 화면에서 사이트처럼 글 수를 보고 끌 수 있게
                name: {
                    "enabled": True,
                    "posts": sum(1 for p in posts if p["site"] == name),
                    "error": "" if any(p["site"] == name for p in posts) else "최근 24시간 글 없음",
                    "kind": "opinion",
                }
                for name in opinion_feeds
            },
        },
        **handoff.channel_rules(cfg),
    }
    (out / "meta.json").write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")
    return meta


# ---- sources ---------------------------------------------------------------------------------


def make_voice(req: dict, out: Path, voice: dict | None = None, client=None) -> dict:  # noqa: ANN001
    """대본을 타입캐스트 API로 읽어 out/narration.wav와 out/voice.json(projects/<ID>.audio에 쓸 값, asset_id 빼고)을
    만든다. voice는 화면이 요청에 담은 {name, tempo, remove_silence_ms} — 비운 칸은 채널 설정(voice:)을 쓴다.
    낱말 시각(words)도 함께 저장해, 자료를 모을 때 음성을 다시 듣지 않고 그 시각에 자막·장면을 맞춘다."""
    from newsclip.autosub import analyze, narration_text
    from newsclip.typecast_tts import TypecastError, find_voice, synthesize

    cfg = load_channel(load_settings()).voice
    v = voice or {}
    name = str(v.get("name") or cfg.name).strip()
    tempo = float(v.get("tempo") or cfg.tempo)
    silence = v["remove_silence_ms"] if "remove_silence_ms" in v else cfg.remove_silence_ms
    voice_id = cfg.voice_id if cfg.voice_id and name == cfg.name else ""
    if not voice_id:
        if not name:
            raise TypecastError("목소리 이름이 없습니다(channel.yaml의 voice.name이나 화면에서 정해 주세요).")
        hit = find_voice(name, model=cfg.model, client=client)
        if hit is None:
            raise TypecastError(f"타입캐스트 목소리 '{name}'을(를) 찾지 못했습니다.")
        voice_id = hit["voice_id"]
    text = narration_text(req["script"])
    out.mkdir(parents=True, exist_ok=True)
    speech = synthesize(
        text,
        voice_id,
        out / "narration.wav",
        model=cfg.model,
        tempo=tempo,
        remove_silence_ms=silence,
        client=client,
        per_sentence=cfg.per_sentence,
    )
    info = analyze(speech.path, min_silence=0.15)
    chars = len(text.replace("\n", ""))
    # 제작실 자산은 wav를 받지 않아서 올릴 파일은 오디오만 든 mp4(AAC 192kbps)로 — 완성본 렌더도 이 파일을 받아 쓴다
    upload = out / "narration.mp4"
    _ffmpeg(
        ["-i", str(speech.path), "-vn", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", str(upload)]
    )
    audio = {
        "source": "typecast",
        "voice": name,
        "voice_id": voice_id,
        "tempo": tempo,
        "remove_silence_ms": silence,
        "name": "narration.mp4",
        "ext": ".mp4",
        "size": upload.stat().st_size,
        "duration": speech.duration,
        "silences": [[round(a, 2), round(b, 2)] for a, b in info.silences],
        "chars": chars,
        "cpm": round(chars / (speech.duration / 60)) if speech.duration else 0,
        "credits": len(text),
        # 문장마다 읽은 채널: 같은 말을 두 번 읽어 다시 읽은 문장 수, 다시 읽어도 남은 의심 구간
        **({"retried": speech.retried} if speech.retried else {}),
        **({"warnings": speech.warnings} if speech.warnings else {}),
        "words": [[w, s, e] for w, s, e in speech.words],
        "analyzed_at": _iso(now_kst()),
    }
    (out / "voice.json").write_text(json.dumps(audio, ensure_ascii=False), encoding="utf-8")
    return audio


def voice_doc(voice_json: Path, asset_id: str) -> dict:
    """올린 음성의 자산 ID를 붙여 projects/<ID>에 쓸 값({audio, status})을 같은 폴더 update.json에 쓴다."""
    audio = json.loads(voice_json.read_text(encoding="utf-8"))
    doc = {"audio": {**audio, "asset_id": asset_id}, "status": "voice"}
    (voice_json.parent / "update.json").write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
    return doc


def _ffmpeg(args: list[str]) -> None:
    import subprocess

    proc = subprocess.run(["ffmpeg", "-loglevel", "error", "-y", *args], capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"ffmpeg 실패: {proc.stderr[-600:]}")


# ---- render(완성 mp4) ------------------------------------------------------------------------


def render_ids(req: dict, bgm: dict | None = None) -> dict[str, str]:
    """완성 영상에 필요한 자산 → {자산 ID: 쓰임}. 작업자가 하나씩 Artifact read(path=ID)로 받는다.
    쓰임은 프로젝트 폴더 안 이름(assets/…, captions/…, overlays/…, narration.srt) 또는 narration·bgm."""
    res = req.get("result") or {}
    if not res.get("files") or not res.get("scenes"):
        raise ValueError("자료(result)가 없습니다 — 먼저 [자료 모으기]를 끝내 주세요.")
    audio = req.get("audio") or {}
    if not audio.get("asset_id"):
        raise ValueError("올린 음성이 없습니다 — 음성 칸에서 음성을 올리거나 타입캐스트로 만들어 주세요.")
    ids = {
        f["id"]: f["name"]
        for f in res["files"]
        if f["name"].startswith(("assets/", "captions/", "overlays/"))
    }
    if res.get("srt_id"):
        ids[res["srt_id"]] = "narration.srt"
    ids[audio["asset_id"]] = "narration"
    if bgm and bgm.get("asset_id"):
        ids[bgm["asset_id"]] = "bgm"
    return ids


def _fetched(fetched: Path, asset_id: str, use: str) -> Path:
    hits = sorted(fetched.glob(f"{asset_id}.*")) or (
        [fetched / asset_id] if (fetched / asset_id).exists() else []
    )
    if not hits:
        raise FileNotFoundError(f"받은 파일이 없습니다: {asset_id} ({use}) — Artifact read로 받아 주세요")
    return hits[0]


def make_render(req: dict, fetched: Path, out: Path, bgm: dict | None = None, crf: int = RENDER_CRF) -> dict:
    """받은 자산(fetched/<ID>.<확장자>)으로 프로젝트 폴더를 다시 꾸미고 완성 mp4(out/final.mp4)를 만든다.
    자막은 자료를 모을 때 만든 SRT 시각 그대로, 훅 조각은 낱말 시각(타입캐스트, 아니면 음성 인식)에 맞춘다.
    out/render.json(projects/<ID>.render에 쓸 값, asset_id 빼고)을 쓰고 그 내용을 돌려준다."""
    import shutil

    from newsclip.shorts_render import probe_duration, refresh_design, render_short

    ids = render_ids(req, bgm)
    proj = out / "project"
    for aid, use in ids.items():
        src = _fetched(fetched, aid, use)
        if use in ("narration", "bgm"):
            continue
        dst = proj / use
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
    # 자료를 모은 뒤 바뀐 디자인(바탕·상단 제목)을 지금 디자인으로 맞춘다
    settings = load_settings()
    style = load_channel(settings).shorts.style
    bold = settings.data_dir / "fonts" / "Pretendard-ExtraBold.otf"  # 자료 모을 때 받아 둔 상단 제목 폰트
    refreshed = refresh_design(proj, str(req.get("thumb") or ""), style, str(bold) if bold.exists() else None)
    audio = req["audio"]
    narration = out / "narration.wav"  # 자막 분석·렌더는 wav로
    _ffmpeg(
        [
            "-i",
            str(_fetched(fetched, audio["asset_id"], "narration")),
            "-ac",
            "1",
            "-ar",
            "48000",
            str(narration),
        ]
    )
    bgm_path = _fetched(fetched, bgm["asset_id"], "bgm") if bgm and bgm.get("asset_id") else None
    words = audio.get("words") if audio.get("source") == "typecast" else None
    srt = proj / "narration.srt"
    res = req["result"]
    project = {
        "script": req["script"],
        "scenes": res["scenes"],
        "hook": res.get("hook"),
        "emphasis": res.get("emphasis"),
    }
    final = out / "final.mp4"
    render_short(
        project,
        proj,
        narration,
        final,
        srt=srt if srt.exists() else None,
        bgm=bgm_path,
        words=words,
        crf=crf,
        style=style,
    )
    if final.stat().st_size > RENDER_MAX_BYTES:  # 한도를 넘으면 화면만 조금 더 압축
        smaller = out / "final-small.mp4"
        _ffmpeg(
            [
                "-i",
                str(final),
                "-c:v",
                "libx264",
                "-crf",
                str(crf + 4),
                "-preset",
                "medium",
                "-c:a",
                "copy",
                "-movflags",
                "+faststart",
                str(smaller),
            ]
        )
        smaller.replace(final)
    doc = {
        "file": final.name,
        "size": final.stat().st_size,
        "duration": round(probe_duration(final), 2),
        "bgm": (bgm or {}).get("asset_id"),
        "timing": "typecast" if words else "srt+asr",
        "redesigned": refreshed,  # 지금 디자인으로 다시 합성한 사진 수
        "replaced": (req.get("render") or {}).get("asset_id"),  # 화면이 지울 지난 완성본
        "made_at": _iso(now_kst()),
    }
    (out / "render.json").write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
    return doc


def render_doc(render_json: Path, asset_id: str) -> dict:
    """올린 완성본 자산 ID를 붙여 projects/<ID>에 쓸 값({render})을 같은 폴더 update.json에 쓴다."""
    r = json.loads(render_json.read_text(encoding="utf-8"))
    doc = {"render": {**r, "asset_id": asset_id}}
    (render_json.parent / "update.json").write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
    return doc


def rebase_xml(xml: str, project: Path) -> str:
    """timeline.xml 안의 이 서버 경로 → file://localhost/__STUDIO_BASE__ (화면이 사용자 폴더로 바꾼다)."""
    from newsclip.fcpxml import path_url

    return xml.replace(path_url(project), f"file://localhost/{BASE}")


def _prepare(req: dict, voice: Path | None = None) -> tuple:
    """프로젝트 폴더에 대본·장면 시각(말 덩어리마다)을 쓴다. 음성 분석값이 있으면 그 쉼에 맞추고, 없으면
    대본 글자 수와 분당 글자 수로 어림한다. 음성·자막은 사용자가 직접 넣으므로 timeline.xml에 음성 트랙은 없다.

    voice(화면이 올린 음성 인식용 webm)가 있으면 음성을 듣고(newsclip.asr) 경계마다 실제로 들린 시각에 맞춘다.
    듣지 못하면(프로그램 없음 등) 쉼만으로 맞추고 그 이유를 asr["error"]에 남긴다."""
    from newsclip.autosub import (
        AudioInfo,
        align,
        analyze,
        asr_boundaries,
        narration_text,
        script_phrases,
        units_of,
    )
    from newsclip.srt import write_srt

    settings = load_settings()
    pid = re.sub(r"[^0-9A-Za-z_-]", "_", req["id"])
    script = req["script"].replace("\r\n", "\n")
    audio = req.get("audio") or {}
    proj = settings.projects_dir / pid
    proj.mkdir(parents=True, exist_ok=True)
    (proj / "script.txt").write_text(script if script.endswith("\n") else script + "\n", encoding="utf-8")
    if audio.get("duration"):
        info = AudioInfo(
            float(audio["duration"]), [(float(a), float(b)) for a, b in audio.get("silences", [])]
        )
    elif voice:
        info = analyze(voice, min_silence=0.15)
    else:
        cpm = load_channel(settings).script.chars_per_minute
        info = AudioInfo(round(len(narration_text(script).replace("\n", "")) / cpm * 60, 2), [])
    phrases = script_phrases(script)
    words, asr = None, {}
    tc_words = audio.get("words") if audio.get("source") == "typecast" else None
    if tc_words:
        # 타입캐스트가 음성을 만들며 준 낱말 시각 — 다시 들을 필요가 없다
        words = [(str(w[0]), float(w[1]), float(w[2])) for w in tc_words]
        texts = [t for sent in phrases for ph in sent for t, _k in units_of(ph)]
        heard = asr_boundaries(texts, words)[1:-1]
        asr = {
            "source": "typecast",
            "words": len(words),
            "heard": sum(h is not None for h in heard),
            "bounds": len(heard),
        }
    elif voice:
        from newsclip.asr import transcribe

        try:
            words = transcribe(voice, cache_dir=proj)
            texts = [t for sent in phrases for ph in sent for t, _k in units_of(ph)]
            heard = asr_boundaries(texts, words)[1:-1]
            asr = {"words": len(words), "heard": sum(h is not None for h in heard), "bounds": len(heard)}
        except Exception as e:  # noqa: BLE001 - 듣지 못하면 쉼만으로 맞춘다
            asr = {"error": f"{type(e).__name__}: {e}"[:300]}
    cues, matched = align(phrases, info, words=words)
    (proj / "narration.srt").write_text(write_srt(cues), encoding="utf-8")
    (proj / "narration.wav").unlink(missing_ok=True)  # 예전 결과의 무음 wav가 남아 음성 트랙이 생기지 않게
    return settings, pid, proj, script, cues, matched, info, asr, words


def plan_scenes(req: dict, out: Path, voice: Path | None = None) -> list[dict]:
    """장면 나누기만(자료 검색 없이) → out/scenes-plan.json. 작업자 Claude가 이걸 읽고 장면별 검색어를 쓴다.
    sources와 같은 규칙으로 나누므로 장면 ID가 그대로 맞는다."""
    from newsclip.scenes import build_scenes

    _settings, _pid, _proj, script, cues, _matched, _info, _asr, _words = _prepare(req, voice)
    plan = [
        {
            "id": sc.id,
            "start": round(sc.start_sec, 2),
            "end": round(sc.end_sec, 2),
            "topic": sc.topic,
            "text": sc.text,
        }
        for sc in build_scenes(cues, script, phrases=True)
    ]
    out.mkdir(parents=True, exist_ok=True)
    (out / "scenes-plan.json").write_text(json.dumps(plan, ensure_ascii=False, indent=1), encoding="utf-8")
    return plan


async def sources(
    req: dict,
    out: Path,
    per_scene: int = 2,
    queries: dict | None = None,
    voice: Path | None = None,
    slides: dict | None = None,
) -> dict:
    """대본 + 음성 분석값 → 자막·장면·자료·timeline.xml. 반환: manifest(올릴 파일 목록과 장면 요약).
    queries: 장면별 사진 검색어·주제별 유튜브 검색어(newsclip.pipeline.apply_queries 형식).
    세로 쇼츠(format "shorts")면 1080x1920 — 사진은 세로 화면에 맞추고, 썸네일 문구(thumb)를 상단 제목으로 깔고,
    자막 줄을 짧게(14자)."""
    from newsclip import vertical
    from newsclip.autosub import SUB_LINE
    from newsclip.fcpxml import caption_for
    from newsclip.pipeline import run_project

    settings, pid, proj, _script, cues, matched, info, asr, words = _prepare(req, voice)
    cfg = load_channel(settings)
    tall = is_vertical_format(req)
    size = vertical.SIZE if tall else (1920, 1080)
    # 클라우드에서는 유튜브 영상 받기가 막혀(403) 영상은 링크 후보로만 남긴다(사용자가 직접 받아 넣음)
    sset, report = await run_project(
        settings,
        cfg,
        proj,
        per_scene=per_scene,
        videos=False,
        queries=queries,
        phrases=True,
        slides=slides,
        size=size,
        headline=str(req.get("thumb") or "") if tall else "",
        style=cfg.shorts.style if tall else "default",
    )
    xml = rebase_xml((proj / "timeline.xml").read_text(encoding="utf-8"), proj)
    out.mkdir(parents=True, exist_ok=True)
    files: list[dict] = []
    scenes = []
    for sc in sset.scenes:
        names = []
        # 앞뒤 장면 사진을 이어 쓴 장면(sc.borrowed)은 그 사진 하나만
        for a in [sc.chosen] if sc.borrowed and sc.chosen else sc.candidates:
            if not a.local_path:
                continue
            p = Path(a.local_path)
            p = p if p.is_absolute() else settings.root / p
            if not p.exists():
                continue
            rel = p.relative_to(proj).as_posix() if p.is_relative_to(proj) else f"assets/{p.name}"
            if rel not in {f["name"] for f in files}:
                files.append({"name": rel, "path": str(p), "media": a.media, "sha": _sha(p)})
            names.append(rel)
        chosen = Path(sc.chosen.local_path).name if sc.chosen and sc.chosen.local_path else None
        scenes.append(
            {
                "id": sc.id,
                "start": round(sc.start_sec, 2),
                "end": round(sc.end_sec, 2),
                "text": sc.text[:80],
                "topic": sc.topic,
                "query": sc.query,
                "chosen": next((n for n in names if chosen and n.endswith(chosen)), None),
                "attribution": caption_for(sc, sc.chosen) if sc.chosen else "",
                "photo_title": (sc.chosen.title or "")[:80] if sc.chosen else "",
                "files": names,
                "videos": video_links(sc.candidates),
                **({"borrowed": sc.borrowed} if sc.borrowed else {}),
                # 수치 카드 장면: 카드 컷에서 다가갈 자리(강조한 줄·숫자)
                **({"card_focus": list(sc.chosen.focus)} if sc.chosen and sc.chosen.focus else {}),
            }
        )
    # timeline.xml이 가리키는 그 밖의 파일(출처 표시 captions/, 상단 제목 overlays/, 자막 subtitles/의 글자 그림)도 같이 올린다
    for folder in ("captions", "overlays", "subtitles"):
        for png in sorted((proj / folder).glob("*.png")):
            rel = f"{folder}/{png.name}"
            if rel in xml and rel not in {f["name"] for f in files}:
                files.append({"name": rel, "path": str(png), "media": "image", "sha": _sha(png)})
    # 효과음(화제 전환·질문 자리, A2 트랙)
    for wav in sorted((proj / "sfx").glob("*.wav")):
        rel = f"sfx/{wav.name}"
        if rel in xml and rel not in {f["name"] for f in files}:
            files.append({"name": rel, "path": str(wav), "media": "audio", "sha": _sha(wav)})
    (out / "timeline.xml.txt").write_text(xml, encoding="utf-8")
    # 프리미어 캡션용 자막(한 줄 20자 안팎 — 세로 쇼츠는 14자, 장면 시각과 같은 시간축 — 음성을 올렸으면 그 음성에 맞춤)
    from newsclip.autosub import subtitle_cues
    from newsclip.srt import write_srt

    limit = vertical.SUB_LINE if tall else SUB_LINE
    (out / "narration.srt.txt").write_text(
        write_srt(subtitle_cues(cues, limit=limit, info=info, words=words)), encoding="utf-8"
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
            "video_scenes": sum(1 for x in scenes if x["videos"]),
            "borrowed": report.borrowed,
            "dropped_thumbs": report.dropped_thumbs,
            "skipped_sources": report.skipped_sources,
            "errors": report.errors + report.xml_errors,
        },
        "audio_name": "narration" + ((req.get("audio") or {}).get("ext") or ".wav"),
        "size": list(size),
        "timing": (
            ("voice+typecast" if asr.get("source") == "typecast" else "voice+asr")
            if asr.get("words")
            else "voice"
            if info.silences
            else "estimated"
        ),
        "asr": asr,
        "base_token": f"file://localhost/{BASE}",
        # 무료 소재 출처(받은 때·사이트·소재 번호·작가·주소) — 화면에서 설명란용으로 복사, 저작권 신고 이의 제기용
        "credits": _credits(proj),
        # 차트판 훅(널스윤서): 첫 문장에서 동그라미 칠 핵심 말·줄 나눔(queries.json "hook") — 완성 영상 렌더가 쓴다
        **({"hook": _hook_spec(queries)} if _hook_spec(queries) else {}),
        # 강조 자막 문구(널스윤서): 긴 자막 안에서 잘라 정치 채널과 같은 강조 효과로 띄울 말(queries.json "emphasis")
        **({"emphasis": _emphasis(queries)} if _emphasis(queries) else {}),
    }
    (out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    return manifest


def _emphasis(queries: dict | None) -> list[str]:
    """queries.json의 "emphasis" → 강조 문구 목록(빈 것·겹치는 것 빼고, 많아야 8개)."""
    items = (queries or {}).get("emphasis")
    if not isinstance(items, list):
        return []
    return list(dict.fromkeys(str(x).strip() for x in items if str(x).strip()))[:8]


def _hook_spec(queries: dict | None) -> dict:
    """queries.json의 "hook" → {key, lines}(빈 값은 뺀다). 없으면 빈 dict."""
    h = (queries or {}).get("hook")
    if not isinstance(h, dict):
        return {}
    out: dict = {}
    if str(h.get("key") or "").strip():
        out["key"] = str(h["key"]).strip()
    lines = [str(x).strip() for x in h.get("lines") or [] if str(x).strip()][:2]
    if lines:
        out["lines"] = lines
    return out


def _credits(proj: Path) -> list[str]:
    """프로젝트의 credits.txt(무료 소재 출처) 줄들. 없으면 빈 목록."""
    path = proj / "credits.txt"
    if not path.exists():
        return []
    return [ln for ln in path.read_text(encoding="utf-8").splitlines()[1:] if ln.strip()]


def is_vertical_format(req: dict) -> bool:
    """세로 화면으로 만들 프로젝트인가(형식 shorts = 세로 쇼츠)."""
    from topicradar.models import FORMATS

    spec = FORMATS.get(str(req.get("format") or ""))
    return bool(spec and spec.vertical)


def _sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


BATCH = 25  # Artifact 자산 올리기 한 번에 25개까지
TEXTS = {"timeline": "timeline.xml.txt", "srt": "narration.srt.txt"}


def upload_plan(manifest: dict, previous: dict | None, out: Path) -> dict:
    """올릴 것만 고른다. 지난 결과에 내용(sha256)이 같은 자산이 있으면 그 ID를 그대로 쓰고(다시 올리지 않음),
    나머지만 25개씩 묶는다. 지난 결과에서 이번에 안 쓰는 자산 ID는 cleanup — 화면이 열릴 때 지운다
    (작업자가 하나씩 지우면 수백 번 호출이라 오래 걸림). 음성 인식용 음성(audio.asset_id)은 절대 넣지 않는다."""
    prev = (previous or {}).get("result") or {}
    keep_audio = ((previous or {}).get("audio") or {}).get("asset_id")
    by_sha = {f["sha"]: f["id"] for f in prev.get("files", []) if f.get("sha") and f.get("id")}
    for key in TEXTS:  # timeline·srt도 내용이 같으면 그대로
        if prev.get(f"{key}_sha") and prev.get(f"{key}_id"):
            by_sha.setdefault(prev[f"{key}_sha"], prev[f"{key}_id"])
    reuse: dict[str, str] = {}
    todo: list[str] = []
    for f in manifest["files"]:
        if f.get("sha") in by_sha:
            reuse[f["name"]] = by_sha[f["sha"]]
        else:
            todo.append(f["path"])
    texts, reuse_texts = [], {}
    for key, name in TEXTS.items():
        path = out / name
        sha = _sha(path)
        if sha in by_sha:
            reuse_texts[key] = by_sha[sha]
        else:
            texts.append(str(path))
    used = set(reuse.values()) | set(reuse_texts.values())
    old = [f.get("id") for f in prev.get("files", [])] + [prev.get("timeline_id"), prev.get("srt_id")]
    old += list(prev.get("cleanup") or [])  # 화면이 아직 못 지운 것
    cleanup = sorted({x for x in old if x and x not in used and x != keep_audio})
    plan = {
        "reuse": reuse,
        "batches": [todo[i : i + BATCH] for i in range(0, len(todo), BATCH)],
        "texts": texts,
        "reuse_texts": reuse_texts,
        "cleanup": cleanup,
    }
    (out / "upload-plan.json").write_text(json.dumps(plan, ensure_ascii=False, indent=1), encoding="utf-8")
    return plan


def result_doc(manifest: dict, plan: dict, uploaded: dict, previous: dict | None, out: Path) -> dict:
    """projects/<ID>에 update로 쓸 문서: status·result(자산 ID 붙인 files, timeline_id, srt_id, cleanup).
    uploaded = {올린 로컬 경로(또는 파일 이름): 자산 ID}. 지난 result에만 있던 키는 __delete__로 지운다
    (update는 안쪽 객체까지 합치므로). 자산 ID가 빠진 파일이 있으면 ValueError."""

    def id_of(path: str) -> str | None:
        return uploaded.get(path) or uploaded.get(Path(path).name)

    files, missing = [], []
    for f in manifest["files"]:
        fid = plan["reuse"].get(f["name"]) or id_of(f["path"])
        if not fid:
            missing.append(f["name"])
        files.append({"name": f["name"], "id": fid, "media": f["media"], "sha": f["sha"]})
    ids = {}
    for key, name in TEXTS.items():
        ids[key] = plan["reuse_texts"].get(key) or id_of(str(out / name))
        if not ids[key]:
            missing.append(name)
    if missing:
        raise ValueError(f"자산 ID가 없는 파일 {len(missing)}개: {', '.join(missing[:10])}")
    keys = (
        "made_at",
        "scenes",
        "report",
        "base_token",
        "timing",
        "asr",
        "size",
        "credits",
        "hook",
        "emphasis",
    )
    result = {k: manifest[k] for k in keys if k in manifest}
    result.update(
        files=files,
        timeline_id=ids["timeline"],
        srt_id=ids["srt"],
        timeline_sha=_sha(out / TEXTS["timeline"]),
        srt_sha=_sha(out / TEXTS["srt"]),
        cleanup=plan["cleanup"],
    )
    prev = (previous or {}).get("result") or {}
    for k, v in prev.items():
        if k not in result:
            result[k] = {"__delete__": True}
        elif isinstance(v, dict) and isinstance(result[k], dict):
            for k2 in v:
                if k2 not in result[k]:
                    result[k][k2] = {"__delete__": True}
    doc = {"status": "ready", "result": result}
    (out / "result.json").write_text(json.dumps(doc, ensure_ascii=False), encoding="utf-8")
    return doc


def video_links(candidates: list, n: int = VIDEO_LINKS) -> list[dict]:
    """장면에 쓸 만한 유튜브 영상 후보(받지 않은 것): 제목·채널·시작 지점이 붙은 링크."""
    out = []
    for a in candidates:
        if a.media != "video" or a.local_path:
            continue
        start = int(a.clip_start_sec or 0)
        url = a.url + (f"&t={start}s" if start and "?" in a.url else "")
        out.append({"title": a.title[:80], "channel": a.author_or_channel, "url": url, "start": start})
        if len(out) >= n:
            break
    return out


def folder_url(folder: str) -> str:
    """사용자 폴더 → file://localhost/… (화면의 JS와 같은 규칙, 테스트용)."""
    p = folder.replace("\\", "/").rstrip("/")
    if not p.startswith("/"):
        p = "/" + p
    return "file://localhost" + quote(p, safe="/:")


def _load_project(path: Path) -> dict:
    """ArtifactData out_dir 파일({id, data, version})이든 문서 내용이든 프로젝트 dict로."""
    req = json.loads(path.read_text(encoding="utf-8"))
    if "script" not in req and isinstance(req.get("data"), dict):
        req = req["data"]
    return req


def _load_doc(path: Path) -> dict:
    """ArtifactData out_dir 파일({id, data, version})이든 문서 내용이든 문서 dict로."""
    doc = json.loads(path.read_text(encoding="utf-8"))
    return doc["data"] if isinstance(doc.get("data"), dict) and "version" in doc else doc


def main() -> None:
    """CLI."""
    ap = argparse.ArgumentParser(prog="studio_app.workroom")
    ap.add_argument(
        "--channel",
        default="",
        help="다른 채널(예: stock → channel.stock.yaml·data-stock/·projects-stock/). 비우면 기본 채널",
    )
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("collect")
    c.add_argument("--out", type=Path, required=True)
    c.add_argument(
        "--hours", type=int, help="최근 몇 시간(비우면 채널 설정 board.window_hours — 보통 24, 건강 48)"
    )
    s = sub.add_parser("sources")
    s.add_argument("--project", type=Path, required=True)
    s.add_argument("--out", type=Path, required=True)
    s.add_argument("--per-scene", type=int, default=2)
    s.add_argument("--queries", type=Path, help="장면별 검색어 JSON(scenes 단계 뒤 작업자가 쓴 것)")
    p = sub.add_parser("scenes", help="장면 나누기만 → scenes-plan.json")
    p.add_argument("--project", type=Path, required=True)
    p.add_argument("--out", type=Path, required=True)
    for x in (s, p):
        x.add_argument(
            "--audio", type=Path, help="화면이 올린 음성(audio.asset_id 파일) — 들린 말에 자막 시각을 맞춘다"
        )
    u = sub.add_parser("upload-plan", help="올릴 것만 고르기 → upload-plan.json")
    u.add_argument("--manifest", type=Path, required=True)
    u.add_argument("--previous", type=Path, help="자료를 만들기 전에 받은 projects/<ID>.json")
    r = sub.add_parser("result", help="자산 ID를 붙여 projects/<ID>에 쓸 result.json 만들기")
    r.add_argument("--manifest", type=Path, required=True)
    r.add_argument("--uploaded", type=Path, required=True, help="{올린 로컬 경로: 자산 ID} JSON")
    r.add_argument("--previous", type=Path)
    s.add_argument(
        "--slides", type=Path, help="증시 채널: 장면별 슬라이드 내용 JSON(사진 대신 슬라이드를 그린다)"
    )
    vo = sub.add_parser("voice", help="타입캐스트로 대본 읽기 → narration.wav + voice.json")
    vo.add_argument("--project", type=Path, required=True)
    vo.add_argument("--out", type=Path, required=True)
    vo.add_argument("--request", type=Path, help="requests/<ID> 문서(화면이 고른 목소리·속도·쉼)")
    vd = sub.add_parser("voice-doc", help="올린 음성 자산 ID를 붙여 projects/<ID>에 쓸 update.json 만들기")
    vd.add_argument("--voice", type=Path, required=True)
    vd.add_argument("--asset-id", required=True)
    ri = sub.add_parser("render-ids", help="완성 영상에 필요한 자산 ID 목록(작업자가 하나씩 받는다)")
    rn = sub.add_parser("render", help="받은 자산으로 완성 mp4 만들기 → final.mp4 + render.json")
    for x in (ri, rn):
        x.add_argument("--project", type=Path, required=True)
        x.add_argument("--bgm", type=Path, help="config/bgm 문서(ArtifactData out_dir 파일) — 공통 배경음악")
    rn.add_argument(
        "--fetched", type=Path, required=True, help="Artifact read로 받은 자산 폴더(<ID>.<확장자>)"
    )
    rn.add_argument("--out", type=Path, required=True)
    rd = sub.add_parser("render-doc", help="올린 완성본 자산 ID를 붙여 projects/<ID>에 쓸 update.json 만들기")
    rd.add_argument("--render", type=Path, required=True)
    rd.add_argument("--asset-id", required=True)
    a = ap.parse_args()
    if a.channel:
        os.environ["STUDIO_CHANNEL"] = a.channel
    if a.cmd == "collect":
        meta = asyncio.run(collect(a.out, a.hours))
        print(
            json.dumps(
                {k: meta[k] for k in ("posts", "articles", "article_bodies", "sites")}, ensure_ascii=False
            )
        )
        return
    if a.cmd in ("upload-plan", "result"):
        man = json.loads(a.manifest.read_text(encoding="utf-8"))
        prev = _load_project(a.previous) if a.previous else None
        out = a.manifest.parent
        if a.cmd == "upload-plan":
            plan = upload_plan(man, prev, out)
            summary = {
                "올릴 사진": sum(len(b) for b in plan["batches"]),
                "묶음": len(plan["batches"]),
                "그대로 쓰는 사진": len(plan["reuse"]),
                "올릴 글 파일": len(plan["texts"]),
                "화면이 지울 옛 자산": len(plan["cleanup"]),
                "file": str(out / "upload-plan.json"),
            }
            print(json.dumps(summary, ensure_ascii=False))
            return
        plan = json.loads((out / "upload-plan.json").read_text(encoding="utf-8"))
        doc = result_doc(man, plan, json.loads(a.uploaded.read_text(encoding="utf-8")), prev, out)
        print(
            json.dumps(
                {"files": len(doc["result"]["files"]), "file": str(out / "result.json")}, ensure_ascii=False
            )
        )
        return
    if a.cmd == "voice-doc":
        voice_doc(a.voice, a.asset_id)
        print(json.dumps({"file": str(a.voice.parent / "update.json")}, ensure_ascii=False))
        return
    if a.cmd == "render-doc":
        render_doc(a.render, a.asset_id)
        print(json.dumps({"file": str(a.render.parent / "update.json")}, ensure_ascii=False))
        return
    req = _load_project(a.project)
    if a.cmd in ("render-ids", "render"):
        bgm = _load_doc(a.bgm) if a.bgm else None
        if a.cmd == "render-ids":
            print(json.dumps(render_ids(req, bgm), ensure_ascii=False))
            return
        doc = make_render(req, a.fetched, a.out, bgm)
        print(json.dumps({**doc, "file": str(a.out / "final.mp4")}, ensure_ascii=False))
        return
    if a.cmd == "voice":
        rq = _load_project(a.request) if a.request else {}
        audio = make_voice(req, a.out, rq.get("voice"))
        keys = ("voice", "voice_id", "tempo", "duration", "chars", "credits", "retried", "warnings")
        print(
            json.dumps(
                {**{k: audio[k] for k in keys if k in audio}, "file": str(a.out / "narration.mp4")},
                ensure_ascii=False,
            )
        )
        return
    if a.cmd == "scenes":
        plan = plan_scenes(req, a.out, a.audio)
        print(json.dumps({"scenes": len(plan), "file": str(a.out / "scenes-plan.json")}, ensure_ascii=False))
        return
    queries = json.loads(a.queries.read_text(encoding="utf-8")) if a.queries else None
    slides = json.loads(a.slides.read_text(encoding="utf-8")) if a.slides else None
    man = asyncio.run(sources(req, a.out, a.per_scene, queries, a.audio, slides))
    print(
        json.dumps(
            {"files": len(man["files"]), "timing": man["timing"], "asr": man["asr"], **man["report"]},
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
