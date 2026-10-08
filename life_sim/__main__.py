"""인생 시뮬레이션. 실행: python3 -m life_sim"""
from __future__ import annotations

import dataclasses
import random
import sys

import anthropic

from . import game, ui
from .storyteller import StoryError, Storyteller

HELP = """명령어
  /상태        능력치와 주변 사람
  /기록        지금까지의 인생 기록
  /넘기기 N    N년 넘기기, N 을 빼면 1년 (그 밖의 행동은 같은 시기 안에서 이어져요)
  /저장        저장 (매 턴 자동으로도 저장돼요)
  /종료        저장하고 끝내기
그 밖의 글은 모두 캐릭터의 행동이에요.
  예: 공부를 열심히 한다 / 짝사랑에게 고백한다 / 회사를 그만두고 창업한다"""


YES = ("y", "yes", "응", "네", "ㅇ")


def ask(prompt: str) -> str:
    return input(prompt).strip()


def call(fn, *args):
    """이야기꾼을 부른다. 실패하면 이유를 보여 주고 None — 턴은 넘어가지 않는다."""
    try:
        with ui.waiting():
            return fn(*args)
    except StoryError as e:
        ui.say(str(e), ui.warn)
    except anthropic.AuthenticationError:
        sys.exit("Claude API 키가 올바르지 않아요. ANTHROPIC_API_KEY 를 확인해 주세요.")
    except anthropic.RateLimitError:
        ui.say("요청이 몰렸어요. 잠시 뒤 다시 입력해 주세요.", ui.warn)
    except anthropic.APIConnectionError:
        ui.say("Claude 에 연결하지 못했어요. 네트워크를 확인하고 다시 입력해 주세요.", ui.warn)
    except anthropic.APIStatusError as e:
        ui.say(f"Claude API 오류({e.status_code})가 났어요. 다시 입력해 주세요.", ui.warn)
    return None


def create(rng: random.Random) -> game.Life:
    ui.rule("새 인생")
    name = ""
    while not name:
        name = ask("이름: ")
    gender = ""
    while gender not in ("남", "여"):
        gender = ask("성별 (남/여): ")
    print(ui.dim("태어날 조건을 정해 주세요. 비워 두고 Enter 를 누르면 운에 맡깁니다."))
    while True:
        year = ask("태어난 해 (1950~2025): ")
        if not year or (year.isdigit() and 1950 <= int(year) <= 2025):
            break
    families = list(game.FAMILIES)
    family = ask("집안 형편 (" + " / ".join(f"{i}. {f}" for i, f in enumerate(families, 1)) + "): ")
    life = game.new_life(
        name, gender, rng,
        birth_year=int(year) if year else None,
        hometown=ask("고향: "),
        family=families[int(family) - 1] if family.isdigit() and 1 <= int(family) <= len(families) else family,
        talent=ask("타고난 재능 (예: " + ", ".join(list(game.TALENTS)[:4]) + "): "),
        background=ask("출생 배경 (예: 쌍둥이 중 동생으로 태어났다): "),
    )
    ui.say(f"{life.birth_year}년 {life.hometown}, {life.family}의 아이로 태어납니다. 타고난 재능은 「{life.talent}」.")
    return life


def start(rng: random.Random) -> game.Life:
    saved = game.load()
    if saved and not saved.dead:
        answer = ask(f"{saved.name}({saved.age}살)의 인생이 저장되어 있어요. 이어서 할까요? (Y/n) ")
        if answer.lower() not in ("n", "no", "아니", "ㄴ"):
            return saved
    return create(rng)


