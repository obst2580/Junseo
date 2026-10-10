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
import business, { companyStage } from '../src/modes/business.js';
import kingdom from '../src/modes/kingdom.js';

function company(scenarioId = 'bakery-2020') {
  const sc = business.scenarios.find((s) => s.id === scenarioId);
  const s = createGame({ ...sc, mode: 'business', scenarioId: sc.id, difficulty: 'standard', tier: 'default' }, 1);
  return applyResponse(s, normalizeResponse(mockResponse({ kind: 'opening', state: s })), { kind: 'opening' });
}

const ROLL = (tier) => ({ tier, reasons: [] });

// 결정 한 번 (days일). raw로 Claude 응답을 덮어쓸 수 있다
function decide(s, days, raw = {}) {
  const ctx = { kind: 'command', command: '배달 앱에 입점해 본다', roll: ROLL('conditional'), days, events: [] };
  return applyResponse(s, normalizeResponse({ ...mockResponse({ ...ctx, state: s }), ...raw }), ctx);
}

test('창업에서 시작한다: 작은 팀, 창업 자금, 적자', () => {
  const s = company();
  assert.equal(s.setup.mode, 'business');
  assert.equal(modeOf(s), business);
  assert.deepEqual(s.units, { currency: '원' });
  assert.deepEqual(
    new Set(Object.keys(s.status)),
    new Set(['cash', 'revenue', 'costs', 'customers', 'members', 'satisfaction', 'morale', 'competitors', 'profit'])
  );
  assert.equal(s.status.cash, 38000000, '첫 기록의 현금은 창업 자금');
  assert.equal(s.status.profit, -2500000, '월 손익 = 월 매출 − 월 비용 (엔진 계산)');
  assert.equal(s.status.members, 2);
  assert.ok(!s.characters.some((c) => /임원|이사|CFO|본부장/.test(c.title)), '창업 첫날에는 임원이 없다');
  for (const sc of business.scenarios) assert.ok(!/직원 약|명의 .*회사/.test(sc.institution), `${sc.id}는 창업 시점이다`);
});

test('설정 화면의 난이도 선택이 없고, 오류 문구는 창업에 맞춘다', () => {
  assert.equal(business.setupOptions, false);
  assert.equal(kingdom.setupOptions, true);
  const errors = validateSetup({ mode: 'business', country: '', ruler: '', startYear: 2020, institution: '' });
  assert.deepEqual(errors, [
    '회사(가게) 이름을 입력하십시오.',
    '창업자(플레이어)의 이름이나 호칭을 입력하십시오.',
    '사업 아이템과 시작 조건을 한 줄 이상 적어 주십시오.',
  ]);
});

test('회사 단계는 팀 크기로 정해진다', () => {
  assert.equal(companyStage(1), '1인 창업');
  assert.equal(companyStage(2), '초기 팀');
  assert.equal(companyStage(12), '스타트업');
  assert.equal(companyStage(80), '중소기업');
  assert.equal(companyStage(500), '중견기업');
  assert.equal(business.status.fields.find((f) => f.key === 'members').note({ members: 1 }), '1인 창업');
});

