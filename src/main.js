// 화면과 진행. 국가의 시대와 기업의 시대가 함께 쓴다.
// 엔진(engine.js)이 판정과 기록을, 프롬프트(prompt.js)가 Claude에게 보낼 글을, llm.js가 연결을 맡는다.
// 문구와 무대는 boot(MODE)로 받은 모드(modes/)가 정한다.

import {
  TIERS,
  PENDING_POLICY,
  createGame,
  validateSetup,
  computePressure,
  rollFriction,
  REVISIT_BONUS,
  rollEvents,
  pickStaleAgenda,
  advanceDate,
  formatSpan,
  spanInMonths,
  rollDecisionDays,
  parseTimeSkip,
  parseJsonLoose,
  normalizeResponse,
  validateResponse,
  applyResponse,
  worldSummary,
  formatDate,
  formatAmount,
  formatBig,
  formatSignedBig,
  serialize,
  deserialize,
  MAX_SKIP_MONTHS,
} from './engine.js';
import { buildPrompt } from './prompt.js';
import { detectBackend, errorMessage, LlmError } from './llm.js';

let MODE = null; // boot()에서 정해진다
let UI = null;

const app = {
  state: null,
  backend: null,
  backendChecked: false,
  busy: null, // 진행 중인 요청의 AbortController
  lastFailed: null, // 실패한 요청 (같은 판정으로 다시 시도)
  rollCache: null, // 같은 턴에 같은 명령을 다시 내려도 주사위를 다시 굴리지 않는다
  tab: 'people',
  selectedScenario: null,
};

// ── DOM 도구 ───────────────────────────────────────────────
const $ = (id) => document.getElementById(id);

