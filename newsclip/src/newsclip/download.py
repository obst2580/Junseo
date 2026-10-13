"""다운로드: 동시 5개, 재시도 3회, sha256 중복 제거.

- 이미지: 원본은 assets/original/에 보관하고, 시퀀스 크기(기본 1920x1080)로 커버 크롭한 JPEG를
  assets/<scene>_<rank>.jpg로 만든다 → 프리미어에서 Scale 100 = 화면 꽉 참(줌 연출 기준).
  세로 쇼츠(1080x1920)면 자르지 않고 가운데 사진 칸에 넣고 바탕은 흐린 같은 사진(newsclip.vertical).
  WebP·AVIF·GIF 등도 JPEG로 바꿔 프리미어 호환 문제를 피한다.
- 영상: yt-dlp로 필요한 구간만 받는다(장면 길이 + 앞뒤 2초, 1080p mp4).
  STUDIO_NEWSCLIP_FAKE_YTDLP=1이면 네트워크 없이 ffmpeg 테스트 패턴으로 같은 길이의 파일을 만든다(시연용).
"""

from __future__ import annotations

import asyncio
import hashlib
import io
import logging
import os
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

import httpx
from PIL import Image, ImageOps

from newsclip.models import Asset
from studio_core.http import request

log = logging.getLogger("newsclip.download")

CONCURRENCY = 5
PAD_SEC = 2.0
FORMAT = "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/b[height<=1080][ext=mp4]/b[height<=1080]"


@dataclass
class DedupeRegistry:
    """sha256 → 최초 저장 경로."""

    seen: dict[str, str] = field(default_factory=dict)

    def check(self, digest: str, path: str) -> str | None:
        """이미 있으면 기존 경로, 처음이면 None(등록)."""
        if digest in self.seen:
            return self.seen[digest]
        self.seen[digest] = path
        return None


def sha256_bytes(data: bytes) -> str:
    """바이트 해시."""
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    """파일 해시."""
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def normalize_image(data: bytes, dest: Path, size: tuple[int, int]) -> tuple[int, int]:
    """커버 크롭(세로 화면이면 흐린 바탕 + 가운데 사진 칸) + JPEG 저장. 원본 크기를 돌려준다."""
    from newsclip.vertical import compose_photo, is_vertical

    with Image.open(io.BytesIO(data)) as im:
        im = ImageOps.exif_transpose(im)
        orig = im.size
        rgb = im.convert("RGB")
        if is_vertical(size):
            fitted = compose_photo(rgb, size)
        else:
            fitted = ImageOps.fit(rgb, size, method=Image.Resampling.LANCZOS, centering=(0.5, 0.4))
        dest.parent.mkdir(parents=True, exist_ok=True)
        fitted.save(dest, "JPEG", quality=90)
    return orig


async def download_image(
    client: httpx.AsyncClient,
    a: Asset,
    dest: Path,
    registry: DedupeRegistry,
    size: tuple[int, int],
    sem: asyncio.Semaphore,
) -> Asset | None:
    """이미지 1건(실패 시 None). http:// 주소는 https://로 먼저 받는다 — 네이버 뉴스 사진(imgnews.naver.net)은
    검색 결과가 http로 오는데, 클라우드(프록시)에서는 http가 막혀 뉴스 사진이 모두 실패했다."""
    url = a.download_url or a.url
    tries = ["https://" + url[len("http://") :], url] if url.startswith("http://") else [url]
    resp = None
    async with sem:
        for u in tries:
            try:
                resp = await request(client, "GET", u, retries=2)
                break
            except Exception as e:  # 실패 격리
                log.warning("이미지 다운로드 실패 %s: %s", u, e)
    if resp is None:
        return None
    data = resp.content
    digest = sha256_bytes(data)
    dup = registry.check(digest, str(dest))
    if dup is not None:
        return a.model_copy(update={"local_path": dup, "sha256": digest})
    try:
        orig_dir = dest.parent / "original"
        orig_dir.mkdir(parents=True, exist_ok=True)
        (orig_dir / f"{dest.stem}.bin").write_bytes(data)
        w, h = await asyncio.to_thread(normalize_image, data, dest, size)
    except Exception as e:  # 손상·비이미지 응답
        log.warning("이미지 변환 실패 %s: %s", a.url, e)
        return None
    return a.model_copy(update={"local_path": str(dest), "sha256": digest, "width": w, "height": h})


