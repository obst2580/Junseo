import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  createGame,
  validateSetup,
  normalizeResponse,
  applyResponse,
  computePressure,
  rollEvents,
  guardStatus,
  worldSummary,
  serialize,
  deserialize,
  seededRandom,
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

test('사업 모드로 새 게임을 만든다', () => {
  const s = company();
  assert.equal(s.setup.mode, 'business');
  assert.equal(modeOf(s), business);
  assert.deepEqual(s.units, { treasury: '원', gdp: '원' });
  assert.equal(s.status.population, 120);
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

test('사업 프롬프트에는 왕조의 말이 섞이지 않는다', () => {
  const s = company();
  const ctx = {
    kind: 'command',
    command: '배달 앱 입점을 검토하세요',
    roll: { tier: 'conditional', reasons: ['고객 불만 고조'] },
    days: 3,
    target: { year: 2020, month: 2, day: 4 },
    events: [{ id: 'competitor', label: '경쟁사 공세', hint: '가격 인하' }],
    agenda: null,
  };
  const p = buildPrompt(s, ctx);
  assert.match(p, /기업의 시대/);
  assert.match(p, /플레이어는 이 회사의 대표다/);
  assert.match(p, /회사: 밀알베이커리/);
  assert.match(p, /\[임원·인물 명부\]/);
  assert.match(p, /\[이해관계자 지지도\]/);
  assert.match(p, /임원진이 예산 축소/);
  assert.match(p, /\[플레이어의 지시[^\]]*\]\n배달 앱 입점을 검토하세요/);
  assert.match(p, /단위\(변경 금지\): 현금 원, 연 매출 원/);
  assert.match(p, /실존 기업이나 실존 인물은 시장 환경/);
  // '구조조정', '일정 조정'은 회사 말이므로 왕조의 조정(朝廷)을 가리키는 꼴만 본다
  for (const word of ['군주', '사관', '왕명', '관료', '민심', '조정 회의', '조정은', '조정의', '조정에서']) {
    assert.ok(!p.includes(word), `사업 프롬프트에 "${word}"가 있다`);
  }
  const k = buildPrompt({ ...s, setup: { ...s.setup, mode: 'kingdom' } }, ctx);
  assert.match(k, /국가의 시대/);
  assert.match(k, /\[플레이어의 명령/);
});

test('사업 모드의 압박 사유와 사건은 회사 말로 나온다', () => {
  const s = company();
  const p = computePressure({ ...s, status: { ...s.status, opinion: 20, treasury: -1 }, streak: { good: 0, bad: 1 } });
  assert.deepEqual(p.reasons, ['고객 평판 추락', '현금 고갈', '앞선 차질 뒤의 타협 분위기']);
  const ids = new Set(business.events.map((e) => e.id));
  const rng = seededRandom(5);
  for (let i = 0; i < 200; i++) {
    for (const e of rollEvents(s, 'time', 60, rng)) assert.ok(ids.has(e.id), e.id);
  }
});

test('회사는 나라보다 빨리 커지고 줄어든다', () => {
  const prev = { population: 100, allies: [], enemies: [], opinion: 50, happiness: 50, treasury: 0, gdp: 1000 };
  const next = { ...prev, population: 180, gdp: 2500 };
  const biz = guardStatus(prev, next, 12, '1년', business);
  assert.equal(biz.status.population, 180, '스타트업은 1년에 직원이 80% 늘 수 있다');
  assert.equal(biz.status.gdp, 2500, '매출은 1년에 2.5배가 될 수 있다');
  const nation = guardStatus(prev, next, 12, '1년', kingdom);
  assert.equal(nation.status.population, 104);
  assert.match(biz.notes.join(), /^$/);
  assert.match(guardStatus(prev, { ...next, opinion: 99 }, 0, '한 차례 회의', business).notes[0], /^고객 평판 변동/);
});

test('지시 한 번과 시간 경과가 회사 기록으로 쌓인다', () => {
  let s = company();
  const ctx = { kind: 'command', command: '배달 앱 입점을 검토하세요', roll: { tier: 'backlash', reasons: [] }, days: 4, events: [] };
  s = applyResponse(s, normalizeResponse(mockResponse({ ...ctx, state: s })), ctx);
  assert.deepEqual(s.date, { year: 2020, month: 2, day: 5 });
  assert.equal(s.turns.at(-1).execution.status, '시행 후 반발');
  assert.equal(s.factions.find((f) => f.name === '가맹점주').support, 37);
  const time = { kind: 'time', months: 0, days: 15, roll: { tier: 'smooth', reasons: [] }, events: [] };
  s = applyResponse(s, normalizeResponse(mockResponse({ ...time, state: s })), time);
  assert.deepEqual(s.date, { year: 2020, month: 2, day: 20 });
  const summary = worldSummary(s);
  assert.match(summary, /회사: 밀알베이커리 \/ 대표: 대표/);
  assert.match(summary, /직원 수 120 명/);
  assert.match(summary, /현금 36\.8억 원/);
  assert.match(summary, /\[이해관계자\]/);
});

test('다른 게임의 저장 기록은 불러오지 않는다', () => {
  const s = company();
  assert.equal(deserialize(serialize(s), 'business').setup.mode, 'business');
  assert.throws(() => deserialize(serialize(s), 'kingdom'), /기업의 시대의 저장 기록입니다/);
  const old = JSON.parse(serialize(s));
  delete old.setup.mode;
  assert.equal(deserialize(JSON.stringify(old), 'kingdom').setup.mode, 'kingdom', '모드 없는 옛 기록은 국가의 시대');
});

test('오류 문구는 기록자 이름에 맞는 조사를 쓴다', () => {
  assert.match(errorMessage({ code: 'refused' }, '사관'), /^사관이 /);
  assert.match(errorMessage({ code: 'refused' }, '서기'), /^서기가 /);
  assert.match(errorMessage({ code: 'upstream_error' }, '서기'), /^서기와의 연결/);
});

test('두 모드는 같은 모양의 문구를 모두 갖춘다', () => {
  const shape = (o) =>
    Object.fromEntries(Object.entries(o).map(([k, v]) => [k, v && typeof v === 'object' && !Array.isArray(v) ? shape(v) : typeof v]));
  for (const key of ['ui', 'prompt', 'summary', 'statusLabels', 'pressureReasons', 'difficulty', 'outcomes', 'caps']) {
    assert.deepEqual(shape(business[key]), shape(kingdom[key]), key);
  }
  for (const m of [kingdom, business]) {
    assert.equal(m.scenarios.length, 6);
    assert.ok(m.scenarios.some((s) => s.id === m.defaultScenario));
  }
});