function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  if (attrs) {
    for (const [k, v] of Object.entries(attrs)) {
      if (v === null || v === undefined || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k === 'text') el.textContent = v;
      else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
      else if (k === 'dataset') Object.assign(el.dataset, v);
      else el.setAttribute(k, v === true ? '' : v);
    }
  }
  for (const c of children.flat()) {
    if (c === null || c === undefined || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

// ── 저장 ───────────────────────────────────────────────────
function save() {
  try {
    if (app.state) localStorage.setItem(MODE.saveKey, serialize(app.state));
  } catch {
    // 저장소를 쓸 수 없는 환경: 이번 접속 동안만 이어진다
  }
}

function loadSaved() {
  try {
    const raw = localStorage.getItem(MODE.saveKey);
    return raw ? deserialize(raw, MODE.id) : null;
  } catch {
    return null;
  }
}

function clearSaved() {
  try {
    localStorage.removeItem(MODE.saveKey);
  } catch {
    // 무시
  }
}

function loadPrefs() {
  try {
    return JSON.parse(localStorage.getItem(MODE.prefsKey) || '{}') || {};
  } catch {
    return {};
  }
}

function savePrefs(p) {
  try {
    localStorage.setItem(MODE.prefsKey, JSON.stringify({ ...loadPrefs(), ...p }));
  } catch {
    // 무시
  }
}

// ── 시나리오 화면 ──────────────────────────────────────────
function renderScenarios() {
  const grid = $('scenarioGrid');
  grid.replaceChildren(
    ...MODE.scenarios.map((s) =>
      h(
        'button',
        {
          type: 'button',
          class: 'scenario',
          role: 'radio',
          'aria-checked': String(s.id === app.selectedScenario),
          onclick: () => selectScenario(s.id),
        },
        h('span', { class: 'sc-year', text: `${s.startYear}` }),
        h('span', { class: 'sc-country', text: `${s.country} · ${s.ruler}` }),
        h('span', { class: 'sc-title', text: s.title }),
        h('span', { class: 'sc-blurb', text: s.blurb })
      )
    ),
    h(
      'button',
      {
        type: 'button',
        class: 'scenario custom',
        role: 'radio',
        'aria-checked': String(app.selectedScenario === 'custom'),
        onclick: () => selectScenario('custom'),
      },
      h('span', { class: 'sc-year', text: '????' }),
      h('span', { class: 'sc-country', text: UI.customCountry }),
      h('span', { class: 'sc-title', text: UI.customTitle }),
      h('span', { class: 'sc-blurb', text: UI.customBlurb })
    )
  );
}

function selectScenario(id) {
  app.selectedScenario = id;
  const s = MODE.scenarios.find((x) => x.id === id);
  $('fCountry').value = s ? s.country : '';
  $('fRuler').value = s ? s.ruler : '';
  $('fYear').value = s ? s.startYear : '';
  $('fMonth').value = s ? s.startMonth : 1;
  $('fInstitution').value = s ? s.institution : '';
  $('fNotes').value = '';
  renderScenarios();
  if (!s) $('fCountry').focus();
}

function renderSegments(containerId, name, options, selected) {
  $(containerId).replaceChildren(
    ...Object.entries(options).map(([value, o]) =>
      h(
        'label',
        { class: 'seg-option' },
        h('input', { type: 'radio', name, value, checked: value === selected, id: `${name}-${value}` }),
        h('span', { class: 'seg-label', text: o.label }),
        h('span', { class: 'seg-desc', text: o.desc })
      )
    )
  );
}

function readSetup() {
  const form = $('setupForm');
  const data = Object.fromEntries(new FormData(form).entries());
  return { ...data, mode: MODE.id, scenarioId: app.selectedScenario };
}

function renderResume() {
  const saved = loadSaved();
  $('resume').hidden = !saved;
  if (saved) {
    $('resumeMeta').textContent = `${saved.setup.country} · ${saved.setup.ruler} · ${formatDate(saved.date)}${
      saved.eraLabel ? ` (${saved.eraLabel})` : ''
    } · ${saved.turnCount}번째 기록`;
  }
}

function renderBackendStatus() {
  const el = $('backendStatus');
  const btn = $('startBtn');
  const b = app.backend;
  if (!app.backendChecked) {
    el.textContent = UI.backendChecking;
    el.dataset.state = 'wait';
    btn.disabled = true;
    return;
  }
  if (!b) {
    el.textContent = UI.backendOff;
    el.dataset.state = 'off';
    btn.disabled = true;
    return;
  }
  if (b.ready === false) {
    el.textContent = UI.backendNoKey;
    el.dataset.state = 'off';
    btn.disabled = true;
    return;
  }
  el.textContent = `${UI.scribe}: ${b.label}`;
  el.dataset.state = 'on';
  btn.disabled = false;
}

function showSetup() {
  $('setup').hidden = false;
  $('game').hidden = true;
  $('realm').hidden = true;
  $('topActions').hidden = true;
  renderResume();
  renderBackendStatus();
}

function showGame() {
  $('setup').hidden = true;
  $('game').hidden = false;
  $('realm').hidden = false;
  $('topActions').hidden = false;
  renderAll();
}

// ── 상태창 ─────────────────────────────────────────────────
function deltaSpan(n, fmt = formatSignedBig) {
  const t = fmt(n);
  if (!t) return null;
  return h('span', { class: `delta ${n > 0 ? 'up' : 'down'}`, text: t });
}

function nationDelta(added, removed) {
  const parts = [];
  if (added?.length) parts.push(h('span', { class: 'delta up', text: `+${added.join(', ')}` }));
  if (removed?.length) parts.push(h('span', { class: 'delta down', text: `−${removed.join(', ')}` }));
  return parts;
}

function gauge(v) {
  return h('span', { class: 'gauge', 'aria-hidden': 'true' }, h('span', { style: `width:${Math.max(0, Math.min(100, v))}%` }));
}

function statusGrid(status, deltas, units, date, eraLabel, months, days) {
  const d = deltas || {};
  const STATUS_LABEL = MODE.statusLabels;
  const signedInt = (n) => (n ? `${n > 0 ? '▲' : '▼'} ${Math.abs(n)}` : '');
  const item = (key, label, value, ...extra) =>
    h('div', { class: `stat stat-${key}` }, h('dt', { text: label }), h('dd', null, h('span', { class: 'stat-value' }, value), ...extra));
  return h(
    'dl',
    { class: 'statwin' },
    item('population', STATUS_LABEL.population, formatAmount(status.population, '명'), deltaSpan(d.population)),
    item('allies', STATUS_LABEL.allies, status.allies.join(', ') || '없음', ...nationDelta(d.alliesAdded, d.alliesRemoved)),
    item('enemies', STATUS_LABEL.enemies, status.enemies.join(', ') || '없음', ...nationDelta(d.enemiesAdded, d.enemiesRemoved)),
    item('opinion', STATUS_LABEL.opinion, `${status.opinion} / 100`, deltaSpan(d.opinion, signedInt), gauge(status.opinion)),
    item('happiness', STATUS_LABEL.happiness, `${status.happiness} / 100`, deltaSpan(d.happiness, signedInt), gauge(status.happiness)),
    item('treasury', STATUS_LABEL.treasury, formatAmount(status.treasury, units?.treasury), deltaSpan(d.treasury)),
    item('gdp', STATUS_LABEL.gdp, formatAmount(status.gdp, units?.gdp), deltaSpan(d.gdp)),
    item(
      'year',
      STATUS_LABEL.date,
      `${formatDate(date)}${eraLabel ? ` · ${eraLabel}` : ''}`,
      months || days ? h('span', { class: 'delta neutral', text: `+${formatSpan(months, days)}` }) : null
    )
  );
}

// ── 기록 카드 ──────────────────────────────────────────────
const SEAL_CLASS = {
  시행: 'ok',
  '조건부 시행': 'cond',
  보류: 'hold',
  '시행 후 반발': 'warn',
  반려: 'bad',
};

function turnHeading(t) {
  if (t.kind === 'opening') return UI.openingHeading;
  if (t.kind === 'time') return `${formatSpan(t.months, t.days)}이 흐름`;
  if (t.kind === 'resync') return '기록 대조';
  return null;
}

function turnCard(t, state) {
  const heading = turnHeading(t);
  const card = h(
    'article',
    { class: 'turn', dataset: { kind: t.kind }, id: `turn-${t.n}` },
    h(
      'header',
      { class: 'turn-head' },
      h('span', { class: 'turn-no', text: UI.turnNo(t.n) }),
      h('span', { class: 'turn-date', text: `${formatDate(t.date)}${t.eraLabel ? ` · ${t.eraLabel}` : ''}` })
    ),
    t.kind === 'command'
      ? h('p', { class: 'turn-command' }, h('span', { class: 'cmd-label', text: UI.commandTag }), h('span', { class: 'cmd-text', text: t.command }))
      : h('p', { class: 'turn-command system' }, h('span', { class: 'cmd-label', text: heading })),
    h('p', { class: 'turn-opening', text: t.opening }),
    h(
      'div',
      { class: 'record' },
      ...t.record.map((r) =>
        r.speaker
          ? h(
              'div',
              { class: 'speech' },
              h('p', { class: 'who' }, h('b', { text: r.speaker }), r.title ? h('span', { text: r.title }) : null),
              h('p', { class: 'says', text: r.text })
            )
          : h('p', { class: 'narr', text: r.text })
      )
    )
  );
  if (t.execution && t.execution.status !== '해당없음') {
    card.append(
      h(
        'div',
        { class: 'verdict' },
        h('span', { class: `seal ${SEAL_CLASS[t.execution.status] || ''}`, text: MODE.execDisplay[t.execution.status] || t.execution.status }),
        t.execution.note ? h('p', { class: 'verdict-note', text: t.execution.note }) : null
      )
    );
  }
  if (t.news.length) {
    card.append(
      h('section', { class: 'news' }, h('h4', { text: '오늘의 뉴스:' }), h('ul', null, ...t.news.map((n) => h('li', { text: n }))))
    );
  }
  card.append(
    h(
      'section',
      { class: 'turn-status' },
      h('h4', { text: '상태창:' }),
      statusGrid(t.status, t.deltas, state.units, t.date, t.eraLabel, t.months, t.days)
    )
  );
  if (t.issues?.length) {
    card.append(
      h(
        'aside',
        { class: 'audit' },
        h('h4', { text: UI.auditTitle }),
        h('ul', null, ...t.issues.map((i) => h('li', { class: i.level, text: i.text })))
      )
    );
  }
  return card;
}

// ── 사이드 패널 ────────────────────────────────────────────
function renderSide() {
  const s = app.state;
  const st = s.status;
  const last = s.turns[s.turns.length - 1];
  $('sideStatus').replaceChildren(
    st ? statusGrid(st, last?.deltas, s.units, s.date, s.eraLabel, last?.months, last?.days) : h('p', { class: 'muted', text: UI.statusPending })
  );
  const p = computePressure(s);
  $('sidePeek').textContent = st ? UI.peek(st, p.chance, formatBig) : '';

  $('mood').replaceChildren(
    h(
      'div',
      { class: 'mood', dataset: { level: p.label } },
      h('span', { class: 'mood-label', text: p.label }),
      h('span', { class: 'mood-scale', 'aria-hidden': 'true' }, ...['우호적', '평온', '긴장', '험악'].map((l) => h('span', { class: l === p.label ? 'on' : '' })))
    ),
    h('p', { class: 'mood-chance' }, UI.acceptLine, h('b', { text: `약 ${p.chance}%` })),
    h(
      'p',
      { class: 'mood-why' },
      p.reasons.length ? p.reasons.join(' · ') : UI.moodNone,
      ` (협조 성향: ${(MODE.difficulty[s.setup.difficulty] || MODE.difficulty.standard).label})`
    ),
    h('p', { class: 'mood-tip', text: UI.moodTip(REVISIT_BONUS) })
  );

  for (const btn of document.querySelectorAll('.tabs [role=tab]')) {
    btn.setAttribute('aria-selected', String(btn.dataset.tab === app.tab));
  }
  $('tabBody').setAttribute('aria-labelledby', `tab-${app.tab}`);
  const body = $('tabBody');
  if (app.tab === 'people') body.replaceChildren(peopleList(s));
  else if (app.tab === 'policy') body.replaceChildren(policyList(s));
  else if (app.tab === 'faction') body.replaceChildren(factionList(s));
  else body.replaceChildren(annalsList(s));
}

function emptyNote(text) {
  return h('p', { class: 'muted', text });
}

function peopleList(s) {
  if (!s.characters.length) return emptyNote(UI.peopleEmpty);
  const active = s.characters.filter((c) => !c.status || c.status === '재직');
  const others = s.characters.filter((c) => c.status && c.status !== '재직');
  const row = (c) =>
    h(
      'li',
      { class: `person ${c.status && c.status !== '재직' ? 'out' : ''}` },
      h(
        'p',
        { class: 'person-head' },
        h('b', { text: c.name }),
        h('span', { text: [c.title, c.faction].filter(Boolean).join(' · ') }),
        c.status && c.status !== '재직' ? h('span', { class: 'pill', text: c.status }) : null
      ),
      c.disposition ? h('p', { class: 'person-trait', text: c.disposition }) : null,
      c.quotes?.length ? h('p', { class: 'person-quote', text: `“${c.quotes[c.quotes.length - 1].text}”` }) : null
    );
  return h('div', null, h('ul', { class: 'people' }, ...active.map(row)), others.length ? h('h5', { class: 'sub', text: UI.peopleRetired }) : null, others.length ? h('ul', { class: 'people' }, ...others.map(row)) : null);
}

function policyList(s) {
  if (!s.policies.length) return emptyNote(UI.policyEmpty);
  const pending = s.policies.filter((p) => PENDING_POLICY.has(p.status));
  const settled = s.policies.filter((p) => !PENDING_POLICY.has(p.status));
  const row = (p, isPending) =>
    h(
      'li',
      { class: 'policy' },
      h(
        'p',
        { class: 'policy-head' },
        h('b', { text: p.name }),
        h('span', { class: `pill ${isPending ? 'pending' : ''}`, text: p.status }),
        isPending ? h('span', { class: 'wait', text: `${s.turnCount - (p.updatedTurn ?? 0)}턴째` }) : null
      ),
      p.summary ? h('p', { class: 'policy-sum', text: p.summary }) : null,
      p.backlash ? h('p', { class: 'policy-back', text: `반발: ${p.backlash}` }) : null,
      h(
        'p',
        { class: 'policy-meta' },
        p.since ? `${formatDate(p.since)} 제기` : '',
        isPending
          ? h('button', {
              type: 'button',
              class: 'linkish',
              text: `${UI.revisitButton} (+${REVISIT_BONUS}%p)`,
              onclick: () => prefill(UI.revisitCommand(p.name)),
            })
          : null
      )
    );
  return h(
    'div',
    null,
    h('h5', { class: 'sub', text: `미결 안건 ${pending.length}` }),
    pending.length ? h('ul', { class: 'policies' }, ...pending.map((p) => row(p, true))) : emptyNote('밀린 안건이 없습니다.'),
    settled.length ? h('h5', { class: 'sub', text: UI.policySettled }) : null,
    settled.length ? h('ul', { class: 'policies' }, ...settled.map((p) => row(p, false))) : null
  );
}

function factionList(s) {
  if (!s.factions.length) return emptyNote(UI.factionEmpty);
  return h(
    'ul',
    { class: 'factions' },
    ...s.factions.map((f) =>
      h(
        'li',
        null,
        h('p', { class: 'faction-head' }, h('b', { text: f.name }), h('span', { class: 'num', text: String(f.support) })),
        gauge(f.support),
        f.note ? h('p', { class: 'faction-note', text: f.note }) : null
      )
    )
  );
}

function annalsList(s) {
  if (!s.chronicle.length) return emptyNote(UI.annalsEmpty);
  return h(
    'ol',
    { class: 'annals' },
    ...[...s.chronicle].reverse().map((c) =>
      h('li', null, h('a', { href: `#turn-${c.turn}`, class: 'annal-date', text: formatDate(c.date) }), h('span', { text: c.text }))
    )
  );
}

// ── 화면 전체 ──────────────────────────────────────────────
function renderHeader() {
  const s = app.state;
  $('realmName').textContent = `${s.setup.country} · ${s.setup.ruler}`;
  $('realmDate').textContent = `${formatDate(s.date)}${s.eraLabel ? ` · ${s.eraLabel}` : ''}`;
}

function renderSuggestions() {
  const s = app.state;
  const last = s.turns[s.turns.length - 1];
  const items = last?.suggestions || [];
  $('suggest').replaceChildren(
    ...items.map((t) => h('button', { type: 'button', class: 'btn chip suggestion', text: t, onclick: () => prefill(t) }))
  );
}

function renderLog() {
  const log = $('log');
  log.replaceChildren(...app.state.turns.map((t) => turnCard(t, app.state)));
  if (!app.state.status && !app.busy && !app.lastFailed) {
    log.append(
      h(
        'div',
        { class: 'notice' },
        h('p', { text: UI.openingNotice }),
        h('button', { type: 'button', class: 'btn primary', text: UI.openingButton, onclick: () => runTurn({ kind: 'opening' }) })
      )
    );
  }
}

function renderAll() {
  renderHeader();
  renderLog();
  renderSide();
  renderSuggestions();
  updateDock();
}

function updateDock() {
  const ready = Boolean(app.state?.status) && !app.busy && app.backend && app.backend.ready !== false;
  for (const el of document.querySelectorAll('#dock button, #dock textarea, #dock input')) el.disabled = !ready;
}

function prefill(text) {
  const cmd = $('cmd');
  cmd.value = text;
  cmd.focus();
  closeSideOnMobile();
}

function scrollToEl(el) {
  el?.scrollIntoView({ behavior: reducedMotion() ? 'auto' : 'smooth', block: 'start' });
}

// ── 진행 중 카드 ───────────────────────────────────────────
function pendingCard(label) {
  const progress = h('p', { class: 'pending-progress', text: UI.pendingStart });
  const preview = h('p', { class: 'pending-preview' });
  const stop = h('button', { type: 'button', class: 'btn ghost', text: UI.stopButton, onclick: () => app.busy?.abort() });
  const card = h(
    'div',
    { class: 'pending', role: 'status' },
    h('p', { class: 'pending-label', text: label }),
    progress,
    preview,
    stop
  );
  return {
    card,
    update(text, note) {
      const speakers = [...text.matchAll(/"speaker"\s*:\s*"([^"]+)"/g)];
      const who = speakers.length ? speakers[speakers.length - 1][1] : '';
      progress.textContent = `${note || UI.pendingWriting} · ${text.length.toLocaleString('ko-KR')}자${who ? ` · ${who} 발언` : ''}`;
      const op = text.match(/"opening"\s*:\s*"((?:[^"\\]|\\.)*)/);
      if (op) preview.textContent = op[1].replace(/\\n/g, ' ');
    },
  };
}

