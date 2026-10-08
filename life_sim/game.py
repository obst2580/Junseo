"""게임 상태: 캐릭터 만들기, 무작위 사건, 수명, 결과 반영, 저장."""
from __future__ import annotations

import json
import os
import random
from collections.abc import Sequence
from dataclasses import asdict, dataclass, field
from pathlib import Path

STATS = {"health": "건강", "happiness": "행복", "smarts": "지능", "looks": "외모"}
SAVE_PATH = Path(os.environ.get("LIFE_SIM_SAVE", Path.home() / ".life_sim" / "save.json"))

FAMILIES = {"어려운 형편": 25, "평범한 형편": 50, "여유 있는 형편": 20, "부유한 집안": 5}
HOMETOWNS = ["서울", "경기도 신도시", "인천", "부산", "대구", "광주", "대전", "강원도 시골 마을", "전라도 바닷가 마을", "제주도"]
# 재능 → 높여 주는 능력치 (없으면 이야기에만 쓰인다)
TALENTS = {
    "운동 신경": "health", "수학 머리": "smarts", "눈에 띄는 외모": "looks", "타고난 낙천성": "happiness",
    "음악적 감각": None, "그림 솜씨": None, "말솜씨": None, "손재주": None, "끈기": None, "요리 감각": None,
}

# (사건, 최소 나이, 최대 나이, 가중치)
YEAR_EVENT_CHANCE = 0.4  # 「/1년」으로 해를 넘길 때마다
TURN_EVENT_CHANCE = 0.07  # 행동 한 번마다 (한 해에 여러 번 행동해도 사건이 넘치지 않게)
MAX_TURN_MONTHS = 3  # 행동 한 번에 흐를 수 있는 최대 개월 수. 해를 넘기는 건 플레이어가 정한다
EVENTS = [
    ("동생이 태어났다", 1, 10, 3),
    ("가족이 다른 도시로 이사를 가게 됐다", 1, 17, 2),
    ("독감으로 크게 앓았다", 0, 90, 2),
    ("부모님 사이가 나빠져 집안 분위기가 무겁다", 3, 19, 1),
    ("반에 전학생이 와서 짝이 됐다", 7, 18, 2),
    ("첫사랑이 찾아왔다", 12, 19, 2),
    ("친구들 사이에서 따돌림이 시작됐다", 9, 16, 1),
    ("대회에 나갈 기회가 생겼다", 9, 18, 2),
    ("길에서 두툼한 지갑을 주웠다", 8, 80, 1),
    ("오랜 친구가 돈을 빌려 달라고 한다", 20, 70, 2),
    ("친한 사람이 보증을 서 달라고 부탁한다", 25, 60, 1),
    ("회사에 구조조정 소문이 돈다", 25, 60, 2),
    ("다른 회사에서 이직 제안이 왔다", 27, 55, 2),
    ("주식 시장이 크게 요동친다", 20, 80, 2),
    ("집값이 크게 뛰었다는 뉴스가 연일 나온다", 25, 70, 1),
    ("복권 3등에 당첨됐다", 19, 90, 1),
    ("교통사고를 당했다", 5, 90, 1),
    ("건강검진에서 재검 통보를 받았다", 30, 90, 2),
    ("부모님 한 분이 크게 편찮으시다", 30, 70, 2),
    ("오래 연락이 끊긴 동창에게서 연락이 왔다", 20, 80, 2),
    ("길고양이가 자꾸 따라온다", 10, 90, 1),
    ("동네에 재개발 소식이 들렸다", 30, 80, 1),
]
DRAFT_NOTICE = "입영 통지서가 날아왔다"
ONCE = {DRAFT_NOTICE}  # 한 번 겪으면 다시 일어나지 않는 사건


@dataclass
class Life:
    name: str
    gender: str  # "남" / "여"
    birth_year: int
    hometown: str
    family: str
    talent: str
    background: str = ""  # 플레이어가 정한 출생 배경
    birth_month: int = 3
    months: int = 0  # 태어나서 지금까지 흐른 개월 수. 행동은 조금씩, 「/1년」은 12개월
    age: int = 0  # months // 12
    health: int = 80
    happiness: int = 60
    smarts: int = 50
    looks: int = 50
    money: int = 0  # 만원
    education: str = "없음"
    job: str = "없음"
    people: list[dict] = field(default_factory=list)  # {"name", "relation", "closeness"}
    chronicle: list[str] = field(default_factory=list)  # 해마다 한 줄씩 쌓이는 인생 기록
    recent: list[str] = field(default_factory=list)  # 최근 결과 글 (이야기를 이어 가려고 이야기꾼에게 다시 보여 준다)
    scene: str = ""  # 지금 플레이어 앞에 놓인 상황
    suggestions: list[str] = field(default_factory=list)
    seen: list[str] = field(default_factory=list)  # 한 번만 일어나는 사건
    dead: bool = False
    cause_of_death: str = ""
    offscreen_death: bool = False  # 이야기 밖에서(수명 판정으로) 떠났다 — 엔딩이 마지막 장면을 쓴다
    last_span: int | None = None  # 직전 턴에 흐른 개월 수
    turns: int = 0

    @property
    def year(self) -> int:
        return self.birth_year + (self.birth_month - 1 + self.months) // 12

    @property
    def month(self) -> int:
        return (self.birth_month - 1 + self.months) % 12 + 1

    @property
    def when(self) -> str:
        return f"{self.year}년 {self.month}월"