test('프롬프트는 회사 단계를 알리고, 현금 대신 목돈만 묻는다', () => {
  const s = company();
  const ctx = {
    kind: 'command',
    command: '배달 앱에 입점해 본다',
    roll: { tier: 'conditional', reasons: ['지친 팀'] },
    days: 3,
    target: { year: 2020, month: 2, day: 4 },
    events: [{ id: 'competitor', label: '경쟁자 등장', hint: '근처에 비슷한 가게 개업' }],
    crises: [{ id: 'cash', label: '현금 바닥', directive: '현금이 마이너스다.' }],
    settlement: '2020년 1분기',
  };
  const p = buildPrompt(s, ctx);
  assert.match(p, /플레이어는 맨손으로 회사를 차린 창업자다/);
  assert.match(p, /회사 단계: 초기 팀\(팀 2명\) — 지금 회사에 실제로 있는 사람만 등장한다/);
  assert.match(p, /"status": \{"cash": 0, "revenue": 0, "costs": 0, "customers": 0, "members": 0, "satisfaction": 0, "morale": 0, "competitors": \["경쟁자 이름"\]\},\n  "cashFlow": 0,/);
  assert.match(p, /cashFlow는 이번 기록에서 생긴 일회성 현금이다/);
  assert.match(p, /현금이 마이너스인 채로 다음 기록을 맞으면 회사는 폐업한다/);
  assert.match(p, /경보 \[현금 바닥\]: 현금이 마이너스다\./);
  assert.match(p, /분기 결산: 2020년 1분기가 마감되었다\. record 끝에 창업자가 장부를 정리하는 장면/);
  assert.match(p, /\[플레이어의 결정[^\]]*\]\n배달 앱에 입점해 본다/);
  assert.match(p, /상태: 현금 3800만 원, 월 손익 −250만 원, 팀 사기 62/);
  assert.doesNotMatch(p, /profit는/, '월 손익은 엔진이 계산하므로 묻지 않는다');
  assert.doesNotMatch(p, /임원진과|이사회가 사사건건|대표 지시|회의록/);
  for (const word of ['군주', '사관', '왕명', '관료', '민심', '조정 회의', '조정은', '조정의', '조정에서']) {
    assert.ok(!p.includes(word), `사업 프롬프트에 "${word}"가 있다`);
  }
});

test('현금은 엔진이 월 손익과 시간으로 계산한다', () => {
  let s = company();
  // Claude가 현금을 마음대로 적어도 무시된다
  s = decide(s, 6, { status: { ...mockResponse({ kind: 'command', command: 'x', roll: ROLL('smooth'), state: s }).status, cash: 999 } });
  const profit = 9000000 - 11800000; // 결정 한 번에 가짜 기록자가 비용을 30만 늘린다
  assert.equal(s.status.profit, profit);
  assert.equal(s.status.cash, Math.round(38000000 + ((-2500000 + profit) / 2) * (6 / 30)));
  assert.equal(s.turns.at(-1).deltas.cash, s.status.cash - 38000000);

  // 목돈(대출)은 cashFlow로 들어온다
  const before = s.status.cash;
  s = decide(s, 0, { cashFlow: 30000000 });
  assert.equal(s.status.cash, Math.round(before + 30000000));
  assert.equal(s.turns.at(-1).cashFlow, 30000000);

  // 회사 규모에 비해 터무니없는 목돈은 상한까지만
  const c2 = s.status.cash;
  s = decide(s, 0, { cashFlow: 5e10 });
  const ceiling = 36 * Math.max(s.status.costs, s.status.revenue);
  assert.equal(s.status.cash, c2 + ceiling);
  assert.ok(s.turns.at(-1).issues.some((i) => i.text.startsWith('일회성 현금 유입이')));
});

test('현금이 마이너스인 채로 다음 기록을 맞으면 폐업한다', () => {
  let s = company();
  s = { ...s, status: { ...s.status, cash: -1000000 } };
  assert.deepEqual(activeCrises(s).map((c) => c.id), ['cash']);
  // 돈을 구하면 산다
  const saved = decide(s, 2, { cashFlow: 20000000 });
  assert.equal(saved.over, null);
  // 못 구하면 문을 닫는다
  const closed = decide(s, 2);
  assert.ok(closed.over);
  assert.match(closed.over.reason, /문을 닫았습니다/);
  assert.equal(closed.best.members, 2);
  assert.equal(business.ui.overStats(closed, (v) => String(v)), `창업 ${closed.turnCount}번째 기록에서 문을 닫았습니다. 가장 많았던 팀 2명, 가장 높았던 월 매출 9000000.`);
  // 국가의 시대에는 끝이 없다
  assert.equal(kingdom.status.gameOver, null);
});

