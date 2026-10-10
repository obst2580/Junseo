import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createGame, applyResponse, normalizeResponse, advanceDate } from '../src/engine.js';
import { buildPrompt } from '../src/prompt.js';
import kingdom from '../src/modes/kingdom.js';

const { outcomes: OUTCOMES, scenarios: SCENARIOS } = kingdom;
import { mockResponse } from '../src/llm.js';

function game() {
  const sc = SCENARIOS.find((s) => s.id === 'joseon-taejong');
  const s = createGame({ ...sc, scenarioId: sc.id, difficulty: 'standard', tier: 'default' }, 1);
  return applyResponse(s, normalizeResponse(mockResponse({ kind: 'opening', state: s })), { kind: 'opening' });
}

test('첫 조회 프롬프트는 단위와 초기 인물을 정하게 한다', () => {
  const sc = SCENARIOS[0];
  const s = createGame({ ...sc, difficulty: 'mild' }, 1);
  const p = buildPrompt(s, { kind: 'opening' });
  assert.match(p, /\[세계 기록 — 고정 설정\]/);
  assert.match(p, /국가: 고려/);
  assert.match(p, /첫 기록이다/);
  assert.match(p, /"units"/);
  assert.match(p, /조정의 협조 성향: 온건/);
  assert.match(p, /이번 턴에는 conflict를 쓰지 않는다/);
  assert.doesNotMatch(p, /\[플레이어의 명령/);
});

test('명령 프롬프트는 판정, 사건, 명령, 충돌 규칙을 담는다', () => {
  const s = game();
  const ctx = {
    kind: 'command',
    command: '호패법을 시행하라',
    roll: { tier: 'backlash', reasons: ['민심 동요'] },
    events: [{ id: 'plague', label: '역병', hint: '돌림병' }],
    agenda: { name: '변경 방비 강화', waited: 6, status: '논의중' },
  };
  const p = buildPrompt(s, { ...ctx, days: 4, target: advanceDate(s.date, 0, 4) });
  assert.ok(p.includes(OUTCOMES.backlash.directive));
  assert.match(p, /1400년 11월 1일부터 1400년 11월 5일까지 4일 사이의 일이다/);
  assert.match(p, /앞당겨 쓰지 않는다/);
  assert.doesNotMatch(p, /elapsedMonths/, '흐르는 시간은 Claude가 정하지 않는다');
  assert.match(p, /판정의 배경으로 드러낼 사정: 민심 동요/);
  assert.match(p, /돌발 사건 \[역병\]/);
  assert.match(p, /장기 미결 안건 "변경 방비 강화"\(6턴째 논의중\)/);
  assert.match(p, /\[플레이어의 명령[^\]]*\]\n호패법을 시행하라/);
  assert.match(p, /\{"conflict": \{"summary"/);
  assert.match(p, /\[인물 명부\]\n- /);
  assert.match(p, /\[직전 기록 원문\]\n제1조/);
  assert.match(p, /단위\(변경 금지\): 재정 석, GDP 석/);
  assert.doesNotMatch(p, /"units"/, '단위는 첫 조회에서만 정한다');
});

test('충돌 해소 방침과 고쳐 쓰기 지시가 들어간다', () => {
  const s = game();
  const base = { kind: 'command', command: 'x', roll: { tier: 'smooth', reasons: [] }, events: [] };
  assert.match(buildPrompt(s, { ...base, resolution: 'keep' }), /기존 설정을 기준으로 진행한다/);
  assert.match(buildPrompt(s, { ...base, resolution: 'override' }), /현재 지시를 우선한다/);
  assert.match(buildPrompt(s, { ...base, correction: ['하륜은 이미 사망했다'] }), /\[고쳐 쓰기\][\s\S]*- 하륜은 이미 사망했다/);
});

test('시간 경과와 재동기화 프롬프트', () => {
  const s = game();
  const p = buildPrompt(s, {
    kind: 'time',
    months: 24,
    days: 0,
    target: advanceDate(s.date, 24),
    roll: { tier: 'delayed', reasons: [] },
    events: [],
  });
  assert.match(p, /1400년 11월 1일부터 1402년 11월 1일까지 2년이 흐른다/);
  assert.match(p, /기존 안건들의 진척 판정: 지연/);
  const r = buildPrompt(s, { kind: 'resync', resyncText: '하경복은 좌의정이다' });
  assert.match(r, /\[플레이어의 세계관 요약\]\n하경복은 좌의정이다/);
});

test('긴 게임에서도 프롬프트가 sample 한도(256KiB) 안에 머문다', () => {
  let s = game();
  for (let i = 0; i < 150; i++) {
    const ctx = { kind: 'command', command: `명령 ${i}번째 — 각 도의 수령에게 진휼을 명하라`, roll: { tier: 'conditional', reasons: [] }, events: [] };
    s = applyResponse(s, normalizeResponse(mockResponse({ ...ctx, state: s })), ctx);
  }
  const bytes = Buffer.byteLength(buildPrompt(s, { kind: 'command', command: 'x', roll: { tier: 'smooth', reasons: [] }, events: [] }));
  assert.ok(bytes < 200 * 1024, `${bytes} bytes`);
});

test('시나리오는 모두 필수 칸을 갖춘다', () => {
  for (const sc of SCENARIOS) {
    for (const k of ['id', 'country', 'ruler', 'startYear', 'startMonth', 'title', 'blurb', 'institution']) {
      assert.ok(sc[k], `${sc.id}.${k}`);
    }
    assert.ok(sc.startMonth >= 1 && sc.startMonth <= 12);
  }
});
