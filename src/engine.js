// 국가의 시대·기업의 시대 — 게임 엔진.
// DOM도 Claude 호출도 모르는 순수 로직만 둔다: 판정 주사위, 돌발 사건, 시간 계산,
// 응답 정규화·검증, 세계 기록 병합. 서술은 Claude가, 규칙과 기억은 엔진이 맡는다.
// 무대(문구, 상태창 항목, 사건 종류, 변동 폭)는 modes/의 모드 파일이 정한다.

import { MODES, getMode } from './modes/index.js';

export const SAVE_VERSION = 1;

export function modeOf(state) {
  return getMode(state?.setup?.mode);
}

// ── 실행 판정 ──────────────────────────────────────────────
// 플레이어 명령이 그대로 관철되지 않도록 엔진이 먼저 결과의 등급을 정한다.
// Claude는 이 등급을 어떤 사정으로 그렇게 되었는지 서사로 풀어낼 뿐 바꾸지 못한다.
// 등급별 지시문은 모드의 outcomes에 있다.
export const TIER_KEYS = ['smooth', 'conditional', 'delayed', 'backlash', 'blocked'];

// Claude가 돌려주는 실행 결과 표기. 화면의 결재 도장과 연속 판정 기록에 쓰인다.
export const EXEC_STATUS = ['시행', '조건부 시행', '보류', '시행 후 반발', '반려', '해당없음'];
const GOOD_EXEC = new Set(['시행', '조건부 시행']);
const BAD_EXEC = new Set(['보류', '시행 후 반발', '반려']);

// 조정(임원진)의 협조 정도에 따른 판정 보정. 이름과 설명은 모드의 difficulty에 있다.
export const DIFFICULTY_MOD = { mild: -15, standard: 0, harsh: 15 };

export const TIERS = {
  quick: { label: '신속', desc: '빠르지만 기록이 짧습니다' },
  default: { label: '표준', desc: '속도와 깊이의 균형' },
  complex: { label: '신중', desc: '가장 깊이 있지만 느립니다' },
};

export const PENDING_POLICY = new Set(['논의중', '보류']);
const DEAD = /사망|처형|사사|졸|卒|서거|붕어|전사|병사/;

// ── 작은 도구들 ────────────────────────────────────────────
export function clamp(v, lo, hi) {
  return Math.min(hi, Math.max(lo, v));
}

function str(v) {
  if (v === null || v === undefined) return '';
  return String(v).trim();
}

function arr(v) {
  if (Array.isArray(v)) return v;
  if (typeof v === 'string' && v.trim()) return v.split(/[,，、·]/).map((s) => s.trim()).filter(Boolean);
  return [];
}

function nameKey(name) {
  return str(name).replace(/\s+/g, '');
}

// "약 552만 명", "1억 2천만", "38,000" 같은 표기를 숫자로 읽는다. 못 읽으면 NaN.
export function num(v) {
  if (typeof v === 'number') return Number.isFinite(v) ? v : NaN;
  let s = str(v).replace(/[,\s]/g, '').replace(/^[−–]/, '-');
  if (!s) return NaN;
  const negative = s.startsWith('-');
  if (negative) s = s.slice(1);
  const units = { 억: 1e8, 천만: 1e7, 백만: 1e6, 만: 1e4, 천: 1e3 };
  const re = /(\d+(?:\.\d+)?)(억|천만|백만|만|천)?/g;
  let total = 0;
  let found = false;
  let m;
  while ((m = re.exec(s))) {
    found = true;
    total += parseFloat(m[1]) * (m[2] ? units[m[2]] : 1);
    if (!m[2]) break; // 단위 없는 숫자 뒤의 숫자는 다른 값이므로 멈춘다
  }
  if (!found) return NaN;
  return negative ? -total : total;
}

