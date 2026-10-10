// 국가의 시대 — 사관(Claude) 연결.
// 세 가지 경로를 같은 모양으로 감싼다: generate(prompt, {signal, onText, tier, meta}) → 응답 전문.
//  1) claude.ai 아티팩트 안: window.claude의 sample 기능 (보는 사람의 Claude 계정)
//  2) 로컬 서버(server.mjs): /api/turn이 Anthropic SDK로 Claude를 부른다
//  3) #mock: 화면 점검용 가짜 사관

export class LlmError extends Error {
  constructor(code, message, partial) {
    super(message || code);
    this.code = code;
    this.partial = partial;
  }
}

const MESSAGES = {
  cancelled: '기록을 중단했습니다.',
  not_granted: '이 페이지가 Claude를 쓰도록 허용되지 않았습니다. 허용해야 사관이 기록할 수 있습니다.',
  sampling_disabled: '이 계정에서는 Claude를 쓸 수 없습니다.',
  rate_limited: '요청이 많아 잠시 막혔습니다. 조금 뒤 다시 하명하십시오.',
  session_expired: '로그인이 만료되었습니다. 다시 로그인한 뒤 이어 하십시오.',
  refused: '사관이 이 명령의 기록을 거절했습니다. 명령을 바꿔 다시 내려 주십시오.',
  truncated: '기록이 너무 길어 중간에 끊겼습니다. 다시 하명하십시오.',
  invalid_json: '사관의 기록 형식이 어긋났습니다. 다시 하명하십시오.',
  prompt_too_large: '세계 기록이 너무 커졌습니다. 기록을 내보낸 뒤 새로 시작하십시오.',
  auth: '서버의 API 키가 없거나 잘못되었습니다. ANTHROPIC_API_KEY를 확인하십시오.',
  unavailable: '사관과 연결되어 있지 않습니다.',
  upstream_error: '사관과의 연결이 잠시 끊겼습니다. 다시 하명하십시오.',
};

export function errorMessage(err) {
  return MESSAGES[err?.code] || MESSAGES.upstream_error;
}

// ── 1) claude.ai 아티팩트 ──────────────────────────────────
async function sampleBackend() {
  if (!window.claude?.use) return null;
  let sample = null;
  try {
    sample = await window.claude.use('sample');
  } catch {
    sample = null;
  }
  if (!sample) return null;
  return {
    id: 'sample',
    label: 'Claude (claude.ai)',
    async generate(prompt, { signal, onText, tier }) {
      try {
        const { text, truncated } = await sample(prompt, {
          signal,
          modelTier: tier,
          cache: false,
          onText: ({ text }) => onText?.(text),
        });
        if (truncated) throw new LlmError('truncated', MESSAGES.truncated, text);
        return text;
      } catch (e) {
        if (e instanceof LlmError) throw e;
        throw new LlmError(e?.code || 'upstream_error', e?.message, e?.text);
      }
    },
  };
}

// ── 2) 로컬 서버 ───────────────────────────────────────────
async function serverBackend() {
  if (!/^https?:$/.test(location.protocol)) return null;
  try {
    const res = await fetch('api/health', { headers: { accept: 'application/json' } });
    if (!res.ok) return null;
    const info = await res.json();
    if (!info?.service || info.service !== 'gukga') return null;
    return {
      id: 'server',
      label: info.ok ? `서버 (${info.model})` : '서버 (API 키 없음)',
      ready: Boolean(info.ok),
      async generate(prompt, { signal, onText, tier }) {
        let res;
        try {
          res = await fetch('api/turn', {
            method: 'POST',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ prompt, tier }),
            signal,
          });
        } catch (e) {
          if (signal?.aborted) throw new LlmError('cancelled');
          throw new LlmError('upstream_error', e?.message);
        }
        if (!res.ok || !res.body) {
          const body = await res.json().catch(() => ({}));
          throw new LlmError(body.code || 'upstream_error', body.message);
        }
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buf = '';
        let text = '';
        try {
          for (;;) {
            const { value, done } = await reader.read();
            if (done) break;
            buf += decoder.decode(value, { stream: true });
            let nl;
            while ((nl = buf.indexOf('\n')) !== -1) {
              const line = buf.slice(0, nl).trim();
              buf = buf.slice(nl + 1);
              if (!line) continue;
              const ev = JSON.parse(line);
              if (ev.t === 'delta') {
                text += ev.d;
                onText?.(text);
              } else if (ev.t === 'done') {
                return ev.text ?? text;
              } else if (ev.t === 'error') {
                throw new LlmError(ev.code || 'upstream_error', ev.message, text);
              }
            }
          }
        } catch (e) {
          if (e instanceof LlmError) throw e;
          if (signal?.aborted) throw new LlmError('cancelled', '', text);
          throw new LlmError('upstream_error', e?.message, text);
        }
        throw new LlmError('upstream_error', '응답이 끝나기 전에 연결이 닫혔습니다.', text);
      },
    };
  } catch {
    return null;
  }
}

