"""출처 기록(assets.json)과 표기 문구.

라이선스 필터는 걸지 않는다(채널 결정). 대신 모든 자료의 출처 URL·도메인·채널을 남기고
화면에 출처 자막을 넣는다. 출처 표기는 저작권 '허락'이 아니라는 점을 README에 적어 두었다.
"""

from __future__ import annotations

import json
from pathlib import Path

from newsclip.models import Asset, SceneSet
from studio_core.config import now_kst


def attribution_text(a: Asset) -> str:
    """표기 문구: '출처: {도메인 또는 채널명}', Wikimedia는 작가·라이선스까지."""
    if a.source == "wikimedia":
        return f"사진: {a.author_or_channel or '작자 미상'} / Wikimedia Commons / {a.license or '라이선스 확인 필요'}"
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
                }
            )
    doc = {
        "project": sset.project,
        "generated_at": now_kst().isoformat(timespec="seconds"),
        "notice": "출처 표기는 저작권 이용 허락이 아님. 업로드 전 사용 범위(인용·공정이용)를 확인할 것.",
        "assets": rows,
    }
    path.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