// 시드 고정 난수 (테스트와 재현용). 기본은 Math.random.
export function seededRandom(seed) {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// ── 날짜 ───────────────────────────────────────────────────
// 게임 달력은 한 달을 30일로 센다. 왕명 한 번은 조정 회의 며칠의 일이고,
// 몇 달·몇 년은 시간 경과 명령으로만 흐른다.
export const DAYS_PER_MONTH = 30;

export function advanceDate(date, months, days = 0) {
  const start = (date.year * 12 + (date.month - 1)) * DAYS_PER_MONTH + ((date.day || 1) - 1);
  const total = start + Math.max(0, Math.round(months)) * DAYS_PER_MONTH + Math.max(0, Math.round(days));
  const monthIndex = Math.floor(total / DAYS_PER_MONTH);
  return {
    year: Math.floor(monthIndex / 12),
    month: (((monthIndex % 12) + 12) % 12) + 1,
    day: (total % DAYS_PER_MONTH) + 1,
  };
}

export function formatDate(date) {
  if (!date) return '';
  const y = date.year < 0 ? `기원전 ${-date.year}년` : `${date.year}년`;
  return `${y} ${date.month}월${date.day ? ` ${date.day}일` : ''}`;
}

// 경과 기간 표기: "4일", "보름"이 아니라 "15일", "3개월", "1년 3개월"
export function formatSpan(months = 0, days = 0) {
  const parts = [];
  const y = Math.floor(months / 12);
  const m = months % 12;
  if (y) parts.push(`${y}년`);
  if (m) parts.push(`${m}개월`);
  if (days) parts.push(`${days}일`);
  return parts.join(' ') || '같은 날';
}

// 상태창 보정과 사건 확률에 쓰는 개월 수 (며칠은 소수로)
export function spanInMonths(months = 0, days = 0) {
  return months + days / DAYS_PER_MONTH;
}

// 판정에 따라 논의가 며칠 걸렸는지. 순조로우면 금방, 지연되면 열흘 남짓 끈다.
export const DECISION_DAYS = {
  smooth: [1, 3],
  conditional: [2, 6],
  delayed: [5, 12],
  backlash: [2, 7],
  blocked: [1, 4],
};

export function rollDecisionDays(tier, rng = Math.random) {
  const [lo, hi] = DECISION_DAYS[tier] || DECISION_DAYS.conditional;
  return lo + Math.floor(rng() * (hi - lo + 1));
}

const KO_NUM = { 한: 1, 두: 2, 세: 3, 석: 3, 네: 4, 넉: 4, 다섯: 5, 여섯: 6, 일곱: 7, 여덟: 8, 아홉: 9, 열: 10 };
export const MAX_SKIP_MONTHS = 240;

// 시간 경과를 뜻하는 꼬리말: "후", "뒤", "이 흐른다", "가 지났다", "동안"
const ELAPSE = '(?:후|뒤|이\\s*(?:흐른다|흘렀다|흐르다|흘러|지난다|지났다|지나다|지나서|경과|경과한다|경과했다)|가\\s*(?:흐른다|흘렀다|지난다|지났다|경과|경과한다)|동안)';
const SKIP_RE = new RegExp(
  `^(\\d+|${Object.keys(KO_NUM).join('|')})\\s*(년|해|분기|개월|달|주|일)\\s*${ELAPSE}$`
);
const DAY_WORDS = { 일주일: 7, 열흘: 10, 보름: 15, 한주: 7 };

// "1년 후", "다음 달", "보름 후", "열흘 뒤", "3년이 흐른다", "5년 후로 넘어가자" 같은
// 시간 경과 명령을 {months, days}로 읽는다. 명령 전체가 시간 경과일 때만 알아본다.
// "1년 후에 세금을 올려라"는 왕명이므로 null.
export function parseTimeSkip(text) {
  const t = str(text)
    .replace(/[.!?…。]+$/, '')
    .replace(/\s*(로|까지|에)?\s*(넘어가(자|라|다)?|진행(하라|한다|해)?|흘려보내(라|자|다)?|건너뛰(어라|자|다)?)?$/, '')
    .trim();
  if (!t) return null;
  if (/^(다음\s*달|한\s*달\s*(후|뒤))$/.test(t)) return { months: 1, days: 0 };
  if (/^(내년|이듬해|다음\s*해)$/.test(t)) return { months: 12, days: 0 };
  if (/^반\s*년\s*(후|뒤)$/.test(t)) return { months: 6, days: 0 };
  if (/^다음\s*분기$/.test(t)) return { months: 3, days: 0 };
  if (/^다음\s*주$/.test(t)) return { months: 0, days: 7 };
  const word = t.match(new RegExp(`^(${Object.keys(DAY_WORDS).join('|')}|한\\s*주)\\s*${ELAPSE}$`));
  if (word) return { months: 0, days: DAY_WORDS[word[1].replace(/\s+/g, '')] };
  const m = t.match(SKIP_RE);
  if (!m) return null;
  const n = /^\d+$/.test(m[1]) ? parseInt(m[1], 10) : KO_NUM[m[1]];
  if (!n || n <= 0) return null;
  if (m[2] === '년' || m[2] === '해') return { months: Math.min(n * 12, MAX_SKIP_MONTHS), days: 0 };
  if (m[2] === '분기') return { months: Math.min(n * 3, MAX_SKIP_MONTHS), days: 0 };
  if (m[2] === '개월' || m[2] === '달') return { months: Math.min(n, MAX_SKIP_MONTHS), days: 0 };
  const days = m[2] === '주' ? n * 7 : n;
  if (days >= DAYS_PER_MONTH) {
    return { months: Math.min(Math.floor(days / DAYS_PER_MONTH), MAX_SKIP_MONTHS), days: days % DAYS_PER_MONTH };
  }
  return { months: 0, days };
}

// ── 새 게임 ────────────────────────────────────────────────
export function createGame(setup, now = Date.now()) {
  const year = Math.trunc(num(setup.startYear));
  const month = clamp(Math.trunc(num(setup.startMonth)) || 1, 1, 12);
  return {
    version: SAVE_VERSION,
    id: `g${now.toString(36)}`,
    createdAt: now,
    setup: {
      mode: MODES[setup.mode] ? setup.mode : 'kingdom',
      scenarioId: str(setup.scenarioId) || 'custom',
      country: str(setup.country),
      ruler: str(setup.ruler),
      startYear: year,
      startMonth: month,
      institution: str(setup.institution),
      notes: str(setup.notes),
      difficulty: setup.difficulty in DIFFICULTY_MOD ? setup.difficulty : 'standard',
      tier: TIERS[setup.tier] ? setup.tier : 'default',
    },
    units: null,
    date: { year, month, day: 1 },
    eraLabel: '',
    status: null,
    characters: [],
    policies: [],
    factions: [],
    regions: [],
    chronicle: [],
    ledger: [],
    best: {},
    over: null,
    turns: [],
    streak: { good: 0, bad: 0 },
    turnCount: 0,
  };
}

export function validateSetup(setup) {
  const msg = getMode(setup.mode).ui.setupErrors;
  const errors = [];
  if (!str(setup.country)) errors.push(msg.country);
  if (!str(setup.ruler)) errors.push(msg.ruler);
  const y = num(setup.startYear);
  if (!Number.isFinite(y) || Math.abs(y) > 3000) errors.push('시작 연도를 숫자로 입력하십시오.');
  if (!str(setup.institution)) errors.push(msg.institution);
  return errors;
}

// ── 조정 기류와 판정 ───────────────────────────────────────
function factionAverage(state) {
  const list = state.factions || [];
  if (!list.length) return null;
  return list.reduce((sum, f) => sum + (Number(f.support) || 0), 0) / list.length;
}

// 주사위를 빼고 남는 보정치. 화면의 '조정 기류'와 판정이 같은 근거를 쓴다.
export function computePressure(state) {
  const R = modeOf(state).pressureReasons;
  const reasons = [];
  let mod = 0;
  const s = state.status;
  // 모드의 규칙: 상태창을 보고 [보정치, 사유]를 돌려준다 (민심, 국고, 직원 사기, 현금, 적자 등)
  if (s) {
    for (const rule of modeOf(state).status.pressure) {
      const hit = rule(s);
      if (hit) {
        mod += hit[0];
        reasons.push(hit[1]);
      }
    }
  }
  const avg = factionAverage(state);
  if (avg !== null) {
    if (avg < 35) {
      mod += 8;
      reasons.push(R.distrust);
    } else if (avg >= 65) {
      mod -= 5;
      reasons.push(R.unity);
    }
  }
  // 연이어 성사되면 견제가 붙지만 두 번째부터, 그리고 완만하게
  const good = Math.min(state.streak?.good || 0, 4);
  if (good >= 2) {
    mod += (good - 1) * 3;
    reasons.push(R.streakGood);
  }
  // 막히고 나면 조정도 타협을 찾는다
  const bad = Math.min(state.streak?.bad || 0, 3);
  if (bad >= 1) {
    mod -= bad * 8;
    reasons.push(R.streakBad);
  }
  mod += DIFFICULTY_MOD[state.setup.difficulty] || 0;

  let label = '평온';
  if (mod <= -8) label = '우호적';
  else if (mod >= 15) label = '험악';
  else if (mod >= 5) label = '긴장';
  return { mod, reasons, label, chance: acceptChance(mod) };
}

// 판정 점수 경계. 시행(순조·조건부)까지가 ACCEPT_MAX.
const TIER_MAX = { smooth: 30, conditional: 65, delayed: 80, backlash: 93 };
export const ACCEPT_MAX = TIER_MAX.conditional;

export function tierForScore(score) {
  if (score <= TIER_MAX.smooth) return 'smooth';
  if (score <= TIER_MAX.conditional) return 'conditional';
  if (score <= TIER_MAX.delayed) return 'delayed';
  if (score <= TIER_MAX.backlash) return 'backlash';
  return 'blocked';
}

// 보정치가 mod일 때 왕명이 시행(순조·조건부)될 확률(%)
export function acceptChance(mod) {
  return clamp(ACCEPT_MAX - mod, 1, 99);
}

// 미결 안건을 다시 꺼내면 논의가 무르익어 받아들여지기 쉽다
export const REVISIT_BONUS = 10;

export function revisitedAgenda(state, command) {
  const k = nameKey(command);
  if (!k) return null;
  return (
    (state.policies || []).find((p) => PENDING_POLICY.has(p.status) && nameKey(p.name).length >= 2 && k.includes(nameKey(p.name))) ||
    null
  );
}

export function rollFriction(state, rng = Math.random, command = '') {
  const base = Math.floor(rng() * 100) + 1;
  let { mod, reasons } = computePressure(state);
  const revisit = revisitedAgenda(state, command);
  if (revisit) {
    mod -= REVISIT_BONUS;
    reasons = [...reasons, modeOf(state).pressureReasons.revisit(revisit.name)];
  }
  const score = clamp(base + mod, 1, 120);
  return { base, mod, score, tier: tierForScore(score), reasons };
}

// ── 돌발 사건 ──────────────────────────────────────────────
// 사건 가중치가 보는 지금의 사정
function eventContext(state) {
  return { status: state.status || {}, factionAvg: factionAverage(state) };
}

// 지금 상태에서 터진 위기들 (기업의 시대: 자금 경색, 조직 이탈, 고객 이탈 등). 매 턴 기록에 반드시 드러난다.
export function activeCrises(state) {
  const st = state.status;
  if (!st) return [];
  return modeOf(state).status.crises.filter((c) => c.test(st)).map(({ id, label, directive }) => ({ id, label, directive }));
}

function pickWeighted(items, weights, rng) {
  const total = weights.reduce((a, b) => a + b, 0);
  let r = rng() * total;
  for (let i = 0; i < items.length; i++) {
    r -= weights[i];
    if (r < 0) return items[i];
  }
  return items[items.length - 1];
}

// 명령 턴에는 가끔, 시간 경과에는 기간에 비례해 돌발 사건이 끼어든다. months는 소수일 수 있다(보름 = 0.5).
export function rollEvents(state, kind, months, rng = Math.random) {
  let count = 0;
  if (kind === 'command') {
    let chance = 0.1;
    if (state.status?.happiness < 35) chance += 0.05;
    if (state.status?.enemies?.length) chance += 0.05;
    if (rng() < chance) count = 1;
  } else if (kind === 'time') {
    if (months < 1) count = rng() < 0.2 ? 1 : 0;
    else if (months <= 2) count = rng() < 0.35 ? 1 : 0;
    else if (months < 12) count = rng() < 0.7 ? 1 : 0;
    else if (months < 36) count = 1 + (rng() < 0.5 ? 1 : 0);
    else count = 2 + (rng() < 0.5 ? 1 : 0);
  }
  const categories = modeOf(state).events;
  const ctx = eventContext(state);
  const events = [];
  const used = new Set();
  for (let i = 0; i < count; i++) {
    const pool = categories.filter((c) => !used.has(c.id));
    const pick = pickWeighted(pool, pool.map((c) => c.weight(ctx)), rng);
    used.add(pick.id);
    events.push({ id: pick.id, label: pick.label, hint: pick.hint });
  }
  return events;
}

// 오래 묵은 미결 안건 하나를 골라 조정이 다시 꺼내도록 한다.
export function pickStaleAgenda(state, rng = Math.random) {
  const stale = (state.policies || []).filter(
    (p) => PENDING_POLICY.has(p.status) && state.turnCount - (p.updatedTurn ?? 0) >= 5
  );
  if (!stale.length || rng() >= 0.5) return null;
  const p = stale[Math.floor(rng() * stale.length)];
  return { name: p.name, waited: state.turnCount - (p.updatedTurn ?? 0), status: p.status };
}

// ── 응답 파싱·정규화 ───────────────────────────────────────
export class ParseError extends Error {
  constructor(message, raw) {
    super(message);
    this.code = 'invalid_json';
    this.raw = raw;
  }
}

// 응답 전체, 코드 블록 본문, 첫 '{'부터 마지막 '}'까지 순서로 JSON을 찾는다.
export function parseJsonLoose(text) {
  const t = str(text);
  const attempts = [t];
  const fence = t.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fence) attempts.push(fence[1]);
  const first = t.indexOf('{');
  const last = t.lastIndexOf('}');
  if (first !== -1 && last > first) attempts.push(t.slice(first, last + 1));
  for (const a of attempts) {
    try {
      return JSON.parse(a);
    } catch {
      // 다음 후보로
    }
  }
  throw new ParseError('응답에서 JSON을 찾지 못했습니다.', text);
}

