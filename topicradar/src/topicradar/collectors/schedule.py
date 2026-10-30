"""오늘 예정 일정 수집.

1차: 수동 YAML(`data/schedule/YYYY-MM-DD.yaml`) + 기사 속 "오늘 ~ 예정" 문장 추출
     (추출은 cluster.py가 담당). 이 모듈은 수동 YAML을 Signal(kind='schedule')로 바꾼다.
2차: 열린국회정보 OpenAPI(국회 일정) — 인터페이스만 둔다. 조사 메모는 docs/sources.md.

수동 YAML 형식:
    - time: "10:00"                 # 선택, 없으면 하루 일정
      title: "국회 본회의 — 플랫폼노동자 기본법 표결"
      category: 국회                  # 선택
      url: "https://..."             # 선택(근거 링크)
      note: "여당 단독 처리 가능성"     # 선택(요약)
      expected: "가결 전망"           # 선택(아침 포인트 예상 결과 힌트)
"""

from __future__ import annotations

from datetime import date, datetime, time, timedelta
from pathlib import Path
from typing import Protocol

import yaml

from studio_core.config import KST, now_kst
from studio_core.text import extract_keywords
from topicradar.collectors.base import Collector
from topicradar.models import Signal


class ScheduleProvider(Protocol):
    """일정 제공자 인터페이스."""

    async def fetch(self, since: datetime) -> list[Signal]:
        """since 이후(오늘·내일 포함) 일정 신호."""
        ...


def parse_schedule_file(path: Path, day: date) -> list[Signal]:
    """YAML 한 파일 → 일정 신호."""
    raw = yaml.safe_load(path.read_text(encoding="utf-8")) or []
    now = now_kst()
    out: list[Signal] = []
    for i, ent in enumerate(raw):
        title = str(ent.get("title", "")).strip()
        if not title:
            continue
        t = ent.get("time")
        hh, mm = (int(x) for x in str(t).split(":")) if t else (0, 0)
        at = datetime.combine(day, time(hh, mm), tzinfo=KST)
        note = str(ent.get("note", "") or "")
        url = str(ent.get("url") or f"schedule://{day.isoformat()}/{i}")
        metric = {"scheduled_at": at.isoformat(), "all_day": t is None}
        for key in ("category", "expected"):
            if ent.get(key):
                metric[key] = str(ent[key])
        out.append(
            Signal(
                source="schedule:manual",
                kind="schedule",
                fetched_at=now,
                # 일정 신호는 '알려진 시각'을 전날 18시로 둔다 → 아침 윈도우에 들어온다.
                published_at=datetime.combine(day - timedelta(days=1), time(18, 0), tzinfo=KST),
                title=title,
                url=url,
                summary=note,
                keywords=extract_keywords(f"{title} {note}", k=6),
                metric=metric,
            )
        )
    return out


class ManualScheduleCollector(Collector):
    """data/schedule/*.yaml 읽기(오늘~모레)."""

    name = "schedule"

    def __init__(self, schedule_dir: Path, days_ahead: int = 2) -> None:
        self.dir = schedule_dir
        self.days_ahead = days_ahead

    async def fetch(self, since: datetime) -> list[Signal]:
        """since 날짜부터 days_ahead일 뒤까지."""
        out: list[Signal] = []
        start = since.astimezone(KST).date()
        end = max(now_kst().date(), start) + timedelta(days=self.days_ahead)
        d = start
        while d <= end:
            f = self.dir / f"{d.isoformat()}.yaml"
            if f.exists():
                out.extend(parse_schedule_file(f, d))
            d += timedelta(days=1)
        return out


class AssemblyScheduleCollector(Collector):
    """열린국회정보 OpenAPI 국회 일정 (2차).

    조사 메모(docs/sources.md 참고):
    - 발급: open.assembly.go.kr 회원가입 후 인증키(ASSEMBLY_API_KEY) 발급
    - 형식: https://open.assembly.go.kr/portal/openapi/{서비스코드}?KEY=..&Type=json&pIndex=1&pSize=100
    - 본회의 일정·위원회 일정 서비스가 있으며 서비스코드는 포털 'Open API 목록'에서 확인 후 고정
    - 국회 일정만 커버되므로 정부 발표·재판 일정은 계속 수동 YAML/기사 추출로 보완
    """

    name = "assembly_schedule"

    async def fetch(self, since: datetime) -> list[Signal]:
        """2차 구현 예정."""
        raise NotImplementedError("열린국회정보 일정 API는 2차 범위입니다(docs/sources.md)")