def span_text(months: int) -> str:
    """흐른 시간을 말로: 0 → 며칠 사이, 5 → 5개월 뒤, 26 → 2년 2개월 뒤"""
    if not months:
        return "며칠 사이"
    y, m = divmod(months, 12)
    return " ".join(part for part in (f"{y}년" if y else "", f"{m}개월" if m else "") if part) + " 뒤"


def new_life(
    name: str,
    gender: str,
    rng: random.Random | None = None,
    *,
    birth_year: int | None = None,
    hometown: str = "",
    family: str = "",
    talent: str = "",
    background: str = "",
) -> Life:
    """새 인생. 정한 것은 그대로 쓰고, 비운 것은 운에 맡긴다."""
    rng = rng or random.Random()
    talent = talent or rng.choice(list(TALENTS))
    life = Life(
        name=name,
        gender=gender,
        birth_year=birth_year or rng.randint(1990, 2010),
        hometown=hometown or rng.choice(HOMETOWNS),
        family=family or rng.choices(list(FAMILIES), weights=list(FAMILIES.values()))[0],
        talent=talent,
        background=background,
        birth_month=rng.randint(1, 12),
        health=rng.randint(60, 95),
        happiness=rng.randint(45, 80),
        smarts=rng.randint(30, 75),
        looks=rng.randint(30, 75),
    )
    if TALENTS.get(talent):
        setattr(life, TALENTS[talent], min(100, getattr(life, TALENTS[talent]) + 20))
    return life


def roll_event(life: Life, age: int, rng: random.Random, chance: float = TURN_EVENT_CHANCE) -> str | None:
    """이 나이에 일어나는 무작위 사건. 이야기꾼이 결과에 녹여 넣는다.
    상태는 바꾸지 않는다 — 턴이 끝까지 진행된 뒤에 apply 가 ONCE 사건을 seen 에 남긴다."""
    if life.gender == "남" and 20 <= age <= 28 and DRAFT_NOTICE not in life.seen:
        return DRAFT_NOTICE
    if rng.random() >= chance:
        return None
    pool = [(text, weight) for text, lo, hi, weight in EVENTS if lo <= age <= hi]
    return rng.choices([t for t, _ in pool], weights=[w for _, w in pool])[0] if pool else None


def death_chance(age: int, health: int) -> float:
    """한 해 동안 세상을 떠날 확률. 40살 0.2% → 80살 약 6% → 100살 약 35%, 건강이 나쁘면 최대 3배."""
    if health <= 0:
        return 1.0
    base = 0.0005 if age < 40 else 0.002 * 1.09 ** (age - 40)
    frailty = 1 + max(0, 60 - health) / 30
    return min(0.95, base * frailty)


def cause_of_death(age: int, health: int, rng: random.Random) -> str:
    if age >= 75:
        return rng.choice(["노환", "지병 악화", "잠자듯 평온한 마지막"])
    if health < 40:
        return rng.choice(["지병 악화", "갑작스러운 병"])
    return rng.choice(["갑작스러운 사고", "갑작스러운 병"])


def death_roll(start_months: int, months: int, health: int, rng: random.Random) -> str | None:
    """흐른 개월 수만큼 수명을 굴린다. 이 사이에 세상을 떠나면 사인, 아니면 None."""
    for t in range(0, months, 12):
        span = min(12, months - t)
        age = (start_months + t) // 12
        if rng.random() < 1 - (1 - death_chance(age, health)) ** (span / 12):
            return cause_of_death(age, health, rng)
    return None


def _clamp(value: int, lo: int, hi: int) -> int:
    return max(lo, min(hi, value))


def apply(life: Life, out: dict, months: int, events: Sequence[str] = ()) -> None:
    """이야기꾼의 결과를 반영하고 months 만큼 시간을 흘린다. events 는 이번 턴에 굴린 무작위 사건."""
    changes = out["changes"]
    for key in STATS:
        delta = _clamp(int(changes.get(key, 0)), -40, 40)
        setattr(life, key, _clamp(getattr(life, key) + delta, 0, 100))
    life.money += int(changes.get("money", 0))
    life.education = out["education"] or life.education
    life.job = out["job"] or life.job
    life.people = [
        {"name": p["name"], "relation": p["relation"], "closeness": _clamp(int(p["closeness"]), 0, 100)}
        for p in out["people"]
    ]

    start_age = life.age
    life.months += months
    life.age = life.months // 12
    life.last_span = None if life.turns == 0 else months
    label = "출생" if life.turns == 0 else (f"{start_age}살" if start_age == life.age else f"{start_age}~{life.age}살")
    life.chronicle.append(f"{label}: {out['summary']}")
    life.recent = (life.recent + [out["result"]])[-2:]
    life.seen += [e for e in events if e in ONCE]
    life.turns += 1

    if out["died"] or life.health <= 0:
        life.dead = True
        life.cause_of_death = out["cause_of_death"] or life.cause_of_death or "건강 악화"
        life.scene, life.suggestions = "", []
    else:
        life.scene = out["next_situation"]
        life.suggestions = list(out["suggestions"])[:3]


def save(life: Life, path: Path | None = None) -> None:
    path = path or SAVE_PATH
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(asdict(life), ensure_ascii=False, indent=1), encoding="utf-8")
    tmp.replace(path)


def load(path: Path | None = None) -> Life | None:
    path = path or SAVE_PATH
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        if "months" not in data:  # 해마다 한 턴이던 때의 저장본
            data["months"] = data.get("age", 0) * 12
        return Life(**data)
    except (FileNotFoundError, json.JSONDecodeError, TypeError):
        return None


def delete_save(path: Path | None = None) -> None:
    path = path or SAVE_PATH
    path.unlink(missing_ok=True)