test('판정은 팀 사기, 현금 사정, 고객 만족을 본다', () => {
  const s = company();
  const base = { cash: 5e7, revenue: 1e7, costs: 8e6, profit: 2e6, customers: 100, members: 3, satisfaction: 60, morale: 50, competitors: [] };
  const reasons = (st) => computePressure({ ...s, status: { ...base, ...st }, streak: { good: 0, bad: 0 } }).reasons;
  assert.deepEqual(reasons({}), ['흑자 운영']);
  assert.deepEqual(reasons({ morale: 20 }), ['번아웃 직전', '흑자 운영']);
  assert.deepEqual(reasons({ morale: 75 }), ['팀의 열기', '흑자 운영']);
  assert.deepEqual(reasons({ cash: -1 }), ['현금 바닥']);
  assert.deepEqual(reasons({ profit: -2e7 }), ['자금 압박(버틸 기간 3개월 미만)']);
  assert.deepEqual(reasons({ profit: -1e6 }), ['적자 운영']);
  assert.deepEqual(reasons({ satisfaction: 20 }), ['흑자 운영', '고객 불만']);
  const start = computePressure(s);
  assert.equal(start.chance, 63, '갓 창업한 적자 가게는 63%');
});

test('경보: 현금 바닥, 폐업 위기, 번아웃, 고객 이탈', () => {
  const s = company();
  const st = (x) => ({ ...s, status: { ...s.status, ...x } });
  assert.deepEqual(activeCrises(s).map((c) => c.id), []);
  assert.deepEqual(activeCrises(st({ cash: 4e6, profit: -2.5e6 })).map((c) => c.id), ['runway'], '두 달 안에 바닥나면 폐업 위기');
  assert.deepEqual(activeCrises(st({ morale: 10, satisfaction: 10 })).map((c) => c.id), ['morale', 'customers']);
  const kingdomState = { ...s, setup: { ...s.setup, mode: 'kingdom' } };
  assert.deepEqual(activeCrises(kingdomState), [], '국가의 시대에는 경보가 없다');
});

test('현금 칸은 버틸 기간을 알려 준다', () => {
  const cash = business.status.fields.find((f) => f.key === 'cash');
  assert.equal(cash.note({ cash: 38e6, profit: -2.5e6 }), '버틸 기간 약 15.2개월');
  assert.equal(cash.note({ cash: 3e6, profit: -2e6 }), '버틸 기간 약 1.5개월');
  assert.equal(cash.note({ cash: -1, profit: -1 }), '현금 마이너스 — 다음 기록까지 못 메우면 폐업');
  assert.equal(cash.note({ cash: 1e9, profit: 1 }), '흑자 운영');
});

test('사건은 창업 사건만 나온다', () => {
  const s = company();
  const ids = new Set(business.events.map((e) => e.id));
  const rng = seededRandom(5);
  for (let i = 0; i < 200; i++) {
    for (const e of rollEvents(s, 'time', 60, rng)) assert.ok(ids.has(e.id), e.id);
  }
});

test('작은 회사는 빨리 자라고 빨리 줄어든다', () => {
  const prev = { cash: 1e7, revenue: 1000, costs: 900, customers: 100, members: 2, satisfaction: 50, morale: 50, competitors: [] };
  const month = guardStatus(prev, { ...prev, revenue: 1800, customers: 190, members: 3 }, 1, '1개월', business);
  assert.equal(month.status.revenue, 1800, '한 달에 매출이 80% 늘 수 있다');
  assert.equal(month.status.customers, 190);
  assert.equal(month.status.members, 3);
  const days = guardStatus(prev, { ...prev, customers: 10000 }, 0.1, '3일', business);
  assert.equal(days.status.customers, 154, '며칠 사이 고객은 절반 남짓까지');
  assert.match(days.notes[0], /^고객 변동 폭이/);
  // 처음 0이던 매출은 제한 없이 생긴다
  assert.equal(guardStatus({ ...prev, revenue: 0 }, { ...prev, revenue: 5e6 }, 0.1, '3일', business).status.revenue, 5e6);
});