export function normalizeExecStatus(v) {
  const s = str(v);
  if (EXEC_STATUS.includes(s)) return s;
  if (/조건/.test(s)) return '조건부 시행';
  if (/반발/.test(s)) return '시행 후 반발';
  if (/반려|좌초|부결|기각|무산/.test(s)) return '반려';
  if (/보류|지연|논의|연기/.test(s)) return '보류';
  if (/시행|윤허|가결|재가/.test(s)) return '시행';
  return '해당없음';
}

// 상태창은 모드마다 항목이 다르므로 이름 그대로 읽는다: 배열은 목록, 숫자로 읽히면 수치, 아니면 목록 문자열
function readStatus(st) {
  const out = {};
  for (const [k, v] of Object.entries(st)) {
    if (Array.isArray(v)) out[k] = v.map(str).filter(Boolean);
    else if (v === null || v === undefined) continue;
    else {
      const n = num(v);
      out[k] = Number.isFinite(n) ? n : arr(v).map(str).filter(Boolean);
    }
  }
  return out;
}

export function normalizeResponse(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new ParseError('응답이 JSON 객체가 아닙니다.', raw);
  }
  const c = raw.conflict;
  const conflictSummary = c && typeof c === 'object' ? str(c.summary || c.message || c.reason) : str(c);
  if (conflictSummary && conflictSummary !== 'null') {
    return {
      conflict: {
        summary: conflictSummary,
        question: (c && typeof c === 'object' && str(c.question)) || '어떻게 진행하시겠습니까?',
      },
    };
  }

  const record = arr(raw.record)
    .map((e) =>
      typeof e === 'string'
        ? { speaker: '', title: '', text: str(e) }
        : { speaker: str(e?.speaker), title: str(e?.title), text: str(e?.text) }
    )
    .filter((e) => e.text);
  const st = raw.status && typeof raw.status === 'object' ? raw.status : {};
  const u = raw.updates && typeof raw.updates === 'object' ? raw.updates : {};
  const ex = raw.execution && typeof raw.execution === 'object' ? raw.execution : { status: raw.execution };
  const units = raw.units && typeof raw.units === 'object' ? raw.units : null;

  return {
    conflict: null,
    opening: str(raw.opening),
    record,
    execution: { status: normalizeExecStatus(ex.status), note: str(ex.note) },
    news: arr(raw.news).map(str).filter(Boolean).slice(0, 3),
    status: readStatus(st),
    eraLabel: str(raw.eraLabel),
    cashFlow: num(raw.cashFlow),
    units: units ? Object.fromEntries(Object.entries(units).map(([k, v]) => [k, str(v)])) : null,
    updates: {
      characters: arr(u.characters).filter((x) => x && typeof x === 'object' && str(x.name)),
      policies: arr(u.policies).filter((x) => x && typeof x === 'object' && str(x.name)),
      factions: arr(u.factions).filter((x) => x && typeof x === 'object' && str(x.name)),
      regions: arr(u.regions).filter((x) => x && typeof x === 'object' && str(x.name)),
    },
    chronicle: str(raw.chronicle),
    suggestions: arr(raw.suggestions).map(str).filter(Boolean).slice(0, 3),
  };
}

