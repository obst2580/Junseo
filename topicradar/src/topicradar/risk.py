"""리스크 표시(제외·감점 없음)와 제목 규칙 점검."""

from __future__ import annotations

import re

from topicradar.models import RiskFlag, Topic, VideoPlan

YELLOW_WORDS = [
    "사망",
    "숨진",
    "숨져",
    "살해",
    "살인",
    "자살",
    "극단적 선택",
    "성폭력",
    "성범죄",
    "성추행",
    "성폭행",
    "범죄",
    "폭행",
    "마약",
    "테러",
    "참사",
    "시신",
]
LEGAL_WORDS = [
    "수사",
    "기소",
    "재판",
    "영장",
    "압수수색",
    "혐의",
    "의혹",
    "공판",
    "선고",
    "고발",
    "고소",
    "구속",
    "특검",
]
POLL_WORDS = ["여론조사", "지지율", "조사 결과", "갤럽", "리얼미터", "오차범위"]

BANNED_TITLE = [
    "충격",
    "경악",
    "대박",
    "소름",
    "초토화",
]  # 빈 감탄(사건을 말하는 동사 '발칵·뒤집혔다'는 허용)
_DATE_TITLE = re.compile(
    r"(\d{1,2}\s*월\s*\d{1,2}\s*일|\d{1,2}[./]\d{1,2}|\d{4}[.-]\d{1,2}[.-]\d{1,2}).{0,6}(이슈|정리|뉴스|소식)?"
)


def topic_risks(t: Topic, title_text: str = "") -> list[RiskFlag]:
    """주제 하나의 리스크."""
    flags: list[RiskFlag] = []
    text = " ".join([t.name, *(s.title for s in t.signals if s.kind not in ("competitor", "community"))])
    in_title = [w for w in YELLOW_WORDS if w in f"{t.name} {title_text}"]
    if in_title:
        flags.append(
            RiskFlag(
                kind="노란딱지",
                topic_id=t.id,
                message=f"제목·썸네일에 '{in_title[0]}' 표현 → 사건 성격을 설명하는 말(예: '책임 공방', '수사 쟁점')로 조정 제안",
            )
        )
    legal = [w for w in LEGAL_WORDS if w in text]
    if legal:
        flags.append(
            RiskFlag(
                kind="법적",
                topic_id=t.id,
                message=f"수사·재판 진행 중('{legal[0]}'): '확인된 사실'과 '의혹·주장'을 구분해 쓰고 확정 표현 금지",
            )
        )
    if any(w in text for w in POLL_WORDS):
        flags.append(
            RiskFlag(
                kind="여론조사",
                topic_id=t.id,
                message="여론조사 인용 시 공직선거법 공표 요건: 조사의뢰자·조사기관·조사기간·조사방법·표본크기·"
                "오차범위(신뢰수준) 명시, '자세한 내용은 중앙선거여론조사심의위원회 홈페이지 참조' 고지",
            )
        )
    return flags


def plan_risks(plan: VideoPlan) -> list[RiskFlag]:
    """영상 계획 전체 리스크(주제별 중복 제거)."""
    titles = " ".join([*plan.titles, plan.thumbnail_text])
    seen: set[tuple[str, str | None]] = set()
    out: list[RiskFlag] = []
    for item in plan.items:
        for f in topic_risks(item.topic, titles if item.order == 1 else ""):
            key = (f.kind, f.topic_id)
            if key not in seen:
                seen.add(key)
                out.append(f)
    return out


def title_problems(title: str) -> list[str]:
    """제목 규칙 위반 목록(날짜형·빈 감탄형)."""
    problems = []
    if _DATE_TITLE.search(title):
        problems.append("날짜형")
    if any(w in title for w in BANNED_TITLE):
        problems.append("감탄형")
    return problems