function errorCard(message, retry) {
  return h(
    'div',
    { class: 'notice error', role: 'alert' },
    h('p', { text: message }),
    retry ? h('button', { type: 'button', class: 'btn primary', text: UI.retryButton, onclick: retry }) : null
  );
}

function conflictCard(conflict, onChoose) {
  return h(
    'div',
    { class: 'conflict', role: 'alert' },
    h('p', { class: 'conflict-title', text: '⚠️ 이전 설정과의 충돌이 감지되었습니다. 다음 중 어떤 방식으로 이어갈까요?' }),
    h('p', { class: 'conflict-summary', text: conflict.summary }),
    conflict.question ? h('p', { class: 'conflict-q', text: conflict.question }) : null,
    h(
      'div',
      { class: 'conflict-options' },
      h('button', { type: 'button', class: 'btn', onclick: () => onChoose('keep') }, h('b', { text: '(1)' }), ' 기존 설정을 기준으로 진행'),
      h('button', { type: 'button', class: 'btn', onclick: () => onChoose('override') }, h('b', { text: '(2)' }), ' 현재 지시를 우선하여 세계관 일부 덮어쓰기'),
      h('button', { type: 'button', class: 'btn', onclick: () => onChoose('resync') }, h('b', { text: '(3)' }), ' 세계관 요약을 정리해 재동기화'),
      h('button', { type: 'button', class: 'btn ghost', onclick: () => onChoose('cancel'), text: UI.conflictCancel })
    )
  );
}

