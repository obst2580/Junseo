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

# (사건, 최소 나이, 최대 나이, 가중치). 해마다 EVENT_CHANCE 확률로 하나가 일어난다.
EVENT_CHANCE = 0.35
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
    age: int = 0
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

    @property
    def year(self) -> int:
        return self.birth_year + self.age


def new_life(name: str, gender: str, rng: random.Random | None = None) -> Life:
    rng = rng or random.Random()
    talent = rng.choice(list(TALENTS))
    life = Life(
        name=name,
        gender=gender,
        birth_year=rng.randint(1990, 2010),
        hometown=rng.choice(HOMETOWNS),
        family=rng.choices(list(FAMILIES), weights=list(FAMILIES.values()))[0],
        talent=talent,
        health=rng.randint(60, 95),
        happiness=rng.randint(45, 80),
        smarts=rng.randint(30, 75),
        looks=rng.randint(30, 75),
    )
    if TALENTS[talent]:
        setattr(life, TALENTS[talent], min(100, getattr(life, TALENTS[talent]) + 20))
    return life


def roll_event(life: Life, age: int, rng: random.Random) -> str | None:
    """이 나이에 일어나는 무작위 사건. 이야기꾼이 결과에 녹여 넣는다.
    상태는 바꾸지 않는다 — 턴이 끝까지 진행된 뒤에 apply 가 ONCE 사건을 seen 에 남긴다."""
    if life.gender == "남" and age == 20 and DRAFT_NOTICE not in life.seen:
        return DRAFT_NOTICE
    if rng.random() >= EVENT_CHANCE:
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


def _clamp(value: int, lo: int, hi: int) -> int:
    return max(lo, min(hi, value))


def apply(life: Life, out: dict, years: int, events: Sequence[str] = ()) -> None:
    """이야기꾼의 결과를 상태에 반영하고 나이를 먹인다. events 는 이번 턴에 굴린 무작위 사건."""
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

    label = "출생" if years == 0 else (f"{life.age}살" if years == 1 else f"{life.age}~{life.age + years - 1}살")
    life.chronicle.append(f"{label}: {out['summary']}")
    life.recent = (life.recent + [out["result"]])[-2:]
    life.seen += [e for e in events if e in ONCE]
    life.age += years

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
        return Life(**json.loads(path.read_text(encoding="utf-8")))
    except (FileNotFoundError, json.JSONDecodeError, TypeError):
        return None


def delete_save(path: Path | None = None) -> None:
    path = path or SAVE_PATH
    path.unlink(missing_ok=True)
