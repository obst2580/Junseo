import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  num,
  advanceDate,
  formatDate,
  formatSpan,
  rollDecisionDays,
  DECISION_DAYS,
  parseTimeSkip,
  createGame,
  validateSetup,
  computePressure,
  rollFriction,
  tierForScore,
  acceptChance,
  rollEvents,
  pickStaleAgenda,
  seededRandom,
  parseJsonLoose,
  normalizeResponse,
  normalizeExecStatus,
  validateResponse,
  guardStatus,
  applyResponse,
  formatBig,
  serialize,
  deserialize,
  worldSummary,
} from '../src/engine.js';
import { mockResponse } from '../src/llm.js';

const SETUP = {
  country: '조선',
  ruler: '태종 이방원',
  startYear: 1400,
  startMonth: 11,
  institution: '조선 초기 양반 관료제',
  difficulty: 'standard',
  tier: 'default',
};

function openedGame() {
  const s = createGame(SETUP, 1);
  const resp = normalizeResponse(mockResponse({ kind: 'opening', state: s }));
  return applyResponse(s, resp, { kind: 'opening' });
}

test('num은 한국어 수 표기를 읽는다', () => {
  assert.equal(num(1234), 1234);
  assert.equal(num('38,000'), 38000);
  assert.equal(num('약 552만 명'), 5520000);
  assert.equal(num('1억 2천만'), 120000000);
  assert.equal(num('−3만'), -30000);
  assert.equal(num('-1억2천만'), -120000000);
  assert.ok(Number.isNaN(num('없음')));
  assert.ok(Number.isNaN(num(null)));
});

test('advanceDate는 30일 달력으로 해를 넘긴다', () => {
  assert.deepEqual(advanceDate({ year: 1400, month: 11, day: 1 }, 3), { year: 1401, month: 2, day: 1 });
  assert.deepEqual(advanceDate({ year: 1400, month: 1 }, 0), { year: 1400, month: 1, day: 1 }, '일이 없던 옛 날짜는 1일');
  assert.deepEqual(advanceDate({ year: 1400, month: 12, day: 20 }, 12), { year: 1401, month: 12, day: 20 });
  assert.deepEqual(advanceDate({ year: 1400, month: 12, day: 28 }, 0, 5), { year: 1401, month: 1, day: 3 });
  assert.equal(formatDate({ year: 1400, month: 11, day: 3 }), '1400년 11월 3일');
  assert.equal(formatDate({ year: 1400, month: 11 }), '1400년 11월');
});

test('formatSpan은 경과 기간을 읽기 좋게 쓴다', () => {
  assert.equal(formatSpan(0, 4), '4일');
  assert.equal(formatSpan(1, 0), '1개월');
  assert.equal(formatSpan(15, 0), '1년 3개월');
  assert.equal(formatSpan(24, 0), '2년');
  assert.equal(formatSpan(0, 0), '같은 날');
});

test('왕명 한 번은 며칠 사이의 일이다', () => {
  const rng = seededRandom(3);
  for (const [tier, [lo, hi]] of Object.entries(DECISION_DAYS)) {
    for (let i = 0; i < 200; i++) {
      const d = rollDecisionDays(tier, rng);
      assert.ok(d >= lo && d <= hi, `${tier}: ${d}`);
    }
  }
  assert.ok(DECISION_DAYS.delayed[1] > DECISION_DAYS.smooth[1], '지연되면 논의가 길어진다');
});

test('parseTimeSkip은 시간 경과 명령만 알아본다', () => {
  const M = (months, days = 0) => ({ months, days });
  assert.deepEqual(parseTimeSkip('다음 달'), M(1));
  assert.deepEqual(parseTimeSkip('1년 후'), M(12));
  assert.deepEqual(parseTimeSkip('3년이 흐른다'), M(36));
  assert.deepEqual(parseTimeSkip('두 달 뒤'), M(2));
  assert.deepEqual(parseTimeSkip('석 달 후'), M(3));
  assert.deepEqual(parseTimeSkip('6개월 후'), M(6));
  assert.deepEqual(parseTimeSkip('내년'), M(12));
  assert.deepEqual(parseTimeSkip('100년 후'), M(240));
  assert.deepEqual(parseTimeSkip('5년 후로 넘어가자'), M(60));
  assert.deepEqual(parseTimeSkip('1년이 지났다.'), M(12));
  assert.deepEqual(parseTimeSkip('보름 후'), M(0, 15));
  assert.deepEqual(parseTimeSkip('열흘 뒤'), M(0, 10));
  assert.deepEqual(parseTimeSkip('일주일 후'), M(0, 7));
  assert.deepEqual(parseTimeSkip('3일 후'), M(0, 3));
  assert.deepEqual(parseTimeSkip('2주 뒤'), M(0, 14));
  assert.deepEqual(parseTimeSkip('45일 후'), M(1, 15));
  assert.equal(parseTimeSkip('북방에 진을 설치하라'), null);
  assert.equal(parseTimeSkip('1년 후에 세금을 올려라'), null);
  assert.equal(parseTimeSkip('호패법을 1년 후 시행하라'), null);
});