// ── 응답 검증 ──────────────────────────────────────────────
// level 'high'는 한 번 고쳐 쓰게 할 만한 기록 오류, 'note'는 엔진이 스스로 보정한 사항.
export function findCharacter(characters, speaker) {
  const k = nameKey(speaker);
  if (!k) return null;
  const exact = characters.find((c) => nameKey(c.name) === k);
  if (exact) return exact;
  // "영의정 하륜"처럼 직책이 붙어 와도 알아본다
  return characters.find((c) => nameKey(c.name).length >= 2 && k.includes(nameKey(c.name))) || null;
}

export function validateResponse(state, resp) {
  const issues = [];
  if (!resp.record.length) issues.push({ level: 'high', text: '기록 본문(record)이 비어 있습니다.' });
  if (!resp.opening) issues.push({ level: 'note', text: '도입 문장이 빠져 있어 엔진이 채웠습니다.' });

  const revived = new Set(resp.updates.characters.filter((c) => !DEAD.test(str(c.status))).map((c) => nameKey(c.name)));
  const reported = new Set();
  for (const r of resp.record) {
    if (!r.speaker) continue;
    const c = findCharacter(state.characters, r.speaker);
    if (c && DEAD.test(str(c.status)) && !revived.has(nameKey(c.name)) && !reported.has(c.name)) {
      reported.add(c.name);
      issues.push({
        level: 'high',
        text: `${c.name}은(는) 이미 ${c.status} 처리된 인물인데 발언자로 등장했습니다.`,
      });
    }
  }

  if (!state.status) {
    for (const f of modeOf(state).status.fields) {
      if (f.type !== 'list' && !f.derived && !Number.isFinite(resp.status[f.key])) {
        issues.push({ level: 'high', text: `첫 상태창에 ${f.label} 수치가 없습니다.` });
      }
    }
  }
  return issues;
}