// ── 3) 화면 점검용 가짜 사관 (#mock) ───────────────────────
const MOCK_PEOPLE = [
  ['하경복', '영의정', '원로', '신중하고 현실적'],
  ['정윤수', '호조판서', '재정파', '셈에 밝고 비용에 민감'],
  ['류성원', '대사헌', '언관', '원칙을 앞세우는 강직한 성품'],
  ['김치형', '병조판서', '무반', '변경 방비를 중시'],
  ['이숙오', '도승지', '근신', '왕명 출납에 충실'],
];
const EXEC_BY_TIER = {
  smooth: '시행',
  conditional: '조건부 시행',
  delayed: '보류',
  backlash: '시행 후 반발',
  blocked: '반려',
};

export function mockResponse(meta) {
  const s = meta.state;
  const st = s.status || {
    population: 5520000,
    allies: ['명'],
    enemies: ['왜구'],
    opinion: 52,
    happiness: 47,
    treasury: 380000,
    gdp: 12400000,
  };
  const country = s.setup.country;
  if (meta.kind === 'command' && !meta.resolution && /재도입|다시 시행/.test(meta.command)) {
    return {
      conflict: {
        summary: '해당 제도는 이미 시행 중이며, 당시 지방 세력의 반발을 샀습니다.',
        question: '재도입하시겠습니까?',
      },
    };
  }
  const tier = meta.roll?.tier || 'conditional';
  const exec = meta.kind === 'command' ? EXEC_BY_TIER[tier] : '해당없음';
  const [a, b, c] = MOCK_PEOPLE;
  const topic = meta.kind === 'command' ? meta.command.slice(0, 18) : '즉위 직후의 국정';
  const opinionShift = { smooth: 3, conditional: 1, delayed: -1, backlash: -6, blocked: -3 }[tier];
  return {
    conflict: null,
    opening:
      meta.kind === 'time'
        ? `지난 ${meta.months ? `${meta.months}개월` : `${meta.days}일`}간의 국정을 정리합니다...`
        : `${country} 관리들과 ${topic}에 관해 의논합니다...`,
    record: [
      { speaker: '', title: '', text: '편전에 대신들이 모여 왕명을 받들었다.' },
      { speaker: a[0], title: a[1], text: '뜻은 옳으나 시행의 완급을 살펴야 할 줄 아뢰옵니다.' },
      { speaker: b[0], title: b[1], text: '국고의 형편으로는 한 해에 다 감당하기 어렵사옵니다.' },
      { speaker: c[0], title: c[1], text: '지방 수령들이 받들지 않을까 염려되옵니다.' },
      { speaker: '', title: '', text: '논의 끝에 조정은 결론을 기록하였다.' },
    ],
    execution: { status: exec, note: meta.kind === 'command' ? `판정 ${tier}에 따른 점검용 결과` : '' },
    news: ['도성 백성들, 새 명령을 두고 의견 분분', '성균관 유생들 상소 준비 중이라는 소문', '지방 수령들 시행 세칙 문의'],
    status: {
      ...st,
      opinion: Math.max(0, Math.min(100, st.opinion + (meta.kind === 'command' ? opinionShift : 0))),
      treasury: st.treasury - (meta.kind === 'command' ? 12000 : 0),
      population: st.population + (meta.kind === 'time' ? Math.round(st.population * 0.004 * ((meta.months || 0) + (meta.days || 0) / 30)) : 0),
    },
    units: s.units ? undefined : { treasury: '석', gdp: '석' },
    eraLabel: "점검 원년",
    updates: {
      characters: MOCK_PEOPLE.map(([name, title, faction, disposition]) => ({ name, title, faction, disposition, status: '재직' })),
      policies:
        meta.kind === 'command'
          ? [{ name: meta.command.slice(0, 16), status: exec === '보류' ? '보류' : exec === '반려' ? '좌초' : '시행', summary: '점검용 정책' }]
          : meta.kind === 'opening'
            ? [{ name: '변경 방비 강화', status: '논의중', summary: '북방 진보 수축 문제' }]
            : [],
      factions:
        meta.kind === 'opening'
          ? [
              { name: '훈구 공신', support: 58 },
              { name: '종친', support: 44 },
              { name: '지방 세력', support: 39 },
              { name: '백성', support: 51 },
            ]
          : [{ name: '지방 세력', support: 35 }],
      regions: [],
    },
    chronicle: meta.kind === 'command' ? `${meta.command.slice(0, 20)} — ${exec}` : '조정이 국정을 정리하였다',
    suggestions: ['호조에 재원 마련책을 올리게 하라', '반대하는 대간을 불러 뜻을 들어 보라'],
  };
}

function mockBackend() {
  return {
    id: 'mock',
    label: '점검용 가짜 사관',
    async generate(prompt, { signal, onText, meta }) {
      const text = JSON.stringify(mockResponse(meta), null, 1);
      for (let i = 400; i < text.length; i += 400) {
        if (signal?.aborted) throw new LlmError('cancelled');
        await new Promise((r) => setTimeout(r, 60));
        onText?.(text.slice(0, i));
      }
      if (signal?.aborted) throw new LlmError('cancelled');
      onText?.(text);
      return text;
    },
  };
}

export async function detectBackend() {
  if (location.hash === '#mock') return mockBackend();
  if (window.claude?.use) {
    const b = await sampleBackend();
    if (b) return b;
  }
  return serverBackend();
}