def play(teller: Storyteller, life: game.Life, action: str, rng: random.Random, skip_years: int = 0) -> None:
    """한 턴: 사건을 굴리고, 이야기꾼이 결과를 쓰고, 흐른 시간만큼 수명을 굴린다.
    행동 턴은 시간이 조금만(최대 MAX_TURN_MONTHS) 흐르고, skip_years 가 있으면 그 햇수가 흐른다."""
    rolled: list[tuple[int, str]] = []
    for age in range(life.age, life.age + skip_years) if skip_years else [life.age]:
        e = game.roll_event(life, age, rng, game.YEAR_EVENT_CHANCE if skip_years else game.TURN_EVENT_CHANCE)
        if e and not (e in game.ONCE and any(x == e for _, x in rolled)):
            rolled.append((age, e))
    events = [e for _, e in rolled]
    shown = [f"만 {age}살 무렵: {e}" for age, e in rolled] if skip_years > 1 else events
    out = call(teller.turn, life, action, shown, skip_years)
    if out is None:
        return

    def months_of(o: dict) -> int:
        return skip_years * 12 if skip_years else max(0, min(game.MAX_TURN_MONTHS, int(o.get("months_passed", 0))))

    out = regrade(life, out, months_of, lambda note: call(teller.turn, life, action, shown, skip_years, note))
    months = months_of(out)
    start_months = life.months
    before = ui.snapshot(life)
    game.apply(life, out, months, events)
    ui.result(out["result"], before, ui.snapshot(life), span=months)
    # 수명은 게임이 정한다: 흐른 시간만큼 굴려서, 이 사이에 떠났으면 엔딩이 그 장면을 쓴다
    cause = None if life.dead else game.death_roll(start_months, months, life.health, rng)
    if cause:
        life.dead, life.cause_of_death, life.offscreen_death = True, cause, True
        life.scene, life.suggestions = "", []
    game.save(life)


def regrade(life: game.Life, out: dict, months_of, ask_again) -> dict:
    """캐릭터의 학년을 또래 학년과 다르게 썼으면 한 번 고쳐 쓰게 하고, 그래도 틀리면 표기만 바로잡는다."""
    def slip_of(o: dict):
        return game.grade_slip(life, o, dataclasses.replace(life, months=life.months + months_of(o)).peer_grade)

    slip = slip_of(out)
    if not slip:
        return out
    again = ask_again(f"앞선 답에서 당신의 학년을 \"{slip[0]}\"(이)라고 썼는데 틀렸다. 그 시점의 또래 학년은 {slip[1]}이고, "
                      f"학교 일정은 {life.school_dates}이다. 학년과 입학·졸업 시기를 이에 맞게 고쳐 다시 써라.")
    if again is None:
        return game.fix_grade(out, slip)
    still = slip_of(again)
    return game.fix_grade(again, still) if still else again


def command(line: str, teller: Storyteller, life: game.Life, rng: random.Random) -> bool:
    """/명령을 처리한다. 게임을 끝내야 하면 False."""
    name, _, arg = line[1:].partition(" ")
    if name == "상태":
        ui.status(life)
        ui.people(life)
    elif name == "기록":
        ui.chronicle(life)
    elif name == "넘기기":
        years = int(arg) if arg.strip().isdigit() else 1
        play(teller, life, "", rng, skip_years=max(1, min(10, years)))
    elif name == "저장":
        game.save(life)
        print(ui.dim(f"저장했어요: {game.SAVE_PATH}"))
    elif name == "종료":
        game.save(life)
        print(ui.dim("저장했어요. 다음에 이어서 할 수 있어요."))
        return False
    else:
        print(HELP)
    return True


def finale(teller: Storyteller, life: game.Life) -> str:
    """부고를 보여 준다. 떠나던 장면을 돌려준다 (되살리기가 이어 쓰도록)."""
    ending = None
    while ending is None:
        ending = call(teller.ending, life)
        if ending is None and ask("엔딩을 다시 써 볼까요? (Y/n) ").lower() in ("n", "no", "아니", "ㄴ"):
            break
    print()
    ui.rule("부고")
    ui.say(f"{life.name} ({life.birth_year}~{life.year}), {life.age}살에 {life.cause_of_death}(으)로 세상을 떠났습니다.")
    if ending:
        print()
        ui.say(ending["last_moment"])
        print()
        print(ui.bold(f"  「{ending['title']}」"))
        ui.say(f"\"{ending['epitaph']}\"", ui.dim)
        print()
        ui.say(ending["story"])
    print()
    ui.status(life)
    return ending["last_moment"] if ending else ""