// ── 상태창 보정 ────────────────────────────────────────────
// 한 턴 사이 수치가 기간에 비해 터무니없이 움직이면 상한까지만 반영하고 그 사실을 남긴다.
function capDelta(prev, next, maxUp, maxDown) {
  if (!Number.isFinite(next)) return { value: prev, capped: false, missing: true };
  if (!Number.isFinite(prev)) return { value: next, capped: false };
  const d = next - prev;
  if (d > maxUp) return { value: prev + maxUp, capped: true, asked: d };
  if (d < -maxDown) return { value: prev - maxDown, capped: true, asked: d };
  return { value: next, capped: false };
}

function signed(n, digits = 0) {
  const r = digits ? Math.round(n * 10 ** digits) / 10 ** digits : Math.round(n);
  return `${r > 0 ? '+' : r < 0 ? '−' : '±'}${Math.abs(r).toLocaleString('ko-KR')}`;
}

// 항목 종류: count(사람 수 등, 비율 상한), money(금액, caps가 있으면 비율 상한), score(0~100, 절대 폭 상한),
// percent(0~100 소수, cap이 있으면 %p 상한), list(이름 목록)
export function guardStatus(prev, next, months, periodLabel, mode = getMode()) {
  const notes = [];
  const s = {};
  const years = months / 12;
  const sway = Math.min(40, 12 + 2 * months);
  const val = (k) => (typeof next[k] === 'number' ? next[k] : NaN);
  for (const f of mode.status.fields) {
    const k = f.key;
    if (f.derived) continue; // 엔진의 돈 계산(economy)이 채운다
    if (f.type === 'list') {
      s[k] = [...new Set(Array.isArray(next[k]) ? next[k] : prev?.[k] || [])];
      continue;
    }
    const v = val(k);
    if (!prev) {
      if (f.type === 'score') s[k] = clamp(Math.round(Number.isFinite(v) ? v : 50), 0, 100);
      else if (f.type === 'percent') s[k] = clamp(Math.round((Number.isFinite(v) ? v : 0) * 10) / 10, 0, 100);
      else s[k] = f.signed ? Math.round(Number.isFinite(v) ? v : 0) : Math.max(0, Math.round(Number.isFinite(v) ? v : 0));
      continue;
    }
    if (f.type === 'score') {
      const r = capDelta(prev[k], v, sway, sway);
      s[k] = clamp(Math.round(r.value), 0, 100);
      if (r.capped) notes.push(`${f.label} 변동(${signed(r.asked)})이 ${periodLabel}에 비해 과도하여 ${signed(s[k] - prev[k])}로 보정`);
    } else if (f.type === 'percent') {
      const cap = f.cap ? f.cap[0] + f.cap[1] * years : Infinity;
      const r = capDelta(prev[k], v, cap, cap);
      s[k] = clamp(Math.round((Number.isFinite(r.value) ? r.value : prev[k]) * 10) / 10, 0, 100);
      if (r.capped) notes.push(`${f.label} 변동 폭이 ${periodLabel}에 비해 과도하여 보정`);
    } else if (f.caps) {
      const r = capDelta(
        prev[k] > 0 ? prev[k] : NaN,
        v,
        prev[k] * (f.caps.up[0] + f.caps.up[1] * years),
        prev[k] * Math.min(f.caps.down[2], f.caps.down[0] + f.caps.down[1] * years)
      );
      s[k] = Math.max(0, Math.round(Number.isFinite(r.value) ? r.value : prev[k] || 0));
      if (r.capped) notes.push(`${f.label} 변동 폭이 ${periodLabel}에 비해 과도하여 보정`);
    } else {
      const n = Math.round(Number.isFinite(v) ? v : prev[k]);
      s[k] = f.signed ? n : Math.max(0, n);
    }
  }
  // 동시에 오를 수 없는 목록 (동맹국과 적대국 등): 앞 목록에서 뺀다
  for (const [a, b] of mode.status.exclusive || []) {
    const both = s[a].filter((x) => s[b].includes(x));
    if (both.length) {
      s[a] = s[a].filter((x) => !both.includes(x));
      const la = mode.status.fields.find((f) => f.key === a).label;
      const lb = mode.status.fields.find((f) => f.key === b).label;
      notes.push(`${both.join(', ')}이(가) ${la}과 ${lb}에 함께 올라 있어 ${lb}으로 정리`);
    }
  }
  return { status: s, notes };
}

