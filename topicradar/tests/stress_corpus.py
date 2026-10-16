"""군집 스트레스용 가상 말뭉치: 실제 하루치처럼 사건 수십 개 × 기사 여러 건 + 공통 어휘 + 여러 사건을 섞어 말하는 커뮤니티 글.

작은 시연 데이터에서는 안 보이던 '연쇄 병합'(A~B, B~C가 겹치면 A·B·C가 한 주제가 되는 문제)을 재현한다.
"""

from __future__ import annotations

import random

from trh import sig

# 사건마다 고유어(핵심어)와 기사 제목 틀. 공통어(대통령·정부·지시·논란·조사·서울…)를 일부러 많이 섞는다.
STORIES = {
    "golf": (
        ["골프장", "대구", "붕괴사고", "신속수습"],
        "대통령, 골프장서 대구 붕괴사고 보고받고 신속수습 지시",
    ),
    "collapse": (["대구", "붕괴", "건물", "매몰"], "대구 건물 붕괴로 매몰자 수색…정부 대책회의"),
    "hack": (["금융보안원", "해킹", "금융사", "외부노출"], "금융보안원 경고에도 금융사 해킹 피해…정부 조사"),
    "hack_budget": (["보안예산", "해킹", "은행", "증액"], "해킹 사고 뒤 은행 보안예산 증액 논란"),
    "trial_ex": (
        ["이상민", "김대기", "1심", "형사합의36부"],
        "이상민·김대기 1심 선고 앞둬…전 정부 인사 재판",
    ),
    "trial_yoon": (
        ["선거법", "2심", "397억", "보조금"],
        "윤석열 전 대통령 선거법 2심 마무리…국민의힘 397억 걸려",
    ),
    "ukraine": (
        ["우크라이나", "젤렌스키", "자포리자", "활공폭탄"],
        "러시아 자포리자 공습…젤렌스키 대통령 규탄",
    ),
    "trump_diesel": (
        ["트럼프", "경유", "푸틴", "새지도자"],
        "트럼프 대통령, 우크라이나에 새 지도자 필요 발언 논란",
    ),
    "thai": (["태국", "공무원", "희망퇴직", "1년치"], "태국, 공무원 희망퇴직 단행…1년치 월급 지급"),
    "public_reform": (["공공기관", "109곳", "통폐합", "발전5사"], "정부 공공기관 109곳 통폐합 개혁안 논란"),
    "budget": (["예산안", "증액", "국회예결위", "삭감"], "국회 예결위, 내년 예산안 삭감 공방"),
    "audit": (["국정감사", "법사위", "특활비", "자료제출"], "법사위 국정감사 특활비 자료 제출 공방"),
    "poll": (["지지율", "여론조사", "갤럽", "하락"], "대통령 지지율 하락…여론조사 결과 발표"),
    "nk": (["북한", "탄도미사일", "동해상", "합참"], "북한 동해상 탄도미사일 발사…합참 발표"),
    "fx": (["환율", "원달러", "1430원", "외환당국"], "원달러 환율 1430원 돌파…외환당국 경계"),
    "strike": (["택배노조", "파업", "찬반투표", "가결"], "택배노조 파업 찬반투표 가결…정부 대책 논의"),
    "medical": (["지역의료", "공공의대", "의대정원", "복지부"], "복지부, 지역의료 강화·공공의대 설립 발표"),
    "prosecutor": (["검찰개혁", "수사권", "중수청", "법무부"], "법무부 검찰개혁 중수청 설치안 국회 제출"),
    "flood": (["집중호우", "산사태", "강원", "대피"], "강원 집중호우 산사태 주민 대피…정부 지원"),
    "realestate": (["부동산", "PF", "저축은행", "연체율"], "부동산 PF 부실 우려…저축은행 연체율 상승"),
    "japan": (["일본", "수산물", "오염수", "수입규제"], "일본 수산물 수입규제 논란…정부 입장 발표"),
    "ai_law": (["AI기본법", "시행령", "과기정통부", "규제"], "과기정통부 AI기본법 시행령 입법예고"),
    "teacher": (["교권", "교사", "학부모", "교육부"], "교육부 교권 보호 대책 발표…교사 단체 반발"),
    "pension": (["국민연금", "보험료율", "소득대체율", "개혁"], "국민연금 보험료율 인상안 국회 논의"),
}
FILLERS = ["서울", "정부", "대통령", "지시", "논란", "조사", "발표", "관련", "책임", "대책", "오늘", "국회"]
SOURCES = ["연합뉴스", "한겨레", "경향신문", "KBS", "MBC", "SBS", "JTBC", "뉴시스", "뉴스1", "오마이뉴스"]


def build(seed: int = 7, per_story: tuple[int, int] = (6, 14), day: str = "2026-10-10") -> tuple[list, dict]:
    """(신호 목록, {신호 id: 사건 키}). 커뮤니티 글은 사건 키를 '+'로 이은 값."""
    rnd = random.Random(seed)
    sigs, truth = [], {}
    n = 0
    for key, (core, title) in STORIES.items():
        for k in range(rnd.randint(*per_story)):
            n += 1
            words = rnd.sample(core, 3) + rnd.sample(FILLERS, 3)
            hh, mm = 6 + (n % 11), (n * 7) % 60
            t = (
                title
                if k == 0
                else f"[{rnd.choice(SOURCES)}] {' '.join(words[:4])} {rnd.choice(['후속', '쟁점', '반응', '파장', '전망'])}"
            )
            summary = f"{' '.join(words)} 관련 보도. {core[0]} 문제를 두고 {rnd.choice(FILLERS)} 쪽 설명이 나왔다. {core[1]} 상황은 계속된다."
            s = sig(t, f"{day} {hh:02d}:{mm:02d}", summary, url=f"https://news{n % 9}.example/{key}/{n}")
            sigs.append(s)
            truth[s.id] = key
    # 여러 사건을 한 글에서 말하는 커뮤니티 글(연쇄 병합의 다리 역할)
    keys = list(STORIES)
    for i in range(40):
        a, b = rnd.sample(keys, 2)
        ca, cb = STORIES[a][0], STORIES[b][0]
        title = f"{ca[0]} {ca[1]} 이어서 {cb[0]} {cb[1]}까지 {rnd.choice(FILLERS)} 뭐하냐"
        s = sig(
            title,
            f"{day} {8 + i % 9:02d}:{(i * 11) % 60:02d}",
            kind="community",
            site="보배드림",
            comments=10 + i,
        )
        sigs.append(s)
        truth[s.id] = f"{a}+{b}"
    return sigs, truth


def purity_report(topics: list, truth: dict) -> dict:
    """주제별 '가장 많은 사건'의 비율과 최대 주제 크기(뉴스 기준)."""
    sizes, impure = [], []
    for t in topics:
        news = [s for s in t.signals if s.kind != "community"]
        if not news:
            continue
        from collections import Counter

        c = Counter(truth[s.id] for s in news)
        top, cnt = c.most_common(1)[0]
        sizes.append(len(news))
        if cnt / len(news) < 0.8:
            impure.append((t.name, dict(c)))
    return {"max_size": max(sizes or [0]), "topics": len(sizes), "impure": impure}