// ── 턴 진행 ────────────────────────────────────────────────
// req: { kind: 'opening'|'command'|'time'|'resync', command?, months?, days?, resolution?, resyncText? }
function buildContext(req) {
  const s = app.state;
  if (req.kind === 'command') {
    const cached = app.rollCache;
    if (cached && cached.turn === s.turnCount && cached.command === req.command) {
      return { ...cached.ctx, resolution: req.resolution };
    }
    const roll = rollFriction(s, Math.random, req.command);
    const days = rollDecisionDays(roll.tier);
    const ctx = {
      kind: 'command',
      command: req.command,
      roll,
      days,
      target: advanceDate(s.date, 0, days),
      events: rollEvents(s, 'command', 0),
      agenda: pickStaleAgenda(s),
    };
    app.rollCache = { turn: s.turnCount, command: req.command, ctx };
    return { ...ctx, resolution: req.resolution };
  }
  if (req.kind === 'time') {
    const cached = app.rollCache;
    const months = req.months || 0;
    const days = req.days || 0;
    const key = `${months}:${days}`;
    if (cached && cached.turn === s.turnCount && cached.span === key) return cached.ctx;
    const ctx = {
      kind: 'time',
      months,
      days,
      target: advanceDate(s.date, months, days),
      roll: rollFriction(s),
      events: rollEvents(s, 'time', spanInMonths(months, days)),
      agenda: pickStaleAgenda(s),
    };
    app.rollCache = { turn: s.turnCount, span: key, ctx };
    return ctx;
  }
  if (req.kind === 'resync') return { kind: 'resync', resyncText: req.resyncText };
  return { kind: 'opening' };
}