REVIVED_SCENE = "당신은 병원 침대에서 눈을 떴다. 몸은 아직 무겁지만 살아 있다. 곁에는 밤새 당신을 지킨 가족이 있다."
REVIVED_SUGGESTIONS = ["몸을 추스르며 푹 쉰다", "가족에게 그동안의 일을 털어놓는다", "앞으로의 계획을 다시 세운다"]


def revive(teller: Storyteller, life: game.Life, last_moment: str) -> bool:
    """죽음의 문턱에서 살아나는 장면을 받아 같은 인생을 이어 간다. 실패하면 False."""
    alive = dataclasses.replace(life, health=max(life.health, 25))
    out = call(teller.revive, alive, last_moment)
    if out is None:
        return False
    # 이야기꾼이 또 죽음으로 끝맺거나 다음 장면을 비워 와도 되살리기는 되살리기다
    out = {**out, "died": False, "cause_of_death": "",
           "next_situation": out.get("next_situation") or REVIVED_SCENE,
           "suggestions": out.get("suggestions") or REVIVED_SUGGESTIONS}
    life.dead, life.cause_of_death, life.offscreen_death = False, "", False
    life.health = alive.health
    life.revivals += 1
    before = ui.snapshot(life)
    game.apply(life, out, max(0, min(game.MAX_TURN_MONTHS, int(out.get("months_passed", 0)))))
    life.health = max(life.health, game.CRITICAL)  # 막 살아난 몸이 바로 다시 위독하지 않게
    ui.result(out["result"], before, ui.snapshot(life), span=life.last_span)
    game.save(life)
    return True


def run(teller: Storyteller, rng: random.Random) -> bool:
    """한 인생을 플레이한다. 끝까지 살았으면 True, 중간에 그만뒀으면 False."""
    life = start(rng)
    while not life.scene and not life.dead:  # 새 인생: 태어나는 장면부터
        out = call(teller.opening, life)
        if out is None:
            ask("엔터를 누르면 다시 시도해요.")
            continue
        game.apply(life, out, 0)
        ui.result(out["result"])
        game.save(life)
        print(ui.dim("\n/도움 으로 명령어를 볼 수 있어요. 하고 싶은 행동을 자유롭게 적어 보세요."))

    while True:
        shown = None
        while not life.dead:
            if life.turns != shown:  # 명령만 쓴 뒤에는 같은 상황을 다시 보여 주지 않는다
                ui.scene(life)
                shown = life.turns
            try:
                line = ask("\n> ")
            except (EOFError, KeyboardInterrupt):
                game.save(life)
                print(ui.dim("\n저장했어요. 다음에 이어서 할 수 있어요."))
                return False
            if not line:
                continue
            if line.startswith("/"):
                if not command(line, teller, life, rng):
                    return False
                continue
            play(teller, life, line, rng)

        last_moment = finale(teller, life)
        while ask("\n되살릴까요? (y/N) ").lower() in YES:
            if revive(teller, life, last_moment):
                break
        else:
            game.delete_save()
            return True


def main() -> int:
    print(ui.bold("\n  인 생 시 뮬 레 이 션\n"))
    teller = Storyteller()
    client = teller.client
    if not (client.api_key or client.auth_token or client.credentials):
        print("Claude API 키가 필요해요. ANTHROPIC_API_KEY 환경 변수를 설정한 뒤 다시 실행해 주세요.")
        return 1
    rng = random.Random()
    try:
        while run(teller, rng):
            if ask("\n새 인생을 시작할까요? (y/N) ").lower() not in YES:
                return 0
    except (EOFError, KeyboardInterrupt):
        print()
        return 0


if __name__ == "__main__":
    sys.exit(main())