test('validateSetup은 빈 칸을 막는다', () => {
  assert.deepEqual(validateSetup(SETUP), []);
  assert.equal(validateSetup({ ...SETUP, country: '', startYear: 'abc' }).length, 2);
});

test('판정 등급 경계', () => {
  assert.equal(tierForScore(1), 'smooth');
  assert.equal(tierForScore(30), 'smooth');
  assert.equal(tierForScore(31), 'conditional');
  assert.equal(tierForScore(65), 'conditional');
  assert.equal(tierForScore(80), 'delayed');
  assert.equal(tierForScore(93), 'backlash');
  assert.equal(tierForScore(94), 'blocked');
  assert.equal(acceptChance(0), 65);
  assert.equal(acceptChance(-15), 80);
  assert.equal(acceptChance(200), 1);
});

test('민심과 국고, 연이은 성사가 조정 기류를 험하게 만든다', () => {
  const s = openedGame();
  const calm = computePressure(s);
  const angry = computePressure({
    ...s,
    status: { ...s.status, opinion: 20, treasury: -100 },
    streak: { good: 3, bad: 0 },
  });
  assert.ok(angry.mod > calm.mod);
  assert.ok(angry.reasons.includes('민심 이반'));
  assert.ok(angry.reasons.includes('국고 고갈'));
  assert.ok(angry.reasons.includes('연이은 성사에 대한 견제'));
  assert.equal(angry.label, '험악');
  const harsh = computePressure({ ...s, setup: { ...s.setup, difficulty: 'harsh' } });
  assert.equal(harsh.mod - calm.mod, 15);
});

test('표준 난이도에서 왕명은 셋 중 둘쯤 받아들여지고 나머지는 막힌다', () => {
  const s = openedGame();
  const rng = seededRandom(42);
  const counts = { smooth: 0, conditional: 0, delayed: 0, backlash: 0, blocked: 0 };
  for (let i = 0; i < 5000; i++) counts[rollFriction(s, rng).tier]++;
  const rough = (counts.delayed + counts.backlash + counts.blocked) / 5000;
  assert.ok(rough > 0.28 && rough < 0.42, `차질 비율 ${rough}`);
  assert.ok(counts.smooth > 0 && counts.blocked > 0, '순조와 좌초가 모두 나온다');
  assert.equal(computePressure(s).chance, 65);
});

test('막힌 뒤에는 타협 분위기, 미결 안건 재론에는 가산점', () => {
  const s = openedGame();
  const base = computePressure(s).mod;
  assert.equal(computePressure({ ...s, streak: { good: 0, bad: 1 } }).mod, base - 8);
  assert.equal(computePressure({ ...s, streak: { good: 1, bad: 0 } }).mod, base, '한 번 성사로는 견제가 붙지 않는다');
  assert.equal(computePressure({ ...s, streak: { good: 2, bad: 0 } }).mod, base + 3);

  const fixed = () => 0.5;
  const plain = rollFriction(s, fixed, '과거를 실시하라');
  const revisit = rollFriction(s, fixed, '변경 방비 강화 안건을 다시 논의하라');
  assert.equal(plain.mod - revisit.mod, 10);
  assert.ok(revisit.reasons.some((r) => r.includes('변경 방비 강화')));
  // 이미 결정된 정책은 재론 가산점이 없다
  const done = { ...s, policies: s.policies.map((p) => ({ ...p, status: '시행' })) };
  assert.equal(rollFriction(done, fixed, '변경 방비 강화 안건을 다시 논의하라').mod, plain.mod);
});

test('시간 경과가 길수록 돌발 사건이 많다', () => {
  const s = openedGame();
  const rng = seededRandom(7);
  let short = 0;
  let long = 0;
  for (let i = 0; i < 500; i++) {
    short += rollEvents(s, 'time', 1, rng).length;
    long += rollEvents(s, 'time', 60, rng).length;
  }
  assert.ok(long > short * 3);
  const many = rollEvents(s, 'time', 120, rng);
  assert.equal(new Set(many.map((e) => e.id)).size, many.length, '같은 범주가 겹치지 않는다');
});

