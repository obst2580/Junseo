// 국가의 시대 — 게임 엔진.
// DOM도 Claude 호출도 모르는 순수 로직만 둔다: 판정 주사위, 돌발 사건, 시간 계산,
// 응답 정규화·검증, 세계 기록 병합. 서술은 Claude가, 규칙과 기억은 엔진이 맡는다.

export const SAVE_VERSION = 1;

// ── 실행 판정 ──────────────────────────────────────────────
// 플레이어 명령이 그대로 관철되지 않도록 엔진이 먼저 결과의 등급을 정한다.
// Claude는 이 등급을 어떤 사정으로 그렇게 되었는지 서사로 풀어낼 뿐 바꾸지 못한다.
export const OUTCOMES = {
  smooth: {
    label: '순조',
    directive:
      '순조 — 명령은 큰 저항 없이 시행된다. 그래도 소수 의견이나 현실적 우려 한 가지는 기록한다.',
  },
  conditional: {
    label: '조건부',
    directive:
      '조건부 — 관료들이 수정안·단계적 시행·예외 조항·시범 지역 같은 조건을 붙여 일부만, 또는 범위를 줄여 시행된다. 어떤 조건이 붙었는지 구체적으로 쓴다.',
  },
  delayed: {
    label: '지연',
    directive:
      '지연 — 논의가 결론나지 않거나 재원·인력·시기 문제로 시행이 미뤄진다. 정책은 논의중 또는 보류 상태로 남고, 다시 논의하려면 무엇이 필요한지(재론 조건)를 관료의 입으로 밝힌다.',
  },
  backlash: {
    label: '반발',
    directive:
      '반발 — 명령은 시행되지만 특정 계층이나 세력이 강하게 반발한다(연명 상소, 집단 사직, 지방의 불복, 민심 악화 등). 해당 세력의 지지도나 국민 여론이 떨어진다.',
  },
  blocked: {
    label: '좌초',
    directive:
      '좌초 — 강한 반대나 현실적 불가능 때문에 명령이 반려되거나 시행 직후 무력화된다. 그 이유는 역사적·제도적으로 설득력이 있어야 한다.',
  },
};

// Claude가 돌려주는 실행 결과 표기. 화면의 결재 도장과 연속 판정 기록에 쓰인다.
export const EXEC_STATUS = ['시행', '조건부 시행', '보류', '시행 후 반발', '반려', '해당없음'];
const GOOD_EXEC = new Set(['시행', '조건부 시행']);
const BAD_EXEC = new Set(['보류', '시행 후 반발', '반려']);

export const DIFFICULTY = {
  mild: { label: '온건', mod: -15, desc: '관료들이 대체로 협조합니다' },
  standard: { label: '표준', mod: 0, desc: '사안마다 이견과 견제가 따릅니다' },
  harsh: { label: '혹독', mod: 15, desc: '조정이 사사건건 제동을 겁니다' },
};

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
  `^(\\d+|${Object.keys(KO_NUM).join('|')})\\s*(년|해|개월|달|주|일)\\s*${ELAPSE}$`
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
  const word = t.match(new RegExp(`^(${Object.keys(DAY_WORDS).join('|')}|한\\s*주)\\s*${ELAPSE}$`));
  if (word) return { months: 0, days: DAY_WORDS[word[1].replace(/\s+/g, '')] };
  const m = t.match(SKIP_RE);
  if (!m) return null;
  const n = /^\d+$/.test(m[1]) ? parseInt(m[1], 10) : KO_NUM[m[1]];
  if (!n || n <= 0) return null;
  if (m[2] === '년' || m[2] === '해') return { months: Math.min(n * 12, MAX_SKIP_MONTHS), days: 0 };
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
      scenarioId: str(setup.scenarioId) || 'custom',
      country: str(setup.country),
      ruler: str(setup.ruler),
      startYear: year,
      startMonth: month,
      institution: str(setup.institution),
      notes: str(setup.notes),
      difficulty: DIFFICULTY[setup.difficulty] ? setup.difficulty : 'standard',
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
    turns: [],
    streak: { good: 0, bad: 0 },
    turnCount: 0,
  };
}

