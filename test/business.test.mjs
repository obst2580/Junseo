import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  createGame,
  validateSetup,
  normalizeResponse,
  applyResponse,
  computePressure,
  activeCrises,
  rollEvents,
  guardStatus,
  worldSummary,
  serialize,
  deserialize,
  seededRandom,
  settlementLabel,
  spanToNextQuarter,
  parseTimeSkip,
  modeOf,
} from '../src/engine.js';
import { buildPrompt } from '../src/prompt.js';
import { mockResponse, errorMessage } from '../src/llm.js';
import business from '../src/modes/business.js';
import kingdom from '../src/modes/kingdom.js';

function company(scenarioId = 'bakery-2020') {
  const sc = business.scenarios.find((s) => s.id === scenarioId);
  const s = createGame({ ...sc, mode: 'business', scenarioId: sc.id, difficulty: 'standard', tier: 'default' }, 1);
  return applyResponse(s, normalizeResponse(mockResponse({ kind: 'opening', state: s })), { kind: 'opening' });
}

const ROLL = (tier) => ({ tier, reasons: [] });

test('사업 모드는 회사 지표로 시작한다', () => {
  const s = company();
  assert.equal(s.setup.mode, 'business');
  assert.equal(modeOf(s), business);
  assert.deepEqual(s.units, { currency: '원' });
  assert.deepEqual(Object.keys(s.status), ['revenue', 'profit', 'cash', 'share', 'employees', 'satisfaction', 'morale', 'competitors']);
  assert.equal(s.status.profit, -600000000, '적자는 음수로 남는다');
  assert.equal(s.status.share, 4.2);
  assert.ok(s.characters.some((c) => c.title.includes('CFO')));
  assert.equal(createGame({ country: 'x' }, 1).setup.mode, 'kingdom', '모드가 없으면 국가의 시대');
});

test('설정 오류 문구도 회사에 맞춘다', () => {
  const errors = validateSetup({ mode: 'business', country: '', ruler: '', startYear: 2020, institution: '' });
  assert.deepEqual(errors, [
    '회사 이름을 입력하십시오.',
    '대표(플레이어)의 이름이나 직함을 입력하십시오.',
    '업종과 사업 구조를 한 줄 이상 적어 주십시오.',
  ]);
});

test('사업 프롬프트는 회사 지표를 요구하고 왕조의 말이 섞이지 않는다', () => {
  const s = company();
  const ctx = {
    kind: 'command',
    command: '배달 앱 입점을 검토하세요',
    roll: { tier: 'conditional', reasons: ['사내 불만'] },
    days: 3,
    target: { year: 2020, month: 2, day: 4 },
    events: [{ id: 'competitor', label: '경쟁사 공세', hint: '가격 인하' }],
    crises: [{ id: 'cash', label: '자금 경색', directive: '현금이 바닥났다.' }],
    settlement: '2020년 1분기',
    agenda: null,
  };
  const p = buildPrompt(s, ctx);
  assert.match(p, /플레이어는 이 회사의 대표다/);
  assert.match(p, /\[임원·인물 명부\]/);
  assert.match(p, /"status": \{"revenue": 0, "profit": 0, "cash": 0, "share": 0, "employees": 0, "satisfaction": 0, "morale": 0, "competitors": \["경쟁사 이름"\]\}/);
  assert.match(p, /profit는 연간 영업이익\(원 단위 정수, 적자면 음수\)/);
  assert.match(p, /경영 경보 \[자금 경색\]: 현금이 바닥났다\./);
  assert.match(p, /분기 결산: 2020년 1분기가 마감되었다/);
  assert.match(p, /\[플레이어의 지시[^\]]*\]\n배달 앱 입점을 검토하세요/);
  assert.match(p, /단위\(변경 금지\): 통화 원/);
  assert.match(p, /상태: 영업이익 −6억 원, 현금 38억 원, 고객 만족도 64, 직원 사기 47/);
  assert.doesNotMatch(p, /population|treasury|"gdp"|allies/);
  // '구조조정', '일정 조정'은 회사 말이므로 왕조의 조정(朝廷)을 가리키는 꼴만 본다
  for (const word of ['군주', '사관', '왕명', '관료', '민심', '조정 회의', '조정은', '조정의', '조정에서']) {
    assert.ok(!p.includes(word), `사업 프롬프트에 "${word}"가 있다`);
  }
});

test('판정은 직원 사기, 현금, 적자, 고객 만족도를 본다', () => {
  const s = company();
  const base = { revenue: 1e10, profit: 1e8, cash: 1e9, share: 5, employees: 100, satisfaction: 60, morale: 50, competitors: [] };
  const reasons = (st, streak = { good: 0, bad: 0 }) => computePressure({ ...s, status: { ...base, ...st }, streak }).reasons;
  assert.deepEqual(reasons({}), []);
  assert.deepEqual(reasons({ morale: 20 }), ['직원 사기 바닥']);
  assert.deepEqual(reasons({ morale: 75 }), ['높은 사기']);
  assert.deepEqual(reasons({ cash: -1 }), ['현금 고갈']);
  assert.deepEqual(reasons({ profit: -1.2e9, cash: 3e8 }), ['자금 압박(버틸 기간 6개월 미만)']);
  assert.deepEqual(reasons({ profit: -1.2e8 }), ['적자 경영']);
  assert.deepEqual(reasons({ satisfaction: 20 }), ['고객 이탈 조짐']);
  assert.equal(computePressure({ ...s, status: base }).chance, 65, '평범한 회사는 국가와 같은 65%');
});