test('오래 묵은 미결 안건을 골라낸다', () => {
  const s = openedGame();
  s.turnCount = 10;
  s.policies = [{ name: '변경 방비 강화', status: '논의중', updatedTurn: 2 }];
  const always = () => 0.1;
  assert.deepEqual(pickStaleAgenda(s, always), { name: '변경 방비 강화', waited: 8, status: '논의중' });
  s.policies[0].updatedTurn = 9;
  assert.equal(pickStaleAgenda(s, always), null);
});

test('parseJsonLoose는 코드 블록과 앞뒤 문장을 견딘다', () => {
  assert.deepEqual(parseJsonLoose('{"a":1}'), { a: 1 });
  assert.deepEqual(parseJsonLoose('```json\n{"a":2}\n```'), { a: 2 });
  assert.deepEqual(parseJsonLoose('기록입니다.\n{"a":3}\n이상.'), { a: 3 });
  assert.throws(() => parseJsonLoose('형식이 없음'), (e) => e.code === 'invalid_json');
});

test('실행 결과 표기를 정리한다', () => {
  assert.equal(normalizeExecStatus('조건부 윤허'), '조건부 시행');
  assert.equal(normalizeExecStatus('좌초'), '반려');
  assert.equal(normalizeExecStatus('논의 지연'), '보류');
  assert.equal(normalizeExecStatus('시행 후 반발'), '시행 후 반발');
  assert.equal(normalizeExecStatus(''), '해당없음');
});

test('충돌 응답은 conflict만 남긴다', () => {
  const r = normalizeResponse({ conflict: { summary: '이미 시행된 제도입니다.', question: '재도입하시겠습니까?' } });
  assert.deepEqual(r, { conflict: { summary: '이미 시행된 제도입니다.', question: '재도입하시겠습니까?' } });
  assert.equal(normalizeResponse({ conflict: null, record: ['서술'] }).conflict, null);
});

test('사망한 인물의 발언을 잡아낸다', () => {
  const s = openedGame();
  s.characters = s.characters.map((c) => (c.name === '하경복' ? { ...c, status: '사망' } : c));
  const resp = normalizeResponse({
    opening: '논의합니다...',
    record: [{ speaker: '영의정 하경복', title: '영의정', text: '아뢰옵니다.' }],
    status: s.status,
  });
  const issues = validateResponse(s, resp);
  assert.ok(issues.some((i) => i.level === 'high' && i.text.includes('하경복')));
  // 같은 응답에서 되살린다고 밝히면 오류가 아니다
  const revived = normalizeResponse({
    opening: '논의합니다...',
    record: [{ speaker: '하경복', text: '아뢰옵니다.' }],
    updates: { characters: [{ name: '하경복', status: '재직', note: '사망 소식은 오보였다' }] },
  });
  assert.ok(!validateResponse(s, revived).some((i) => i.level === 'high'));
});

test('첫 상태창이 비면 고쳐 쓰게 한다', () => {
  const s = createGame(SETUP, 1);
  const resp = normalizeResponse({ opening: 'x', record: ['y'], status: { population: 100 } });
  assert.ok(validateResponse(s, resp).some((i) => /첫 상태창에 국민 여론/.test(i.text)));
});

test('guardStatus는 기간에 비해 과한 변동을 보정한다', () => {
  const prev = { population: 5000000, allies: ['명'], enemies: [], opinion: 50, happiness: 50, treasury: 1000, gdp: 100000 };
  const next = { population: 9000000, allies: ['명', '여진'], enemies: ['여진'], opinion: 95, happiness: 49, treasury: NaN, gdp: 100000 };
  const { status, notes } = guardStatus(prev, next, 0, '한 차례 회의');
  assert.equal(status.opinion, 62);
  assert.equal(status.population, 5100000);
  assert.equal(status.treasury, 1000, '빠진 수치는 이전 값을 지킨다');
  assert.deepEqual(status.allies, ['명']);
  assert.deepEqual(status.enemies, ['여진']);
  assert.equal(notes.length, 3);
  // 긴 시간이 흐르면 더 큰 변화가 허용된다
  const decade = guardStatus(prev, { ...next, opinion: 80, population: 6000000 }, 120, '10년');
  assert.equal(decade.status.opinion, 80);
  assert.equal(decade.status.population, 6000000);
});