export function validateSetup(setup) {
  const errors = [];
  if (!str(setup.country)) errors.push('국가를 입력하십시오.');
  if (!str(setup.ruler)) errors.push('군주(플레이어)의 칭호를 입력하십시오.');
  const y = num(setup.startYear);
  if (!Number.isFinite(y) || Math.abs(y) > 3000) errors.push('시작 연도를 숫자로 입력하십시오.');
  if (!str(setup.institution)) errors.push('제도를 한 줄 이상 적어 주십시오.');
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
  const reasons = [];
  let mod = 0;
  const s = state.status;
  if (s) {
    if (s.opinion < 25) {
      mod += 12;
      reasons.push('민심 이반');
    } else if (s.opinion < 40) {
      mod += 5;
      reasons.push('민심 동요');
    } else if (s.opinion >= 70) {
      mod -= 5;
      reasons.push('민심의 지지');
    }
    if (s.treasury < 0) {
      mod += 8;
      reasons.push('국고 고갈');
    }
  }
  const avg = factionAverage(state);
  if (avg !== null) {
    if (avg < 35) {
      mod += 8;
      reasons.push('조정 불신');
    } else if (avg >= 65) {
      mod -= 5;
      reasons.push('조정의 결속');
    }
  }
  // 연이어 성사되면 견제가 붙지만 두 번째부터, 그리고 완만하게
  const good = Math.min(state.streak?.good || 0, 4);
  if (good >= 2) {
    mod += (good - 1) * 3;
    reasons.push('연이은 성사에 대한 견제');
  }
  // 막히고 나면 조정도 타협을 찾는다
  const bad = Math.min(state.streak?.bad || 0, 3);
  if (bad >= 1) {
    mod -= bad * 8;
    reasons.push('앞선 차질 뒤의 타협 분위기');
  }
  mod += DIFFICULTY[state.setup.difficulty]?.mod || 0;

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
    reasons = [...reasons, `재론된 안건(${revisit.name})이라 논의가 무르익음`];
  }
  const score = clamp(base + mod, 1, 120);
  return { base, mod, score, tier: tierForScore(score), reasons };
}

// ── 돌발 사건 ──────────────────────────────────────────────
export const EVENT_CATEGORIES = [
  { id: 'disaster', label: '자연재해', hint: '가뭄·홍수·냉해·병충해·지진 가운데 시대와 지역에 맞는 것', weight: () => 3 },
  { id: 'plague', label: '역병', hint: '도성이나 특정 지방에 번지는 돌림병', weight: () => 1.5 },
  { id: 'diplomacy', label: '외교', hint: '사신 내방, 국서, 책봉·조공·통상 요구, 혼인 동맹 제안 등', weight: () => 2 },
  {
    id: 'border',
    label: '국경·군사',
    hint: '변경 침입, 해적, 국경 분쟁, 군량 부족',
    weight: (s) => 1.5 + (s.status?.enemies?.length ? 1.5 : 0),
  },
  {
    id: 'politics',
    label: '정치',
    hint: '탄핵 상소, 붕당·파벌 대립, 역모 고변, 대신의 사직 소동',
    weight: (s) => {
      const avg = factionAverage(s);
      return 2 + (avg !== null && avg < 45 ? 1 : 0);
    },
  },
  {
    id: 'economy',
    label: '민생·경제',
    hint: '흉년, 물가 급등, 도적 횡행, 민란 조짐, 화폐·조세 문제',
    weight: (s) => 2 + (s.status && s.status.happiness < 35 ? 2 : 0),
  },
  { id: 'people', label: '인물', hint: '원로 대신의 와병·사망·은퇴, 뜻밖의 인재 등장', weight: () => 1 },
  { id: 'fortune', label: '길보', hint: '풍년, 새 기술·서적, 외국 사절의 호의 같은 좋은 소식', weight: () => 1.5 },
];

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
  const events = [];
  const used = new Set();
  for (let i = 0; i < count; i++) {
    const pool = EVENT_CATEGORIES.filter((c) => !used.has(c.id));
    const pick = pickWeighted(pool, pool.map((c) => c.weight(state)), rng);
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
    status: {
      population: num(st.population),
      allies: st.allies === undefined ? null : arr(st.allies).map(str).filter(Boolean),
      enemies: st.enemies === undefined ? null : arr(st.enemies).map(str).filter(Boolean),
      opinion: num(st.opinion),
      happiness: num(st.happiness),
      treasury: num(st.treasury),
      gdp: num(st.gdp),
    },
    eraLabel: str(raw.eraLabel),
    units: units ? { treasury: str(units.treasury), gdp: str(units.gdp) } : null,
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
    for (const k of ['population', 'opinion', 'happiness', 'treasury', 'gdp']) {
      if (!Number.isFinite(resp.status[k])) {
        issues.push({ level: 'high', text: `첫 상태창에 ${STATUS_LABEL[k]} 수치가 없습니다.` });
      }
    }
  }
  return issues;
}

