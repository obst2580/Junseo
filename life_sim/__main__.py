"""인생 시뮬레이션. 실행: python3 -m life_sim"""
from __future__ import annotations

import random
import sys

import anthropic

from . import game, ui
from .storyteller import StoryError, Storyteller

HELP = """명령어
  /상태        능력치와 주변 사람
  /기록        지금까지의 인생 기록
  /넘기기 N    N년(1~10)을 특별한 행동 없이 흘려보내기
  /저장        저장 (매 턴 자동으로도 저장돼요)
  /종료        저장하고 끝내기
그 밖의 글은 모두 캐릭터의 행동이에요.
  예: 공부를 열심히 한다 / 짝사랑에게 고백한다 / 회사를 그만두고 창업한다"""
IDLE = "특별히 하는 일 없이 시간을 흘려보낸다"


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
    life = game.new_life(name, gender, rng)
    ui.say(f"{life.birth_year}년 {life.hometown}, {life.family}의 아이로 태어납니다. 타고난 재능은 「{life.talent}」.")
    return life


def start(rng: random.Random) -> game.Life:
    saved = game.load()
    if saved and not saved.dead:
        answer = ask(f"{saved.name}({saved.age}살)의 인생이 저장되어 있어요. 이어서 할까요? (Y/n) ")
        if answer.lower() not in ("n", "no", "아니", "ㄴ"):
            return saved
    return create(rng)


def play(teller: Storyteller, life: game.Life, action: str, years: int, rng: random.Random) -> None:
    """한 턴: 사건과 수명을 굴리고, 이야기꾼이 결과를 쓰고, 상태에 반영한다."""
    rolled, fate, lived = [], None, years
    for age in range(life.age, life.age + years):
        event = game.roll_event(life, age, rng)
        if event:
            rolled.append((age, event))
        if rng.random() < game.death_chance(age, life.health):
            fate, lived = game.cause_of_death(age, life.health, rng), age - life.age + 1
            break
    events = [event for _, event in rolled]
    shown = events if lived == 1 else [f"{age}살: {event}" for age, event in rolled]
    out = call(teller.turn, life, action, lived, shown, fate)
    if out is None:
        return
    if fate:  # 수명은 게임이 정한다. 이야기꾼이 빠뜨려도 이번 턴에 떠난다.
        out["died"] = True
        out["cause_of_death"] = out["cause_of_death"] or fate
    before = ui.snapshot(life)
    game.apply(life, out, lived, events)
    ui.result(out["result"], before, ui.snapshot(life))
    game.save(life)


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
        play(teller, life, IDLE, max(1, min(10, years)), rng)
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


def finale(teller: Storyteller, life: game.Life) -> None:
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
        print(ui.bold(f"  「{ending['title']}」"))
        ui.say(f"\"{ending['epitaph']}\"", ui.dim)
        print()
        ui.say(ending["story"])
    print()
    ui.status(life)
    game.delete_save()


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

    shown = None
    while not life.dead:
        if life.age != shown:  # 명령만 쓴 뒤에는 같은 상황을 다시 보여 주지 않는다
            ui.scene(life)
            shown = life.age
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
        play(teller, life, line, 1, rng)

    finale(teller, life)
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
            if ask("\n새 인생을 시작할까요? (y/N) ").lower() not in ("y", "yes", "응", "네", "ㅇ"):
                return 0
    except (EOFError, KeyboardInterrupt):
        print()
        return 0


if __name__ == "__main__":
    sys.exit(main())