async function generateOnce(ctx, ui, note) {
  const prompt = buildPrompt(app.state, ctx);
  const text = await app.backend.generate(prompt, {
    signal: app.busy.signal,
    tier: app.state.setup.tier,
    meta: { ...ctx, state: app.state },
    onText: (t) => ui.update(t, note),
  });
  return normalizeResponse(parseJsonLoose(text));
}

async function runTurn(req) {
  if (app.busy || !app.backend) return;
  const ctx = buildContext(req);
  const labels = {
    opening: UI.openingLabel,
    command: UI.commandLabel(req.command),
    time: `${formatSpan(ctx.months, ctx.days)}의 시간을 흘려보냅니다`,
    resync: UI.resyncLabel,
  };
  app.busy = new AbortController();
  app.lastFailed = null;
  updateDock();
  for (const n of document.querySelectorAll('#log > .notice, #log > .conflict')) n.remove();
  const ui = pendingCard(labels[req.kind]);
  $('log').append(ui.card);
  scrollToEl(ui.card);

  try {
    let resp = await generateOnce(ctx, ui);
    if (resp.conflict && (ctx.kind !== 'command' || ctx.resolution)) {
      resp = await generateOnce({ ...ctx, correction: ['이번 턴에는 conflict를 쓰지 말고 기록을 진행한다.'] }, ui, UI.pendingRewrite);
    }
    if (resp.conflict) {
      ui.card.remove();
      app.busy = null;
      const card = conflictCard(resp.conflict, (choice) => resolveConflict(choice, req, card));
      $('log').append(card);
      scrollToEl(card);
      updateDock();
      return;
    }
    let issues = validateResponse(app.state, resp);
    let high = issues.filter((i) => i.level === 'high');
    if (high.length) {
      const retry = await generateOnce({ ...ctx, correction: high.map((i) => i.text) }, ui, UI.pendingRewrite);
      if (!retry.conflict) {
        const retryIssues = validateResponse(app.state, retry);
        resp = retry;
        issues = retryIssues;
        high = retryIssues.filter((i) => i.level === 'high');
      }
    }
    if (!app.state.status && high.some((i) => /첫 상태창/.test(i.text))) {
      throw new LlmError('invalid_json', '첫 상태창이 비어 있습니다.');
    }
    const auditIssues = issues.map((i) => ({
      level: i.level,
      text: i.level === 'high' ? `확인 필요: ${i.text}` : i.text,
    }));
    app.state = applyResponse(app.state, resp, { ...ctx, issues: auditIssues });
    app.rollCache = null;
    save();
    app.busy = null;
    ui.card.remove();
    renderAll();
    if (req.kind === 'command') $('cmd').value = '';
    const last = app.state.turns[app.state.turns.length - 1];
    scrollToEl($(`turn-${last.n}`));
  } catch (e) {
    const err = e?.code ? e : new LlmError('upstream_error', e?.message);
    app.busy = null;
    ui.card.remove();
    if (err.code === 'cancelled') {
      if (req.kind === 'command') $('cmd').value = req.command;
      renderLog();
      updateDock();
      return;
    }
    app.lastFailed = req;
    const hideRetry = ['not_granted', 'sampling_disabled', 'auth', 'prompt_too_large'].includes(err.code);
    const card = errorCard(errorMessage(err, UI.scribe), hideRetry ? null : () => runTurn(req));
    renderLog();
    $('log').append(card);
    scrollToEl(card);
    updateDock();
  }
}