test('applyResponse는 기록을 쌓고 연속 판정을 센다', () => {
  let s = openedGame();
  assert.equal(s.turnCount, 1);
  assert.ok(s.status.population > 0);
  assert.deepEqual(s.units, { treasury: '석', gdp: '석' });
  assert.ok(s.characters.length >= 3);
  assert.ok(s.factions.length >= 3);
  assert.equal(s.policies[0].status, '논의중');

  const ctx = { kind: 'command', command: '사병을 혁파하라', roll: { tier: 'smooth', reasons: [] }, days: 2, events: [] };
  s = applyResponse(s, normalizeResponse(mockResponse({ ...ctx, state: s })), ctx);
  assert.equal(s.streak.good, 1);
  assert.deepEqual(s.date, { year: 1400, month: 11, day: 3 }, '왕명은 엔진이 정한 날수만큼만 흐른다');
  const t = s.turns[s.turns.length - 1];
  assert.equal(t.execution.status, '시행');
  assert.ok(t.deltas, '두 번째 기록부터 변동이 있다');
  assert.ok(s.characters.find((c) => c.name === '하경복').quotes.length > 0, '발언을 인물 기억에 남긴다');

  const bad = { ...ctx, command: '세금을 두 배로 올려라', roll: { tier: 'blocked', reasons: [] }, days: 3 };
  s = applyResponse(s, normalizeResponse(mockResponse({ ...bad, state: s })), bad);
  assert.deepEqual(s.streak, { good: 0, bad: 1 });
  const policy = s.policies.find((p) => p.name === '세금을 두 배로 올려라');
  assert.equal(policy.status, '좌초');

  assert.deepEqual(s.date, { year: 1400, month: 11, day: 6 });
  const time = { kind: 'time', months: 24, days: 0, roll: { tier: 'conditional', reasons: [] }, events: [] };
  s = applyResponse(s, normalizeResponse(mockResponse({ ...time, state: s })), time);
  assert.deepEqual(s.date, { year: 1402, month: 11, day: 6 });
  assert.equal(s.turns[s.turns.length - 1].months, 24);
  assert.deepEqual(s.streak, { good: 0, bad: 1 }, '시간 경과는 연속 판정에 들지 않는다');
  assert.equal(s.chronicle.length, 4);
});

test('세력 지지도는 한 번에 크게 뒤집히지 않는다', () => {
  const s = openedGame();
  const resp = normalizeResponse({
    opening: 'x',
    record: ['y'],
    status: s.status,
    updates: { factions: [{ name: '종친', support: 0 }] },
  });
  const next = applyResponse(s, resp, { kind: 'command', command: 'z', roll: { tier: 'backlash', reasons: [] } });
  assert.equal(next.factions.find((f) => f.name === '종친').support, 44 - 15);
});

test('저장 기록을 주고받는다', () => {
  const s = openedGame();
  const back = deserialize(serialize(s));
  assert.deepEqual(back, s);
  assert.throws(() => deserialize('{"hello":1}'));
  assert.match(worldSummary(s), /국가: 조선/);
});

test('수치 표기', () => {
  assert.equal(formatBig(5520000), '552만');
  assert.equal(formatBig(123456789), '1.23억');
  assert.equal(formatBig(-38000), '−3.8만');
  assert.equal(formatBig(950), '950');
});

test('Claude가 몇 달을 흘려보내려 해도 왕명 열 번에 두 달이 넘지 않는다', () => {
  let s = openedGame();
  const start = s.date;
  const rng = seededRandom(11);
  for (let i = 0; i < 10; i++) {
    const roll = rollFriction(s, rng);
    const ctx = { kind: 'command', command: `명령 ${i}`, roll, days: rollDecisionDays(roll.tier, rng), events: [] };
    const raw = { ...mockResponse({ ...ctx, state: s }), elapsedMonths: 3 };
    s = applyResponse(s, normalizeResponse(raw), ctx);
  }
  const elapsed = (s.date.year - start.year) * 360 + (s.date.month - start.month) * 30 + (s.date.day - start.day);
  assert.ok(elapsed >= 10 && elapsed <= 60, `${elapsed}일`);
});

test('일이 없는 옛 저장 기록도 불러온다', () => {
  const s = openedGame();
  const old = JSON.parse(serialize(s));
  old.date = { year: 1400, month: 11 };
  assert.deepEqual(deserialize(JSON.stringify(old)).date, { year: 1400, month: 11, day: 1 });
});