test('위기에 빠지면 경영 경보가 울린다', () => {
  const s = company();
  const st = (x) => ({ ...s, status: { ...s.status, ...x } });
  assert.deepEqual(activeCrises(s).map((c) => c.id), []);
  assert.deepEqual(activeCrises(st({ cash: -5 })).map((c) => c.id), ['cash']);
  assert.deepEqual(activeCrises(st({ cash: 1e8, profit: -1.2e9 })).map((c) => c.id), ['runway'], '석 달 안에 바닥나면 부도 위기');
  assert.deepEqual(activeCrises(st({ morale: 10, satisfaction: 10 })).map((c) => c.id), ['morale', 'customers']);
  const kingdomState = { ...s, setup: { ...s.setup, mode: 'kingdom' } };
  assert.deepEqual(activeCrises(kingdomState), [], '국가의 시대에는 경보가 없다');
});

test('현금 칸은 적자일 때 버틸 기간을 알려 준다', () => {
  const cash = business.status.fields.find((f) => f.key === 'cash');
  assert.equal(cash.note({ cash: 3.8e9, profit: -6e8 }), '버틸 기간 약 76개월');
  assert.equal(cash.note({ cash: 3e8, profit: -1.2e9 }), '버틸 기간 약 3개월');
  assert.equal(cash.note({ cash: 0, profit: -1 }), '자금 경색');
  assert.equal(cash.note({ cash: 1e9, profit: 1 }), '');
});

test('사건은 회사 사건만 나온다', () => {
  const s = company();
  const ids = new Set(business.events.map((e) => e.id));
  const rng = seededRandom(5);
  for (let i = 0; i < 200; i++) {
    for (const e of rollEvents(s, 'time', 60, rng)) assert.ok(ids.has(e.id), e.id);
  }
});

test('회사는 나라보다 빨리 커지고 줄어든다', () => {
  const prev = { revenue: 1000, profit: 10, cash: 100, share: 5, employees: 100, satisfaction: 50, morale: 50, competitors: [] };
  const next = { ...prev, revenue: 2500, employees: 180, share: 9 };
  const year = guardStatus(prev, next, 12, '1년', business);
  assert.equal(year.status.employees, 180, '1년에 직원이 80% 늘 수 있다');
  assert.equal(year.status.revenue, 2500, '매출은 1년에 2.5배가 될 수 있다');
  assert.equal(year.status.share, 9, '점유율은 1년에 4%p까지 무리 없다');
  const day = guardStatus(prev, { ...next, share: 20, satisfaction: 99 }, 0.1, '3일', business);
  assert.equal(day.status.share, 6.6, '며칠 사이 점유율은 1.5%p 남짓');
  assert.deepEqual(day.notes.map((n) => n.split(' ')[0]), ['연', '시장', '직원', '고객']);
  const nation = guardStatus(
    { population: 100, allies: [], enemies: [], opinion: 50, happiness: 50, treasury: 0, gdp: 1000 },
    { population: 180 },
    12,
    '1년',
    kingdom
  );
  assert.equal(nation.status.population, 104);
});

test('분기가 넘어가면 결산하고 장부에 남긴다', () => {
  assert.equal(settlementLabel({ year: 2020, month: 3, day: 28 }, { year: 2020, month: 4, day: 2 }), '2020년 1분기');
  assert.equal(settlementLabel({ year: 2020, month: 2, day: 1 }, { year: 2020, month: 3, day: 30 }), '');
  assert.equal(settlementLabel({ year: 2020, month: 5, day: 1 }, { year: 2021, month: 2, day: 1 }), '2020년 2분기~2020년 4분기');
  assert.deepEqual(spanToNextQuarter({ year: 2020, month: 2, day: 5 }), { months: 1, days: 26 });
  assert.deepEqual(spanToNextQuarter({ year: 2020, month: 12, day: 1 }), { months: 1, days: 0 });
  assert.deepEqual(parseTimeSkip('다음 분기'), { months: 3, days: 0 });
  assert.deepEqual(parseTimeSkip('2분기 후'), { months: 6, days: 0 });
  assert.deepEqual(parseTimeSkip('다음 주'), { months: 0, days: 7 });

  let s = company();
  const span = spanToNextQuarter(s.date);
  const time = { kind: 'time', ...span, roll: ROLL('smooth'), events: [], settlement: settlementLabel(s.date, { year: 2020, month: 4, day: 1 }) };
  s = applyResponse(s, normalizeResponse(mockResponse({ ...time, state: s })), time);
  assert.deepEqual(s.date, { year: 2020, month: 4, day: 1 });
  assert.equal(s.turns.at(-1).settlement, '2020년 1분기');
  assert.equal(s.ledger.length, 1);
  assert.deepEqual(Object.keys(s.ledger[0].values), ['revenue', 'profit', 'cash', 'share', 'employees']);

  const kingdomGame = createGame({ country: '조선', ruler: '태종', startYear: 1400, startMonth: 3, institution: 'x' }, 1);
  const k = applyResponse(kingdomGame, normalizeResponse(mockResponse({ kind: 'opening', state: kingdomGame })), { kind: 'opening' });
  const k2 = applyResponse(k, normalizeResponse(mockResponse({ kind: 'time', months: 3, state: k })), { kind: 'time', months: 3 });
  assert.equal(k2.ledger.length, 0, '국가의 시대는 분기 결산을 하지 않는다');
});

