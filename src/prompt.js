// Claude에게 보내는 프롬프트.
// Claude는 대화를 기억하지 않으므로 매 턴 규칙, 세계 기록, 엔진 지시, 명령을 한 번에 보낸다.
// 문구는 모드(modes/)가 정하고, 이 파일은 순서와 틀을 맞춘다.

import { modeOf, formatDate, formatSpan, formatAmount, PENDING_POLICY } from './engine.js';

function outputSpec(state, kind) {
  const P = modeOf(state).prompt;
  const O = P.output;
  const tu = state.units?.treasury || '(units에서 정한 단위)';
  const gu = state.units?.gdp || '(units에서 정한 단위)';
  const unitsLine = kind === 'opening' ? `\n  ${O.units},` : '';
  const conflictRule =
    kind === 'command'
      ? '- 명령이 [세계 기록]과 충돌하면 {"conflict": {"summary": "무엇이 어떻게 충돌하는지", "question": "플레이어에게 확인할 질문"}} 만 출력한다. 충돌이 없으면 conflict는 null.'
      : '- 이번 턴에는 conflict를 쓰지 않는다(null).';
  return `[출력 형식]
JSON 객체 하나만 출력한다. 코드 블록이나 설명 문장을 붙이지 않는다. 모든 문자열은 한국어로 쓴다.
{
  "conflict": null,
  "opening": "도입 문장",
  "record": [
    {"speaker": "", "title": "", "text": "상황 서술"},
    {"speaker": "인물 이름", "title": "직책", "text": "발언"}
  ],
  "execution": {"status": "시행 | 조건부 시행 | 보류 | 시행 후 반발 | 반려 | 해당없음", "note": "결과를 한 줄로"},
  "news": ["헤드라인", "헤드라인", "헤드라인"],
  "status": {"population": 0, "allies": ["${O.allyExample}"], "enemies": ["${O.enemyExample}"], "opinion": 0, "happiness": 0, "treasury": 0, "gdp": 0},${unitsLine}
  "eraLabel": "${O.eraLabelHint}",
  "updates": {
    "characters": [{"name": "", "title": "", "faction": "", "disposition": "성향", "status": "${O.characterStatuses}", "note": ""}],
    "policies": [{"name": "", "status": "논의중 | 시행 | 조건부 시행 | 보류 | 철회 | 좌초", "summary": "", "backlash": ""}],
    "factions": [{"name": "", "support": 0, "note": ""}],
    "regions": [{"name": "", "note": ""}]
  },
  "chronicle": "${O.chronicle}",
  "suggestions": ["다음 명령 후보", "다음 명령 후보", "다음 명령 후보"]
}
규칙:
${conflictRule}
- record는 4~8개 항목이다. 발언은 1~3문장. 서술 항목은 speaker와 title을 빈 문자열로 둔다.
- ${O.statusLegend(tu, gu)} 숫자에 단위나 쉼표를 넣지 않는다.
- 상태창은 매번 모든 항목을 쓴다. 바뀌지 않은 항목은 이전 값을 그대로 쓴다.
- updates에는 이번 턴에 새로 등장했거나 바뀐 항목만 넣는다. 없으면 빈 배열.
- 발언한 실명 인물이 인물 명부에 없으면 updates.characters에 반드시 추가한다. 인물의 신상이 바뀌면${O.characterChange} 반영한다.
- 이번 턴에 논의된 안건은 updates.policies에 판정에 맞는 status로 넣는다. 이름은 기존 기록의 이름을 그대로 쓴다.
- 세력·이해관계자 지지도(support)는 0~100 정수다.
- suggestions는 2~3개, 각 30자 이내, ${O.suggestions}로 쓴다.`;
}

function characterLine(c) {
  const parts = [c.name, c.title || '직책 미상', c.status || '재직'];
  if (c.faction) parts.push(c.faction);
  if (c.disposition) parts.push(c.disposition);
  let line = `- ${parts.join(' | ')}`;
  if (c.note) line += ` | ${c.note}`;
  const q = c.quotes?.[c.quotes.length - 1];
  if (q) line += ` | 최근 발언: "${q.text}"`;
  return line;
}

