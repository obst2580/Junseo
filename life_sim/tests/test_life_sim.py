"""실행: python3 -m unittest discover -s life_sim/tests -t ."""
import io
import json
import random
import tempfile
import unittest
from contextlib import redirect_stdout
from dataclasses import asdict
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

from life_sim import __main__ as app
from life_sim import game, ui
from life_sim.storyteller import ENDING_SCHEMA, Storyteller, StoryError, describe


def turn_out(**over):
    out = {
        "result": "시간이 흘렀다.",
        "months_passed": 6,
        "summary": "평범한 한 해",
        "changes": {"health": 2, "happiness": -3, "smarts": 1, "looks": 0, "money": 50},
        "education": "초등학생",
        "job": "없음",
        "people": [{"name": "김영희", "relation": "엄마", "closeness": 90}],
        "next_situation": "새 학기가 시작됐다.",
        "suggestions": ["공부한다", "논다", "운동한다"],
        "died": False,
        "cause_of_death": "",
    }
    out.update(over)
    return out


class FakeClient:
    """client.beta.messages.create 를 흉내 낸다. 받은 요청을 requests 에 남긴다."""

    api_key, auth_token, credentials = "test", None, None

    def __init__(self, replies=None):
        self.replies = list(replies or [])
        self.requests = []
        self.beta = SimpleNamespace(messages=SimpleNamespace(create=self.create))

    def create(self, **kwargs):
        self.requests.append(kwargs)
        schema = kwargs["output_config"]["format"]["schema"]
        reply = self.replies.pop(0) if self.replies else (
            {"last_moment": "조용히 눈을 감았다.", "story": "끝.", "title": "평범한 사람", "epitaph": "잘 살았다"} if schema is ENDING_SCHEMA else turn_out()
        )
        if isinstance(reply, SimpleNamespace):
            return reply
        return SimpleNamespace(stop_reason="end_turn", content=[SimpleNamespace(type="text", text=json.dumps(reply))])