function resolveConflict(choice, req, card) {
  card.remove();
  if (choice === 'cancel') {
    $('cmd').value = req.command;
    updateDock();
    return;
  }
  if (choice === 'resync') {
    openResync(req.command);
    return;
  }
  runTurn({ ...req, resolution: choice });
}

function submitCommand(text) {
  const command = text.trim();
  if (!command || app.busy) return;
  const skip = parseTimeSkip(command);
  if (skip) {
    $('cmd').value = '';
    runTurn({ kind: 'time', ...skip });
    return;
  }
  runTurn({ kind: 'command', command });
}

// ── 모달 ───────────────────────────────────────────────────
let lastFocus = null;

function openModal(title, ...content) {
  lastFocus = document.activeElement;
  $('modalTitle').textContent = title;
  $('modalBody').replaceChildren(...content);
  $('modal').hidden = false;
  $('modalClose').focus();
}

function closeModal() {
  $('modal').hidden = true;
  lastFocus?.focus?.();
}

async function copyText(text, button) {
  try {
    await navigator.clipboard.writeText(text);
    button.textContent = '복사했습니다';
  } catch {
    button.textContent = '아래 글을 직접 선택해 복사하십시오';
  }
}

async function offerFile(filename, text, button) {
  try {
    const downloads = window.claude?.use ? await window.claude.use('downloads') : null;
    if (downloads) {
      await downloads.save({ filename, data: text });
      button.textContent = '저장했습니다';
      return;
    }
  } catch {
    button.textContent = '저장하지 못했습니다. 복사를 이용하십시오';
    return;
  }
  if (window.claude) {
    button.textContent = '이 화면에서는 파일 저장을 쓸 수 없습니다. 복사를 이용하십시오';
    return;
  }
  const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
  const a = h('a', { href: url, download: filename });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  button.textContent = '저장했습니다';
}