export const STATUS_LABEL = {
  population: '인구',
  allies: '동맹국',
  enemies: '적대국',
  opinion: '국민 여론',
  happiness: '국민 행복도',
  treasury: '재정',
  gdp: 'GDP',
};

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

export function guardStatus(prev, next, months, periodLabel) {
  const notes = [];
  const s = {};
  if (!prev) {
    s.population = Math.max(0, Math.round(next.population || 0));
    s.allies = next.allies || [];
    s.enemies = next.enemies || [];
    s.opinion = clamp(Math.round(Number.isFinite(next.opinion) ? next.opinion : 50), 0, 100);
    s.happiness = clamp(Math.round(Number.isFinite(next.happiness) ? next.happiness : 50), 0, 100);
    s.treasury = Math.round(Number.isFinite(next.treasury) ? next.treasury : 0);
    s.gdp = Math.max(0, Math.round(Number.isFinite(next.gdp) ? next.gdp : 0));
  } else {
    const years = months / 12;
    const sway = Math.min(40, 12 + 2 * months);
    for (const k of ['opinion', 'happiness']) {
      const r = capDelta(prev[k], next[k], sway, sway);
      s[k] = clamp(Math.round(r.value), 0, 100);
      if (r.capped) {
        notes.push(`${STATUS_LABEL[k]} 변동(${signed(r.asked)})이 ${periodLabel}에 비해 과도하여 ${signed(s[k] - prev[k])}로 보정`);
      }
    }
    const pop = capDelta(
      prev.population > 0 ? prev.population : NaN,
      next.population,
      prev.population * (0.02 + 0.02 * years),
      prev.population * Math.min(0.5, 0.06 + 0.05 * years)
    );
    s.population = Math.max(0, Math.round(Number.isFinite(pop.value) ? pop.value : prev.population || 0));
    if (pop.capped) notes.push(`인구 변동 폭이 ${periodLabel}에 비해 과도하여 보정`);
    const gdp = capDelta(
      prev.gdp > 0 ? prev.gdp : NaN,
      next.gdp,
      prev.gdp * (0.08 + 0.08 * years),
      prev.gdp * Math.min(0.6, 0.15 + 0.08 * years)
    );
    s.gdp = Math.max(0, Math.round(Number.isFinite(gdp.value) ? gdp.value : prev.gdp || 0));
    if (gdp.capped) notes.push(`GDP 변동 폭이 ${periodLabel}에 비해 과도하여 보정`);
    s.treasury = Math.round(Number.isFinite(next.treasury) ? next.treasury : prev.treasury);
    s.allies = next.allies ?? prev.allies;
    s.enemies = next.enemies ?? prev.enemies;
  }
  s.allies = [...new Set(s.allies)];
  s.enemies = [...new Set(s.enemies)];
  const both = s.allies.filter((a) => s.enemies.includes(a));
  if (both.length) {
    s.allies = s.allies.filter((a) => !both.includes(a));
    notes.push(`${both.join(', ')}이(가) 동맹국과 적대국에 함께 올라 있어 적대국으로 정리`);
  }
  return { status: s, notes };
}