export function statusDeltas(prev, next, mode = getMode()) {
  if (!prev) return null;
  const d = {};
  for (const f of mode.status.fields) {
    const k = f.key;
    if (f.type === 'list') {
      const before = prev[k] || [];
      d[`${k}Added`] = next[k].filter((x) => !before.includes(x));
      d[`${k}Removed`] = before.filter((x) => !next[k].includes(x));
    } else if (Number.isFinite(prev[k])) {
      d[k] = f.type === 'percent' ? Math.round((next[k] - prev[k]) * 10) / 10 : next[k] - prev[k];
    }
  }
  return d;
}

// ── 세계 기록 병합 ─────────────────────────────────────────
function upsert(list, items, apply) {
  const out = list.map((x) => ({ ...x }));
  for (const item of items) {
    const k = nameKey(item.name);
    let target = out.find((x) => nameKey(x.name) === k);
    if (!target) {
      target = { name: str(item.name) };
      out.push(target);
      apply(target, item, true);
    } else {
      apply(target, item, false);
    }
  }
  return out;
}

function setIf(target, item, fields) {
  for (const f of fields) {
    const v = str(item[f]);
    if (v) target[f] = v;
  }
}

export function mergeCharacters(list, updates, record, ctx) {
  let out = upsert(list, updates, (c, u, isNew) => {
    if (isNew) {
      c.since = ctx.date;
      c.firstTurn = ctx.turn;
      c.quotes = [];
      c.status = '재직';
    }
    setIf(c, u, ['title', 'faction', 'disposition', 'status', 'note']);
    c.updatedTurn = ctx.turn;
  });
  for (const r of record) {
    if (!r.speaker) continue;
    const c = findCharacter(out, r.speaker);
    if (!c) continue;
    const quote = r.text.length > 70 ? `${r.text.slice(0, 69)}…` : r.text;
    c.quotes = [...(c.quotes || []), { turn: ctx.turn, text: quote }].slice(-2);
    if (!c.title && r.title) c.title = r.title;
  }
  return out;
}

export function mergePolicies(list, updates, ctx) {
  return upsert(list, updates, (p, u, isNew) => {
    const status = str(u.status) || p.status || '논의중';
    if (isNew) {
      p.since = ctx.date;
      p.raisedTurn = ctx.turn;
      p.history = [];
    }
    if (isNew || status !== p.status) {
      p.history = [...(p.history || []), { turn: ctx.turn, date: ctx.date, status }].slice(-6);
    }
    p.status = status;
    setIf(p, u, ['summary', 'backlash']);
    p.updatedTurn = ctx.turn;
  });
}

export function mergeFactions(list, updates, months) {
  const cap = 15 + months;
  return upsert(list, updates, (f, u, isNew) => {
    const v = num(u.support);
    if (Number.isFinite(v)) {
      const next = isNew || !Number.isFinite(f.support) ? v : clamp(v, f.support - cap, f.support + cap);
      f.support = clamp(Math.round(next), 0, 100);
    } else if (isNew) {
      f.support = 50;
    }
    setIf(f, u, ['note']);
  });
}

export function mergeRegions(list, updates) {
  return upsert(list, updates, (r, u) => setIf(r, u, ['note']));
}

export const MAX_TURN_LOG = 200;
export const MAX_CHRONICLE = 400;