function openRecords() {
  const s = app.state;
  const json = serialize(s);
  const summary = worldSummary(s);
  const out = h('textarea', { class: 'mono', rows: 4, readonly: true, id: 'exportText' });
  out.value = json;
  const copyBtn = h('button', { type: 'button', class: 'btn', text: '저장 기록 복사' });
  copyBtn.addEventListener('click', () => copyText(json, copyBtn));
  const fileBtn = h('button', { type: 'button', class: 'btn', text: '파일로 저장' });
  fileBtn.addEventListener('click', () => offerFile(`${MODE.filePrefix}-${s.setup.country}-${s.date.year}.json`, json, fileBtn));

  const input = h('textarea', { class: 'mono', rows: 4, id: 'importText', placeholder: '저장 기록(JSON)을 붙여 넣으십시오' });
  const importMsg = h('p', { class: 'form-error', hidden: true, role: 'alert' });
  const importBtn = h('button', {
    type: 'button',
    class: 'btn',
    text: '불러오기',
    onclick: () => {
      try {
        app.state = deserialize(input.value.trim(), MODE.id);
        save();
        closeModal();
        showGame();
      } catch (e) {
        importMsg.hidden = false;
        importMsg.textContent = e?.message || '불러오지 못했습니다.';
      }
    },
  });

  const tierBox = h('div', { class: 'seg-options' });
  for (const [value, o] of Object.entries(TIERS)) {
    tierBox.append(
      h(
        'label',
        { class: 'seg-option' },
        h('input', {
          type: 'radio',
          name: 'tierLive',
          value,
          checked: s.setup.tier === value,
          id: `tierLive-${value}`,
          onchange: () => {
            app.state.setup.tier = value;
            savePrefs({ tier: value });
            save();
          },
        }),
        h('span', { class: 'seg-label', text: o.label }),
        h('span', { class: 'seg-desc', text: o.desc })
      )
    );
  }

  const diffBox = h('div', { class: 'seg-options' });
  for (const [value, o] of Object.entries(MODE.difficulty)) {
    diffBox.append(
      h(
        'label',
        { class: 'seg-option' },
        h('input', {
          type: 'radio',
          name: 'difficultyLive',
          value,
          checked: s.setup.difficulty === value,
          id: `difficultyLive-${value}`,
          onchange: () => {
            app.state.setup.difficulty = value;
            app.rollCache = null;
            savePrefs({ difficulty: value });
            save();
            renderSide();
          },
        }),
        h('span', { class: 'seg-label', text: o.label }),
        h('span', { class: 'seg-desc', text: o.desc })
      )
    );
  }

  const confirmRow = h('div', { class: 'confirm-row', hidden: true },
    h('p', { text: UI.newGameConfirm }),
    h('button', { type: 'button', class: 'btn danger', text: '지우고 새로 시작', onclick: () => { clearSaved(); app.state = null; closeModal(); showSetup(); } }),
    h('button', { type: 'button', class: 'btn ghost', text: '그만두기', onclick: () => { confirmRow.hidden = true; } })
  );

  openModal(
    '기록 관리',
    h('section', { class: 'modal-sec' }, h('h3', { text: UI.difficultyHeading }), h('fieldset', { class: 'seg' }, h('legend', { class: 'sr-only', text: UI.difficultyLegend }), diffBox)),
    h('section', { class: 'modal-sec' }, h('h3', { text: UI.tierHeading }), h('fieldset', { class: 'seg' }, h('legend', { class: 'sr-only', text: UI.tierLegend }), tierBox)),
    h('section', { class: 'modal-sec' },
      h('h3', { text: UI.summaryHeading }),
      h('pre', { class: 'summary', text: summary }),
      h('button', { type: 'button', class: 'btn', text: '기록 대조(재동기화)…', onclick: () => openResync('') })
    ),
    h('section', { class: 'modal-sec' }, h('h3', { text: '내보내기' }), out, h('div', { class: 'btn-row' }, copyBtn, fileBtn)),
    h('section', { class: 'modal-sec' }, h('h3', { text: '불러오기' }), input, importMsg, h('div', { class: 'btn-row' }, importBtn)),
    h('section', { class: 'modal-sec' },
      h('h3', { text: UI.newGameHeading }),
      h('button', { type: 'button', class: 'btn danger', text: UI.newGameButton, onclick: () => { confirmRow.hidden = false; } }),
      confirmRow
    )
  );
}