function policyLine(p, turnCount) {
  let line = `- ${p.name} | ${p.status}`;
  if (p.since) line += ` | ${formatDate(p.since)} 제기`;
  if (PENDING_POLICY.has(p.status)) line += ` | ${turnCount - (p.updatedTurn ?? 0)}턴째 미결`;
  if (p.summary) line += ` | ${p.summary}`;
  if (p.backlash) line += ` | 반발: ${p.backlash}`;
  return line;
}

export function turnText(t, state) {
  const mode = modeOf(state);
  const T = mode.prompt.turnText;
  const LBL = mode.statusLabels;
  const head =
    t.kind === 'opening'
      ? T.opening
      : t.kind === 'time'
        ? `시간 경과 ${formatSpan(t.months, t.days)}`
        : t.kind === 'resync'
          ? '기록 재동기화'
          : T.command(t.command);
  const L = [`${mode.ui.turnNo(t.n)} ${formatDate(t.date)} — ${head}`, t.opening];
  for (const r of t.record) L.push(r.speaker ? `${r.speaker}${r.title ? `(${r.title})` : ''}: ${r.text}` : r.text);
  if (t.execution && t.execution.status !== '해당없음') {
    L.push(`결과: ${t.execution.status}${t.execution.note ? ` — ${t.execution.note}` : ''}`);
  }
  if (t.news.length) L.push(`뉴스: ${t.news.join(' / ')}`);
  if (t.status && state.units) {
    L.push(
      `상태: ${LBL.opinion} ${t.status.opinion}, ${LBL.happiness} ${t.status.happiness}, ${LBL.treasury} ${formatAmount(t.status.treasury, state.units.treasury)}`
    );
  }
  return L.join('\n');
}

function worldSection(state) {
  const mode = modeOf(state);
  const W = mode.prompt.world;
  const SEC = mode.prompt.sections;
  const { setup } = state;
  const diff = mode.difficulty[setup.difficulty] || mode.difficulty.standard;
  const L = ['[세계 기록 — 고정 설정]'];
  L.push(`${W.country}: ${setup.country}`);
  L.push(`${W.ruler}: ${setup.ruler}`);
  L.push(`시작 시점: ${formatDate({ year: setup.startYear, month: setup.startMonth })}`);
  L.push(`${W.institution}: ${setup.institution}`);
  if (setup.notes) L.push(`특이사항: ${setup.notes}`);
  L.push(`${W.difficulty}: ${diff.label} — ${diff.desc}`);
  if (state.units) L.push(`단위(변경 금지): ${W.units(state.units)}`);

  L.push('', '[현재 상태]');
  L.push(`날짜: ${formatDate(state.date)}${state.eraLabel ? ` (${state.eraLabel})` : ''} · 지금까지 ${state.turnCount}번의 기록`);
  if (state.status) L.push(`상태창: ${JSON.stringify(state.status)}`);

  if (state.characters.length) {
    const list = [...state.characters].sort((a, b) => (b.updatedTurn ?? 0) - (a.updatedTurn ?? 0)).slice(0, 60);
    L.push('', SEC.people, ...list.map(characterLine));
  }
  if (state.policies.length) {
    const list = [...state.policies].sort((a, b) => (b.updatedTurn ?? 0) - (a.updatedTurn ?? 0)).slice(0, 60);
    L.push('', SEC.policies, ...list.map((p) => policyLine(p, state.turnCount)));
  }
  if (state.factions.length) {
    L.push('', SEC.factions, ...state.factions.map((f) => `- ${f.name}: ${f.support}${f.note ? ` | ${f.note}` : ''}`));
  }
  if (state.regions.length) {
    L.push('', SEC.regions, ...state.regions.slice(-30).map((r) => `- ${r.name}: ${r.note || ''}`));
  }
  if (state.chronicle.length) {
    L.push('', SEC.annals, ...state.chronicle.slice(-120).map((c) => `- ${formatDate(c.date)} ${c.text}`));
  }
  const recent = state.turns.slice(-2);
  if (recent.length) {
    L.push('', '[직전 기록 원문]', ...recent.map((t) => turnText(t, state)));
  }
  return L.join('\n');
}