// ctx: { kind: 'opening'|'command'|'time'|'resync', command, months, days, roll, events, issues }
// 흐르는 시간은 엔진이 정한다: 왕명은 ctx.days(판정에 따른 며칠), 시간 경과는 ctx.months·ctx.days.
export function applyResponse(state, resp, ctx) {
  const s = structuredClone(state);
  const turn = s.turnCount + 1;
  let months = 0;
  let days = 0;
  if (ctx.kind === 'time') {
    months = Math.max(0, Math.round(ctx.months || 0));
    days = Math.max(0, Math.round(ctx.days || 0));
  } else if (ctx.kind === 'command') {
    days = clamp(Math.round(ctx.days ?? 1), 0, DAYS_PER_MONTH - 1);
  }
  s.date = advanceDate(s.date, months, days);
  if (resp.eraLabel) s.eraLabel = resp.eraLabel;

  const mode = modeOf(s);
  // 단위는 첫 기록에서 정하고 바꾸지 않는다. 빠진 단위는 앞서 정한 단위나 모드 기본값으로 채운다.
  if (!s.units) {
    const given = mode.status.units.map((k) => resp.units?.[k]).find(Boolean);
    s.units = Object.fromEntries(mode.status.units.map((k) => [k, resp.units?.[k] || given || mode.status.defaultUnit]));
  }

  const prev = s.status;
  const span = spanInMonths(months, days);
  const period = months || days ? formatSpan(months, days) : '한 차례 회의';
  const { status, notes } = guardStatus(prev, resp.status, span, period, mode);
  // 돈 계산이 있는 모드(기업의 시대)는 손익과 현금을 엔진이 정한다
  const money = mode.status.economy?.({ prev, next: status, resp, months: span, notes }) || {};
  s.status = status;
  const deltas = statusDeltas(prev, status, mode);
  s.best = { ...(s.best || {}) };
  for (const f of mode.status.fields) {
    if (f.type !== 'list' && Number.isFinite(status[f.key])) s.best[f.key] = Math.max(s.best[f.key] ?? -Infinity, status[f.key]);
  }
  // 끝나는 조건이 있는 모드(기업의 시대: 폐업)
  const ending = mode.status.gameOver?.(prev, status);
  if (ending) s.over = { turn, date: s.date, reason: ending };

  // 분기 결산: 이번 턴에 분기가 넘어갔으면 마감한 분기의 숫자를 장부에 남긴다
  const settlement = mode.features?.quarterly ? settlementLabel(state.date, s.date) : '';
  if (settlement) {
    const values = Object.fromEntries(mode.ledger.keys.map((k) => [k, status[k]]));
    s.ledger = [...(s.ledger || []), { turn, label: settlement, date: s.date, values }].slice(-80);
  }

  const mctx = { turn, date: s.date };
  s.characters = mergeCharacters(s.characters, resp.updates.characters, resp.record, mctx);
  s.policies = mergePolicies(s.policies, resp.updates.policies, mctx);
  s.factions = mergeFactions(s.factions, resp.updates.factions, span);
  s.regions = mergeRegions(s.regions, resp.updates.regions);

  if (ctx.kind === 'command') {
    if (GOOD_EXEC.has(resp.execution.status)) s.streak = { good: s.streak.good + 1, bad: 0 };
    else if (BAD_EXEC.has(resp.execution.status)) s.streak = { good: 0, bad: s.streak.bad + 1 };
  }

  const line = resp.chronicle || resp.news[0] || resp.opening;
  if (line) {
    s.chronicle = [...s.chronicle, { turn, date: s.date, kind: ctx.kind, text: line }].slice(-MAX_CHRONICLE);
  }

  const issues = [...(ctx.issues || []), ...notes.map((text) => ({ level: 'note', text }))];
  const opening =
    resp.opening ||
    (ctx.kind === 'time'
      ? mode.ui.fallbackTimeOpening(formatDate(state.date), formatDate(s.date))
      : mode.ui.fallbackOpening(s.setup.country));

  s.turns = [
    ...s.turns,
    {
      n: turn,
      kind: ctx.kind,
      command: ctx.command || '',
      months,
      days,
      from: state.date,
      date: s.date,
      eraLabel: s.eraLabel,
      opening,
      record: resp.record,
      execution: resp.execution,
      news: resp.news,
      status,
      deltas,
      issues,
      settlement,
      cashFlow: money.cashFlow || 0,
      roll: ctx.roll || null,
      events: ctx.events || [],
      suggestions: resp.suggestions,
    },
  ].slice(-MAX_TURN_LOG);
  s.turnCount = turn;
  return s;
}

// ── 세계관 요약 (재동기화·내보내기용) ───────────────────────
export function worldSummary(state, fmt = formatAmount) {
  const mode = modeOf(state);
  const S = mode.summary;
  const L = [];
  const st = state.status;
  L.push(`${S.country}: ${state.setup.country} / ${S.ruler}: ${state.setup.ruler}`);
  L.push(`${S.institution}: ${state.setup.institution}`);
  if (state.setup.notes) L.push(`특이사항: ${state.setup.notes}`);
  L.push(`현재: ${formatDate(state.date)}${state.eraLabel ? ` (${state.eraLabel})` : ''} · ${state.turnCount}번째 기록`);
  if (st) {
    const fields = mode.status.fields;
    const nums = fields.filter((f) => f.type !== 'list');
    const lists = fields.filter((f) => f.type === 'list');
    L.push(`상태: ${nums.map((f) => `${f.label} ${formatField(f, st[f.key], state.units, fmt)}`).join(', ')}`);
    if (lists.length) L.push(lists.map((f) => `${f.label}: ${formatField(f, st[f.key])}`).join(' / '));
  }
  if (state.characters.length) {
    L.push('', `[${S.people}]`);
    for (const c of state.characters) {
      L.push(`- ${c.name} · ${c.title || '직책 미상'} · ${c.status || '재직'}${c.faction ? ` · ${c.faction}` : ''}${c.disposition ? ` · ${c.disposition}` : ''}`);
    }
  }
  if (state.policies.length) {
    L.push('', `[${S.policies}]`);
    for (const p of state.policies) {
      L.push(`- ${p.name} · ${p.status}${p.since ? ` (${formatDate(p.since)}~)` : ''}${p.summary ? ` · ${p.summary}` : ''}`);
    }
  }
  if (state.factions.length) {
    L.push('', `[${S.factions}]`);
    for (const f of state.factions) L.push(`- ${f.name} 지지 ${f.support}${f.note ? ` · ${f.note}` : ''}`);
  }
  if (state.chronicle.length) {
    L.push('', `[${S.annals}]`);
    for (const c of state.chronicle.slice(-10)) L.push(`- ${formatDate(c.date)} ${c.text}`);
  }
  return L.join('\n');
}