function openResync(pendingCommand) {
  const area = h('textarea', { rows: 12, id: 'resyncText' });
  area.value = worldSummary(app.state);
  openModal(
    '기록 대조',
    h('p', { class: 'form-hint', text: UI.resyncHint }),
    area,
    h('div', { class: 'btn-row' },
      h('button', {
        type: 'button',
        class: 'btn primary',
        text: '이 요약으로 대조',
        onclick: () => {
          const text = area.value.trim();
          if (!text) return;
          closeModal();
          if (pendingCommand) $('cmd').value = pendingCommand;
          runTurn({ kind: 'resync', resyncText: text });
        },
      })
    )
  );
}

// ── 모바일 사이드 패널 ─────────────────────────────────────
function closeSideOnMobile() {
  const side = $('side');
  if (side.classList.contains('open')) {
    side.classList.remove('open');
    $('sideToggle').setAttribute('aria-expanded', 'false');
  }
}

// ── 시작 ───────────────────────────────────────────────────
function wire() {
  const prefs = loadPrefs();
  renderSegments('difficultyOptions', 'difficulty', MODE.difficulty, prefs.difficulty || 'standard');
  renderSegments('tierOptions', 'tier', TIERS, prefs.tier || 'default');
  selectScenario(app.selectedScenario);

  $('setupForm').addEventListener('submit', (e) => {
    e.preventDefault();
    const setup = readSetup();
    const errors = validateSetup(setup);
    const box = $('setupError');
    if (errors.length) {
      box.hidden = false;
      box.textContent = errors.join(' ');
      return;
    }
    box.hidden = true;
    savePrefs({ difficulty: setup.difficulty, tier: setup.tier });
    app.state = createGame(setup);
    app.rollCache = null;
    save();
    showGame();
    runTurn({ kind: 'opening' });
  });

  $('resumeBtn').addEventListener('click', () => {
    const saved = loadSaved();
    if (!saved) return renderResume();
    app.state = saved;
    showGame();
    const last = app.state.turns[app.state.turns.length - 1];
    if (last) scrollToEl($(`turn-${last.n}`));
  });

  $('dock').addEventListener('submit', (e) => {
    e.preventDefault();
    submitCommand($('cmd').value);
  });
  $('cmd').addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing && e.keyCode !== 229) {
      e.preventDefault();
      submitCommand($('cmd').value);
    }
  });
  for (const b of document.querySelectorAll('#dock [data-months], #dock [data-days]')) {
    b.addEventListener('click', () =>
      runTurn({ kind: 'time', months: Number(b.dataset.months || 0), days: Number(b.dataset.days || 0) })
    );
  }
  $('skipGo').addEventListener('click', () => {
    const years = Math.round(Number($('skipYears').value));
    if (!years || years < 1) return;
    runTurn({ kind: 'time', months: Math.min(years * 12, MAX_SKIP_MONTHS) });
  });

  for (const btn of document.querySelectorAll('.tabs [role=tab]')) {
    btn.addEventListener('click', () => {
      app.tab = btn.dataset.tab;
      renderSide();
    });
  }
  $('sideToggle').addEventListener('click', () => {
    const open = $('side').classList.toggle('open');
    $('sideToggle').setAttribute('aria-expanded', String(open));
  });
  $('openRecords').addEventListener('click', openRecords);
  $('modalClose').addEventListener('click', closeModal);
  $('modal').addEventListener('click', (e) => {
    if (e.target === $('modal')) closeModal();
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !$('modal').hidden) closeModal();
  });
}

async function start(hotData) {
  wire();
  const restored = hotData?.state ? (() => { try { return deserialize(hotData.state, MODE.id); } catch { return null; } })() : null;
  if (restored) {
    app.state = restored;
    showGame();
  } else {
    showSetup();
  }
  app.backend = await detectBackend();
  app.backendChecked = true;
  if (app.state) updateDock();
  renderBackendStatus();
}

export function boot(mode) {
  MODE = mode;
  UI = mode.ui;
  app.selectedScenario = mode.defaultScenario;
  // 아티팩트가 다시 게시되어도 진행 중인 게임을 잃지 않도록 한다
  const hot = window.claude?.hot;
  hot?.snapshot?.(() => ({ state: app.state ? serialize(app.state) : null }));
  if (hot?.ready) hot.ready(start);
  else start(hot?.data ?? {});
}

