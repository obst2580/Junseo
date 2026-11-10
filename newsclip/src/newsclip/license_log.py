"""출처 기록(assets.json)과 표기 문구.

라이선스 필터는 걸지 않는다(채널 결정). 대신 모든 자료의 출처 URL·도메인·채널을 남기고
화면에 출처 자막을 넣는다. 출처 표기는 저작권 '허락'이 아니라는 점을 README에 적어 두었다.
"""

from __future__ import annotations

import json
from pathlib import Path

from newsclip.models import Asset, SceneSet
from studio_core.config import now_kst

STOCK_SOURCES = ("pexels_image", "pexels_video", "pixabay_image", "pixabay_video")
# 한국어로 찾는 자유이용 소재 사이트 — 공유마당(공공누리 1유형·CC BY 등, 출처 표시가 이용 조건)
KO_SOURCES = ("gongu_image",)
FREE_SOURCES = (*STOCK_SOURCES, *KO_SOURCES)


def attribution_text(a: Asset) -> str:
    """표기 문구: '출처: {도메인 또는 채널명}', Wikimedia는 작가·라이선스까지."""
    if a.source == "wikimedia":
        return f"사진: {a.author_or_channel or '작자 미상'} / Wikimedia Commons / {a.license or '라이선스 확인 필요'}"
    if a.source in KO_SOURCES:
        return (
            f"사진: {a.author_or_channel or '저작자 미상'} / 공유마당 / {a.license or '이용조건 확인 필요'}"
        )
    if a.source in STOCK_SOURCES:
        site = "Pexels" if a.source.startswith("pexels") else "Pixabay"
        return f"{'영상' if a.media == 'video' else '사진'}: {a.author_or_channel or '작가 미상'} / {site}"
    if a.media == "video":
        return f"출처: {a.author_or_channel or a.source_domain} (YouTube)"
    return f"출처: {a.source_domain or a.author_or_channel or '출처 미상'}"


def write_assets_json(path: Path, sset: SceneSet) -> None:
    """다운로드된 자료 전부의 출처 기록."""
    rows = []
    for sc in sset.scenes:
        for rank_no, a in enumerate((c for c in sc.candidates if c.local_path), start=1):
            rows.append(
                {
                    "scene_id": sc.id,
                    "rank": rank_no,
                    "chosen": bool(sc.chosen and sc.chosen.id == a.id),
                    "source": a.source,
                    "media": a.media,
                    "title": a.title,
                    "url": a.url,
                    "source_page_url": a.source_page_url,
                    "source_domain": a.source_domain,
                    "author_or_channel": a.author_or_channel,
                    "license": a.license,
                    "attribution_text": a.attribution_text,
                    "local_path": a.local_path,
                    "sha256": a.sha256,
                    "clip_start_sec": a.clip_start_sec,
                    "duration_sec": a.duration_sec,
                    "provider_id": a.provider_id,
                    "fetched_at": a.fetched_at,
                    "query": sc.query,
                }
            )
    doc = {
        "project": sset.project,
        "generated_at": now_kst().isoformat(timespec="seconds"),
        "notice": "출처 표기는 저작권 이용 허락이 아님. 업로드 전 사용 범위(인용·공정이용)를 확인할 것.",
        "assets": rows,
    }
    path.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")


def write_credits(path: Path, sset: SceneSet) -> int:
    """무료 소재 사이트(Pexels·Pixabay·공유마당)에서 받은 소재의 출처 목록 → credits.txt(영상 설명란·저작권 신고 이의 제기용).
    받은 소재 수를 돌려준다(없으면 파일을 쓰지 않는다)."""
    seen: dict[str, Asset] = {}
    for sc in sset.scenes:
        for a in sc.candidates:
            if a.local_path and a.source in FREE_SOURCES:
                seen.setdefault(a.id, a)
    if not seen:
        return 0
    lines = [f"# {sset.project} 화면 소재 출처(받은 때 · 사이트 · 소재 번호 · 작가 · 주소)"]
    for a in seen.values():
        site = (
            "공유마당" if a.source in KO_SOURCES else "Pexels" if a.source.startswith("pexels") else "Pixabay"
        )
        kind = "영상" if a.media == "video" else "사진"
        lines.append(
            f"{a.fetched_at or '-'} · {site} {kind} #{a.provider_id or '-'} · {a.author_or_channel or '작가 미상'}"
            f" · {a.source_page_url or a.url} · {a.license or ''}".rstrip(" ·")
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return len(seen)