class GameTest(unittest.TestCase):
    def setUp(self):
        self.life = game.new_life("준서", "남", random.Random(1))

    def test_new_life_within_bounds(self):
        for seed in range(50):
            life = game.new_life("a", "여", random.Random(seed))
            for key in game.STATS:
                self.assertTrue(0 <= getattr(life, key) <= 100)
            self.assertTrue(1990 <= life.birth_year <= 2010)

    def test_new_life_keeps_chosen_conditions(self):
        life = game.new_life("a", "여", random.Random(0), birth_year=1975, hometown="목포",
                             family="부유한 집안", talent="바둑", background="쌍둥이 동생")
        self.assertEqual((life.birth_year, life.hometown, life.family, life.talent), (1975, "목포", "부유한 집안", "바둑"))
        self.assertIn("출생 배경(플레이어가 정함): 쌍둥이 동생", describe(life))

    def test_custom_start_in_cli(self):
        inputs = ["준서", "남", "3000", "1988", "4", "울산", "", "아버지는 조선소 용접공이다"]
        with mock.patch("builtins.input", side_effect=inputs), redirect_stdout(io.StringIO()):
            life = app.create(random.Random(0))
        self.assertEqual((life.birth_year, life.family, life.hometown), (1988, "부유한 집안", "울산"))
        self.assertIn(life.talent, game.TALENTS)
        self.assertEqual(life.background, "아버지는 조선소 용접공이다")

    def test_calendar_and_span(self):
        self.life.birth_year, self.life.birth_month, self.life.months = 2003, 11, 17
        self.assertEqual(self.life.when, "2005년 4월")
        self.assertEqual([game.span_text(m) for m in (0, 5, 12, 26)], ["며칠 사이", "5개월 뒤", "1년 뒤", "2년 2개월 뒤"])

    def test_death_roll_scales_with_time(self):
        rng = random.Random(0)
        self.assertIsNone(game.death_roll(80 * 12, 0, 70, rng))  # 시간이 흐르지 않으면 떠나지 않는다
        self.assertIsNotNone(game.death_roll(30 * 12, 1, 0, rng))  # 건강 0 이면 반드시
        month = sum(game.death_roll(80 * 12, 1, 70, rng) is not None for _ in range(20000)) / 20000
        self.assertAlmostEqual(month, 1 - (1 - game.death_chance(80, 70)) ** (1 / 12), delta=0.002)

    def test_death_chance_grows_with_age_and_frailty(self):
        self.assertLess(game.death_chance(20, 80), game.death_chance(80, 80))
        self.assertLess(game.death_chance(70, 90), game.death_chance(70, 20))
        self.assertEqual(game.death_chance(30, 0), 1.0)

    def test_draft_notice_once_for_men_at_20(self):
        rng = random.Random(0)
        self.assertEqual(game.roll_event(self.life, 20, rng), game.DRAFT_NOTICE)
        game.apply(self.life, turn_out(), 1, [game.DRAFT_NOTICE])
        self.assertNotEqual(game.roll_event(self.life, 20, random.Random(0)), game.DRAFT_NOTICE)
        woman = game.new_life("b", "여", random.Random(1))
        self.assertNotEqual(game.roll_event(woman, 20, random.Random(0)), game.DRAFT_NOTICE)

    def test_apply_clamps_and_advances(self):
        game.apply(self.life, turn_out(), 0)  # 태어나는 장면
        self.life.health, self.life.happiness, self.life.smarts = 95, 30, 70
        changes = {"health": 99, "happiness": -500, "smarts": -500, "looks": 0, "money": -30}
        game.apply(self.life, turn_out(changes=changes), 40)
        self.assertEqual(self.life.health, 100)
        self.assertEqual(self.life.happiness, 0)
        self.assertEqual(self.life.smarts, 30)  # 한 턴 변화량은 ±40 까지
        self.assertEqual(self.life.money, 20)
        self.assertEqual((self.life.months, self.life.age, self.life.last_span), (40, 3, 40))
        self.assertEqual(self.life.chronicle, ["출생: 평범한 한 해", "0~3살: 평범한 한 해"])
        game.apply(self.life, turn_out(), 0)  # 며칠 사이의 일은 같은 나이로 남는다
        self.assertEqual((self.life.age, self.life.chronicle[-1]), (3, "3살: 평범한 한 해"))
        self.assertEqual(self.life.scene, "새 학기가 시작됐다.")

    def test_apply_death_when_health_runs_out(self):
        self.life.health = 5
        game.apply(self.life, turn_out(changes={"health": -10, "happiness": 0, "smarts": 0, "looks": 0, "money": 0}), 1)
        self.assertTrue(self.life.dead)
        self.assertEqual(self.life.scene, "")

    def test_load_old_yearly_save(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "save.json"
            data = {k: v for k, v in asdict(self.life).items() if k not in ("months", "turns", "last_span")}
            path.write_text(json.dumps({**data, "age": 7}, ensure_ascii=False), encoding="utf-8")
            self.assertEqual(game.load(path).months, 84)

    def test_save_and_load_roundtrip(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "save.json"
            game.apply(self.life, turn_out(), 1)
            game.save(self.life, path)
            self.assertEqual(game.load(path), self.life)
            game.delete_save(path)
            self.assertIsNone(game.load(path))


class UiTest(unittest.TestCase):
    def test_wrap_respects_double_width(self):
        text = "당신은 부산의 작은 병원에서 태어났다. " * 10 + "띄어쓰기없이아주길게이어지는낱말" * 5
        for line in ui.wrap(text, 30):
            self.assertLessEqual(ui.width(line), 30)


class StorytellerTest(unittest.TestCase):
    def test_request_shape(self):
        client = FakeClient()
        teller = Storyteller(client, model="claude-opus-5-5", effort="low")
        life = game.new_life("준서", "남", random.Random(1))
        out = teller.turn(life, "공부한다", ["첫사랑이 찾아왔다"])
        self.assertEqual(out["summary"], "평범한 한 해")
        request = client.requests[0]
        self.assertEqual(request["fallbacks"], "default")
        self.assertEqual(request["output_config"]["effort"], "low")
        prompt = request["messages"][0]["content"]
        self.assertIn("공부한다", prompt)
        self.assertIn("첫사랑이 찾아왔다", prompt)
        self.assertIn("자연스럽게 걸리는 만큼", prompt)
        teller.turn(life, "특별히 하는 일 없이", [], skip_years=3)
        self.assertIn("months_passed는 정확히 36", client.requests[1]["messages"][0]["content"])

    def test_no_fallbacks_for_other_models(self):
        client = FakeClient()
        Storyteller(client, model="claude-haiku-5-5").opening(game.new_life("a", "여"))
        self.assertNotIn("fallbacks", client.requests[0])

    def test_refusal_raises_story_error(self):
        client = FakeClient([SimpleNamespace(stop_reason="refusal", content=[])])
        with self.assertRaises(StoryError):
            Storyteller(client).opening(game.new_life("a", "여"))


class PlayTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        patcher = mock.patch.object(game, "SAVE_PATH", Path(tmp.name) / "save.json")
        patcher.start()
        self.addCleanup(patcher.stop)

    def run_with(self, inputs, client):
        with mock.patch("builtins.input", side_effect=inputs), redirect_stdout(io.StringIO()) as out:
            app.run(Storyteller(client), random.Random(3))
        return out.getvalue()

    def test_play_save_resume_and_die(self):
        client = FakeClient()
        self.run_with(["준서", "남", *[""] * 5, "공부를 열심히 한다", "/넘기기 3", "/상태", "/종료"], client)
        life = game.load()
        self.assertEqual((life.months, life.age), (42, 3))  # 태어남 → 6개월 (이야기꾼이 정함) → 3년 넘기기
        self.assertEqual(len(life.chronicle), 3)
        self.assertIn("공부를 열심히 한다", client.requests[1]["messages"][0]["content"])

        # 이어 하기: 이번 턴에 세상을 떠나면 엔딩을 보여 주고 저장을 지운다
        client = FakeClient([turn_out(died=True, cause_of_death="사고")])
        output = self.run_with(["", "번지점프를 한다"], client)
        self.assertIn("부고", output)
        self.assertIn("평범한 사람", output)
        self.assertIsNone(game.load())

    def test_failed_turn_does_not_advance(self):
        client = FakeClient([turn_out(), SimpleNamespace(stop_reason="refusal", content=[])])
        self.run_with(["준서", "여", *[""] * 5, "무언가 한다", "/종료"], client)
        self.assertEqual(game.load().age, 0)


if __name__ == "__main__":
    unittest.main()