// ── 분기 ───────────────────────────────────────────────────
export function quarterOf(date) {
  return Math.floor((date.month - 1) / 3) + 1;
}

function quarterIndex(date) {
  return date.year * 4 + quarterOf(date) - 1;
}

export function quarterLabel(index) {
  return `${Math.floor(index / 4)}년 ${(index % 4) + 1}분기`;
}

// from에서 to로 넘어가며 마감한 분기의 이름. 없으면 ''. 여러 분기를 건너면 "2020년 2분기~2021년 1분기".
export function settlementLabel(from, to) {
  const a = quarterIndex(from);
  const b = quarterIndex(to);
  if (b <= a) return '';
  return b - a === 1 ? quarterLabel(a) : `${quarterLabel(a)}~${quarterLabel(b - 1)}`;
}

// 다음 분기 첫날까지 남은 기간 ("분기 마감까지" 버튼)
export function spanToNextQuarter(date) {
  const day = (date.day || 1) - 1;
  const nextQuarterMonth = quarterOf(date) * 3; // 0부터 센 다음 분기 첫 달
  const total = (nextQuarterMonth - (date.month - 1)) * DAYS_PER_MONTH - day;
  return { months: Math.floor(total / DAYS_PER_MONTH), days: total % DAYS_PER_MONTH };
}

// 상태창 항목 하나를 글로 쓴다
export function formatField(f, v, units, fmt = formatAmount) {
  if (f.type === 'list') return v?.length ? v.join(', ') : '없음';
  if (!Number.isFinite(v)) return '—';
  if (f.type === 'score') return `${v}`;
  if (f.type === 'percent') return `${v}%`;
  if (f.type === 'money') return fmt(v, units?.[f.unitKey]);
  return fmt(v, f.unit);
}

// ── 수치 표기 ──────────────────────────────────────────────
export function formatBig(n) {
  if (!Number.isFinite(n)) return '—';
  const a = Math.abs(n);
  const sign = n < 0 ? '−' : '';
  const trim = (x) => String(Math.round(x * 100) / 100).replace(/\.0+$/, '');
  if (a >= 1e8) return `${sign}${trim(a / 1e8)}억`;
  if (a >= 1e4) return `${sign}${trim(Math.round(a / 1e3) / 10)}만`;
  return `${sign}${Math.round(a).toLocaleString('ko-KR')}`;
}

// "2명", "950원"처럼 숫자 바로 뒤의 한 글자 단위는 붙이고, "552만 명", "38억 원"은 띄운다
export function formatAmount(n, unit) {
  const b = formatBig(n);
  if (!unit) return b;
  return /\d$/.test(b) && unit.length === 1 ? `${b}${unit}` : `${b} ${unit}`;
}

export function formatSignedBig(n) {
  if (!Number.isFinite(n) || n === 0) return '';
  return `${n > 0 ? '▲' : '▼'} ${formatBig(Math.abs(n))}`;
}

// ── 저장 ───────────────────────────────────────────────────
export function serialize(state) {
  return JSON.stringify(state);
}

// expectMode를 주면 다른 게임의 저장 기록을 거절한다 (국가의 시대 기록을 기업의 시대에 불러오는 일 등)
export function deserialize(text, expectMode) {
  const data = typeof text === 'string' ? JSON.parse(text) : text;
  const wanted = expectMode ? getMode(expectMode).title : '국가의 시대·기업의 시대';
  if (!data || typeof data !== 'object' || data.version !== SAVE_VERSION || !data.setup || !data.date) {
    throw new Error(`${wanted} 저장 기록이 아닙니다.`);
  }
  data.setup.mode = MODES[data.setup.mode] ? data.setup.mode : 'kingdom';
  if (expectMode && data.setup.mode !== expectMode) {
    throw new Error(`${getMode(data.setup.mode).title}의 저장 기록입니다. ${wanted}에서는 불러올 수 없습니다.`);
  }
  getMode(data.setup.mode).migrate?.(data);
  for (const k of ['characters', 'policies', 'factions', 'regions', 'chronicle', 'ledger', 'turns']) {
    if (!Array.isArray(data[k])) data[k] = [];
  }
  if (!data.date.day) data.date = { ...data.date, day: 1 };
  data.streak = data.streak || { good: 0, bad: 0 };
  data.best = data.best || {};
  data.over = data.over || null;
  data.turnCount = Number(data.turnCount) || data.turns.length;
  return data;
}
