"""검색 추이(선택) — 1차는 인터페이스 + 조사 메모.

결정: Google Trends는 쓰지 않는다(공식 API 없음, pytrends는 비공식·429 차단 잦음).
네이버 데이터랩 검색어 트렌드 API를 2차에 붙인다.

조사 메모(docs/sources.md 참고):
- POST {NAVER_API_BASE}/v1/datalab/search, 네이버 검색 API와 같은 Client ID/Secret
- body: startDate, endDate, timeUnit(date|week|month), keywordGroups(최대 5개, 그룹당 키워드 최대 20개)
- 응답 ratio는 요청 구간 내 최대값=100 상대지표 → '상승률' 계산에 적합(절대량 아님)
- 일 호출 한도는 검색 API와 별도로 관리됨(개발자센터/API HUB 대시보드 확인)

구현이 없을 때 score.py는 신호 수 기반 상승률(직전 동일 길이 윈도우 대비)을 쓴다.
"""

from __future__ import annotations

from datetime import date
from typing import Protocol


class TrendProvider(Protocol):
    """키워드 묶음의 검색 상승률 제공자."""

    async def rise_ratio(self, keywords: list[str], start: date, end: date) -> float | None:
        """(최근 구간 평균 / 직전 구간 평균) - 1. 데이터 없으면 None."""
        ...


class NaverDataLabTrends:
    """네이버 데이터랩 검색어 트렌드 (2차)."""

    async def rise_ratio(self, keywords: list[str], start: date, end: date) -> float | None:
        """2차 구현 예정."""
        raise NotImplementedError("네이버 데이터랩 연동은 2차 범위입니다(docs/sources.md)")