test('분기가 넘어가면 결산하고 장부에 남긴다', () => {
  assert.equal(settlementLabel({ year: 2020, month: 3, day: 28 }, { year: 2020, month: 4, day: 2 }), '2020년 1분기');
  assert.deepEqual(spanToNextQuarter({ year: 2020, month: 2, day: 5 }), { months: 1, days: 26 });
  assert.deepEqual(parseTimeSkip('다음 분기'), { months: 3, days: 0 });
  assert.deepEqual(parseTimeSkip('다음 주'), { months: 0, days: 7 });

  let s = company();
  const span = spanToNextQuarter(s.date);
  const time = { kind: 'time', ...span, roll: ROLL('smooth'), events: [] };
  s = applyResponse(s, normalizeResponse(mockResponse({ ...time, state: s })), time);
  assert.deepEqual(s.date, { year: 2020, month: 4, day: 1 });
  assert.equal(s.turns.at(-1).settlement, '2020년 1분기');
  assert.deepEqual(Object.keys(s.ledger[0].values), ['revenue', 'costs', 'profit', 'cash', 'customers', 'members']);
  assert.equal(s.status.cash, Math.round(38000000 - 2500000 * 2), '두 달 적자만큼 현금이 줄었다');
});

test('회사 요약과 과제 표기', () => {
  const s = decide(company(), 4);
  const summary = worldSummary(s);
  assert.match(summary, /회사: 밀알베이커리 \/ 창업자: 창업자/);
  assert.match(summary, /현금 3764\.7만 원, 월 손익 −280만 원, 월 매출 900만 원, 월 비용 1180만 원, 고객 300, 팀 2명/);
  assert.match(summary, /경쟁자: 프랜차이즈 빵집, 편의점 베이커리/);
  assert.equal(business.policyDisplay[s.policies.find((p) => p.name.startsWith('배달 앱에')).status], '실행');
  assert.equal(business.execDisplay['조건부 시행'], '일부 실행');
});

test('다른 게임의 저장 기록은 불러오지 않는다', () => {
  const s = company();
  assert.equal(deserialize(serialize(s), 'business').setup.mode, 'business');
  assert.throws(() => deserialize(serialize(s), 'kingdom'), /기업의 시대의 저장 기록입니다/);
  assert.notEqual(business.saveKey, 'gieop.save.v1', '임원 회의판 기록과 섞이지 않는다');
});

test('오류 문구는 기록자 이름에 맞는 조사를 쓴다', () => {
  assert.match(errorMessage({ code: 'refused' }, '사관'), /^사관이 /);
  assert.match(errorMessage({ code: 'refused' }, '기록자'), /^기록자가 /);
  assert.match(errorMessage({ code: 'upstream_error' }, '기록자'), /^기록자와의 연결/);
});

test('두 모드는 같은 모양의 문구와 규칙을 갖춘다', () => {
  const shape = (o) =>
    Object.fromEntries(
      Object.entries(o).map(([k, v]) => [k, v && typeof v === 'object' && !Array.isArray(v) ? shape(v) : Array.isArray(v) ? 'array' : typeof v])
    );
  for (const key of ['ui', 'summary', 'pressureReasons', 'difficulty', 'outcomes', 'features', 'ledger']) {
    assert.deepEqual(shape(business[key]), shape(kingdom[key]), key);
  }
  const keys = (o) => Object.keys(o).sort();
  assert.deepEqual(keys(business.prompt), keys(kingdom.prompt));
  assert.deepEqual(keys(business.prompt.output), keys(kingdom.prompt.output));
  assert.deepEqual(keys(business.status), keys(kingdom.status));
  for (const m of [kingdom, business]) {
    assert.equal(m.scenarios.length, 6);
    assert.ok(m.scenarios.some((s) => s.id === m.defaultScenario));
    for (const f of m.status.fields) assert.ok(f.key && f.label && f.type && f.legend, `${m.id}.${f.key}`);
    assert.ok(m.timeChips.length >= 3);
  }
});