function directiveSection(state, ctx) {
  const mode = modeOf(state);
  const P = mode.prompt;
  const L = ['[엔진 지시 — 플레이어에게 그대로 드러내지 말고 서사로만 반영한다]'];
  if (ctx.kind === 'opening') L.push(...P.opening);
  if (ctx.kind === 'command') {
    L.push(`- 실행 판정: ${mode.outcomes[ctx.roll.tier].directive}`);
    L.push(
      `- 경과 시간: 이번 논의와 결정은 ${formatDate(state.date)}부터 ${formatDate(ctx.target)}까지 ${ctx.days}일 사이의 일이다. 그 뒤의 일(몇 달 뒤의 성과, 결말, 계절이 바뀐 뒤의 상황)을 앞당겨 쓰지 않는다. 결정, 시행 착수, 즉각적인 반응까지만 기록하고, 오래 걸리는 결과는 시간이 흐른 뒤에 드러나도록 남겨 둔다. 상태창 수치도 며칠 사이에 있을 법한 만큼만 바꾼다.`
    );
    if (ctx.roll.reasons.length) L.push(`- 판정의 배경으로 드러낼 사정: ${ctx.roll.reasons.join(', ')}`);
    L.push(P.nonPolicy);
    if (ctx.resolution === 'keep') {
      L.push('- 충돌 해소 방침: 기존 설정을 기준으로 진행한다. 명령을 기존 기록과 모순되지 않게 해석한다(예: 재도입 대신 기존 제도의 보완이나 재천명).');
    } else if (ctx.resolution === 'override') {
      L.push('- 충돌 해소 방침: 플레이어의 현재 지시를 우선한다. 세계관 일부를 덮어쓰되 바뀐 내용을 updates와 연대기에 분명히 남긴다.');
    }
  }
  if (ctx.kind === 'time') {
    L.push(
      `- 시간 경과: ${formatDate(state.date)}부터 ${formatDate(ctx.target)}까지 ${formatSpan(ctx.months, ctx.days)}이 흐른다. ${P.timeQuiet}`,
      P.timeRecord,
      `- 기존 안건들의 진척 판정: ${mode.outcomes[ctx.roll.tier].label} — 이 기간 정책과 미결 안건이 대체로 이 등급만큼 풀리거나 막힌다.`,
      '- 상태창 수치는 기간에 비례해 현실적으로 바꾼다. eraLabel은 새 날짜에 맞춘다.',
      P.timeResult
    );
  }
  if (ctx.kind === 'resync') {
    L.push(P.resync, '- 시간은 흐르지 않는다. execution.status는 "해당없음".');
  }
  for (const e of ctx.events || []) L.push(P.event(e));
  if (ctx.agenda) L.push(P.agenda(ctx.agenda));
  if (ctx.correction?.length) {
    L.push(
      '',
      '[고쳐 쓰기] 직전 초안에서 다음 기록 오류가 발견되었다. 같은 판정과 사건을 유지하되 오류를 고쳐 처음부터 다시 쓴다.',
      ...ctx.correction.map((x) => `- ${x}`)
    );
  }
  return L.join('\n');
}

// ctx.kind: 'opening' | 'command' | 'time' | 'resync'
export function buildPrompt(state, ctx) {
  const P = modeOf(state).prompt;
  const parts = [P.rules, worldSection(state), directiveSection(state, ctx)];
  if (ctx.kind === 'command') parts.push(`${P.commandSection}\n${ctx.command}`);
  if (ctx.kind === 'resync') parts.push(`${P.resyncSection}\n${ctx.resyncText}`);
  parts.push(outputSpec(state, ctx.kind));
  return parts.join('\n\n');
}