export function statusDeltas(prev, next) {
  if (!prev) return null;
  const d = {};
  for (const k of ['population', 'opinion', 'happiness', 'treasury', 'gdp']) d[k] = next[k] - prev[k];
  d.alliesAdded = next.allies.filter((x) => !prev.allies.includes(x));
  d.alliesRemoved = prev.allies.filter((x) => !next.allies.includes(x));
  d.enemiesAdded = next.enemies.filter((x) => !prev.enemies.includes(x));
  d.enemiesRemoved = prev.enemies.filter((x) => !next.enemies.includes(x));
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

  if (!s.units) {
    s.units = {
      treasury: resp.units?.treasury || '냥',
      gdp: resp.units?.gdp || resp.units?.treasury || '냥',
    };
  }

  const prev = s.status;
  const span = spanInMonths(months, days);
  const period = months || days ? formatSpan(months, days) : '한 차례 회의';
  const { status, notes } = guardStatus(prev, resp.status, span, period);
  s.status = status;
  const deltas = statusDeltas(prev, status);

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
      ? `${formatDate(state.date)}부터 ${formatDate(s.date)}까지의 국정을 정리합니다...`
      : `${s.setup.country} 조정에서 논의가 시작됩니다...`);

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
  const L = [];
  const st = state.status;
  L.push(`국가: ${state.setup.country} / 군주: ${state.setup.ruler}`);
  L.push(`제도: ${state.setup.institution}`);
  if (state.setup.notes) L.push(`특이사항: ${state.setup.notes}`);
  L.push(`현재: ${formatDate(state.date)}${state.eraLabel ? ` (${state.eraLabel})` : ''} · ${state.turnCount}번째 기록`);
  if (st) {
    L.push(
      `상태: 인구 ${fmt(st.population, '명')}, 여론 ${st.opinion}, 행복도 ${st.happiness}, 재정 ${fmt(st.treasury, state.units?.treasury)}, GDP ${fmt(st.gdp, state.units?.gdp)}`
    );
    L.push(`동맹국: ${st.allies.join(', ') || '없음'} / 적대국: ${st.enemies.join(', ') || '없음'}`);
  }
  if (state.characters.length) {
    L.push('', '[인물]');
    for (const c of state.characters) {
      L.push(`- ${c.name} · ${c.title || '직책 미상'} · ${c.status || '재직'}${c.faction ? ` · ${c.faction}` : ''}${c.disposition ? ` · ${c.disposition}` : ''}`);
    }
  }
  if (state.policies.length) {
    L.push('', '[정책·제도]');
    for (const p of state.policies) {
      L.push(`- ${p.name} · ${p.status}${p.since ? ` (${formatDate(p.since)}~)` : ''}${p.summary ? ` · ${p.summary}` : ''}`);
    }
  }
  if (state.factions.length) {
    L.push('', '[세력·계층]');
    for (const f of state.factions) L.push(`- ${f.name} 지지 ${f.support}${f.note ? ` · ${f.note}` : ''}`);
  }
  if (state.chronicle.length) {
    L.push('', '[최근 연대기]');
    for (const c of state.chronicle.slice(-10)) L.push(`- ${formatDate(c.date)} ${c.text}`);
  }
  return L.join('\n');
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

export function formatAmount(n, unit) {
  const b = formatBig(n);
  return unit ? `${b} ${unit}` : b;
}

export function formatSignedBig(n) {
  if (!Number.isFinite(n) || n === 0) return '';
  return `${n > 0 ? '▲' : '▼'} ${formatBig(Math.abs(n))}`;
}

// ── 저장 ───────────────────────────────────────────────────
export function serialize(state) {
  return JSON.stringify(state);
}

export function deserialize(text) {
  const data = typeof text === 'string' ? JSON.parse(text) : text;
  if (!data || typeof data !== 'object' || data.version !== SAVE_VERSION || !data.setup || !data.date) {
    throw new Error('국가의 시대 저장 기록이 아닙니다.');
  }
  for (const k of ['characters', 'policies', 'factions', 'regions', 'chronicle', 'turns']) {
    if (!Array.isArray(data[k])) data[k] = [];
  }
  if (!data.date.day) data.date = { ...data.date, day: 1 };
  data.streak = data.streak || { good: 0, bad: 0 };
  data.turnCount = Number(data.turnCount) || data.turns.length;
  return data;
}