def section_for(a: Asset, scene_duration: float, max_sec: float) -> tuple[float, float]:
    """받을 구간 [start, end]: 시작점 앞 2초 ~ 장면 길이(최대 max_sec) + 2초, 영상 길이 안으로."""
    start = max(0.0, (a.clip_start_sec or 0.0) - PAD_SEC)
    want = min(scene_duration, max_sec) + PAD_SEC * 2
    end = start + want
    total = a.source_duration_sec or a.duration_sec
    if total:
        end = min(end, total)
    return round(start, 2), round(max(end, start + 1.0), 2)


def ytdlp_cmd(url: str, start: float, end: float, out: Path) -> list[str]:
    """yt-dlp 구간 다운로드 명령."""
    return [
        sys.executable,
        "-m",
        "yt_dlp",
        "-f",
        FORMAT,
        "--merge-output-format",
        "mp4",
        "--download-sections",
        f"*{start:.2f}-{end:.2f}",
        "--force-keyframes-at-cuts",
        "--no-playlist",
        "--no-progress",
        "--quiet",
        "-o",
        str(out),
        url,
    ]


def fake_clip(out: Path, seconds: float, size: tuple[int, int] = (1920, 1080), fps: int = 30) -> None:
    """시연용: ffmpeg 테스트 패턴 mp4(ffmpeg 없으면 빈 파일)."""
    out.parent.mkdir(parents=True, exist_ok=True)
    if shutil.which("ffmpeg") is None:
        out.write_bytes(b"")
        return
    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-loglevel",
            "error",
            "-f",
            "lavfi",
            "-i",
            f"testsrc2=size={size[0]}x{size[1]}:rate={fps}:duration={seconds:.2f}",
            "-pix_fmt",
            "yuv420p",
            "-c:v",
            "libx264",
            "-preset",
            "ultrafast",
            str(out),
        ],
        check=True,
    )


def run_ytdlp(cmd: list[str], out: Path, seconds: float, timeout: int = 300) -> None:
    """yt-dlp 실행(시연 모드면 테스트 패턴 생성)."""
    if os.environ.get("STUDIO_NEWSCLIP_FAKE_YTDLP") == "1":
        fake_clip(out, seconds)
        return
    subprocess.run(cmd, check=True, timeout=timeout, capture_output=True)


async def download_video(
    a: Asset,
    dest: Path,
    scene_duration: float,
    max_sec: float,
    registry: DedupeRegistry,
    sem: asyncio.Semaphore,
) -> Asset | None:
    """영상 구간 1건(3회 재시도)."""
    start, end = section_for(a, scene_duration, max_sec)
    cmd = ytdlp_cmd(a.download_url or a.url, start, end, dest)
    async with sem:
        for attempt in range(3):
            try:
                await asyncio.to_thread(run_ytdlp, cmd, dest, end - start)
                break
            except Exception as e:  # 재시도 후 포기
                log.warning("yt-dlp 실패(%s/3) %s: %s", attempt + 1, a.url, e)
                await asyncio.sleep(2**attempt)
        else:
            return None
    if not dest.exists():
        return None
    digest = sha256_file(dest)
    dup = registry.check(digest, str(dest))
    path = dup or str(dest)
    # 받은 파일의 0초 = 원본 start초. 장면 시작점은 PAD_SEC 뒤(또는 start=0이면 clip_start 그대로).
    return a.model_copy(
        update={
            "local_path": path,
            "sha256": digest,
            "duration_sec": round(end - start, 2),
            "source_duration_sec": a.source_duration_sec or a.duration_sec,
        }
    )


def in_point(a: Asset) -> float:
    """잘라 온 파일 안에서 장면이 시작할 지점(초)."""
    return min(PAD_SEC, a.clip_start_sec or 0.0)