test('지시 한 번이 회사 기록으로 쌓인다', () => {
  let s = company();
  const ctx = { kind: 'command', command: '배달 앱 입점을 검토하세요', roll: ROLL('backlash'), days: 4, events: [] };
  s = applyResponse(s, normalizeResponse(mockResponse({ ...ctx, state: s })), ctx);
  assert.deepEqual(s.date, { year: 2020, month: 2, day: 5 });
  assert.equal(s.turns.at(-1).execution.status, '시행 후 반발');
  assert.equal(s.turns.at(-1).deltas.cash, -120000000);
  assert.equal(s.factions.find((f) => f.name === '가맹점주').support, 37);
  const summary = worldSummary(s);
  assert.match(summary, /회사: 밀알베이커리 \/ 대표: 대표/);
  assert.match(summary, /연 매출 210억 원, 영업이익 −6억 원, 현금 36\.8억 원, 시장 점유율 4\.2%, 직원 수 120 명/);
  assert.match(summary, /경쟁사: 대형 베이커리 체인, 편의점 베이커리/);
  assert.equal(business.policyDisplay[s.policies.find((p) => p.name.startsWith('배달 앱')).status], '실행');
});

test('첫 판(나라 모양)으로 저장된 기업의 시대 기록을 회사 지표로 옮긴다', () => {
  const old = JSON.parse(serialize(company()));
  old.status = { population: 120, allies: ['밀가루 공급사'], enemies: ['대형 체인'], opinion: 52, happiness: 47, treasury: 3.8e9, gdp: 2.1e10 };
  old.units = { treasury: '원', gdp: '원' };
  old.turns[0].status = { ...old.status };
  delete old.ledger;
  const s = deserialize(JSON.stringify(old), 'business');
  assert.deepEqual(s.status, {
    revenue: 2.1e10,
    profit: 0,
    cash: 3.8e9,
    share: 0,
    employees: 120,
    satisfaction: 52,
    morale: 47,
    competitors: ['대형 체인'],
  });
  assert.deepEqual(s.units, { currency: '원' });
  assert.equal(s.turns[0].status.employees, 120);
  assert.deepEqual(s.ledger, []);
});

test('다른 게임의 저장 기록은 불러오지 않는다', () => {
  const s = company();
  assert.equal(deserialize(serialize(s), 'business').setup.mode, 'business');
  assert.throws(() => deserialize(serialize(s), 'kingdom'), /기업의 시대의 저장 기록입니다/);
  const old = JSON.parse(serialize(s));
  delete old.setup.mode;
  old.status = { population: 1, allies: [], enemies: [], opinion: 50, happiness: 50, treasury: 0, gdp: 0 };
  assert.equal(deserialize(JSON.stringify(old), 'kingdom').setup.mode, 'kingdom', '모드 없는 옛 기록은 국가의 시대');
});

test('오류 문구는 기록자 이름에 맞는 조사를 쓴다', () => {
  assert.match(errorMessage({ code: 'refused' }, '사관'), /^사관이 /);
  assert.match(errorMessage({ code: 'refused' }, '서기'), /^서기가 /);
  assert.match(errorMessage({ code: 'upstream_error' }, '서기'), /^서기와의 연결/);
});

test('두 모드는 같은 모양의 문구와 규칙을 갖춘다', () => {
  const shape = (o) =>
    Object.fromEntries(
      Object.entries(o).map(([k, v]) => [k, v && typeof v === 'object' && !Array.isArray(v) ? shape(v) : Array.isArray(v) ? 'array' : typeof v])
    );
  for (const key of ['ui', 'summary', 'pressureReasons', 'difficulty', 'outcomes', 'features', 'ledger']) {
    assert.deepEqual(shape(business[key]), shape(kingdom[key]), key);
  }
  const promptKeys = (m) => Object.keys(m.prompt).sort();
  assert.deepEqual(promptKeys(business), promptKeys(kingdom));
  assert.deepEqual(Object.keys(business.status).sort(), Object.keys(kingdom.status).sort());
  for (const m of [kingdom, business]) {
    assert.equal(m.scenarios.length, 6);
    assert.ok(m.scenarios.some((s) => s.id === m.defaultScenario));
    for (const f of m.status.fields) assert.ok(f.key && f.label && f.type && f.legend, `${m.id}.${f.key}`);
    assert.ok(m.timeChips.length >= 3);
  }
});
