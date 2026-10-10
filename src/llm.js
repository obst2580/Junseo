// 국가의 시대·기업의 시대 — 기록자(Claude) 연결.
// 세 가지 경로를 같은 모양으로 감싼다: generate(prompt, {signal, onText, tier, meta}) → 응답 전문.
//  1) claude.ai 아티팩트 안: window.claude의 sample 기능 (보는 사람의 Claude 계정)
//  2) 로컬 서버(server.mjs): /api/turn이 Anthropic SDK로 Claude를 부른다
//  3) #mock: 화면 점검용 가짜 기록자

import { modeOf } from './engine.js';

export class LlmError extends Error {
  constructor(code, message, partial) {
    super(message || code);
    this.code = code;
    this.partial = partial;
  }
}

// 받침에 따라 조사를 고른다: josa('사관', '이', '가') → '사관이', josa('서기', '이', '가') → '서기가'
function josa(word, withBatchim, without) {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  const has = code >= 0 && code <= 11171 && code % 28 !== 0;
  return word + (has ? withBatchim : without);
}

// scribe: 기록을 맡은 사람의 이름 (국가의 시대 '사관', 기업의 시대 '서기')
export function errorMessage(err, scribe = '사관') {
  const i = josa(scribe, '이', '가');
  const messages = {
    cancelled: '기록을 중단했습니다.',
    not_granted: `이 페이지가 Claude를 쓰도록 허용되지 않았습니다. 허용해야 ${i} 기록할 수 있습니다.`,
    sampling_disabled: '이 계정에서는 Claude를 쓸 수 없습니다.',
    rate_limited: '요청이 많아 잠시 막혔습니다. 조금 뒤 다시 내려 주십시오.',
    session_expired: '로그인이 만료되었습니다. 다시 로그인한 뒤 이어 하십시오.',
    refused: `${i} 이 명령의 기록을 거절했습니다. 명령을 바꿔 다시 내려 주십시오.`,
    truncated: '기록이 너무 길어 중간에 끊겼습니다. 다시 내려 주십시오.',
    invalid_json: `${scribe}의 기록 형식이 어긋났습니다. 다시 내려 주십시오.`,
    prompt_too_large: '세계 기록이 너무 커졌습니다. 기록을 내보낸 뒤 새로 시작하십시오.',
    auth: '서버의 API 키가 없거나 잘못되었습니다. ANTHROPIC_API_KEY를 확인하십시오.',
    unavailable: `${josa(scribe, '과', '와')} 연결되어 있지 않습니다.`,
    upstream_error: `${josa(scribe, '과', '와')}의 연결이 잠시 끊겼습니다. 다시 내려 주십시오.`,
  };
  return messages[err?.code] || messages.upstream_error;
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
        if (truncated) throw new LlmError('truncated', '', text);
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

// ── 3) 화면 점검용 가짜 기록자 (#mock) ─────────────────────
// 판정·충돌·시간 경과 흐름을 Claude 없이 확인한다. 재료는 모드의 mock에 있다.
const EXEC_BY_TIER = {
  smooth: '시행',
  conditional: '조건부 시행',
  delayed: '보류',
  backlash: '시행 후 반발',
  blocked: '반려',
};

export function mockResponse(meta) {
  const s = meta.state;
  const M = modeOf(s).mock;
  const K = M.keys; // 판정에 따라 움직일 항목: 평판(mood), 비용이 빠지는 돈(cost), 시간에 따라 느는 수(grow)
  const st = s.status || M.status;
  if (meta.kind === 'command' && !meta.resolution && /재도입|다시 시행|다시 도입/.test(meta.command)) {
    return {
      conflict: {
        summary: '해당 제도는 이미 시행 중이며, 당시 반발을 샀습니다.',
        question: '다시 도입하시겠습니까?',
      },
    };
  }
  const tier = meta.roll?.tier || 'conditional';
  const exec = meta.kind === 'command' ? EXEC_BY_TIER[tier] : '해당없음';
  const [a, b, c] = M.people;
  const topic = meta.kind === 'command' ? meta.command.slice(0, 18) : M.openingTopic;
  const opinionShift = { smooth: 3, conditional: 1, delayed: -1, backlash: -6, blocked: -3 }[tier];
  const span = meta.months ? `${meta.months}개월` : `${meta.days}일`;
  const hit = M.factions.find((f) => f.name === M.factionHit);
  return {
    conflict: null,
    opening: meta.kind === 'time' ? M.timeOpening(span) : M.opening(s.setup.country, topic),
    record: [
      { speaker: '', title: '', text: M.narrStart },
      { speaker: a[0], title: a[1], text: M.speeches[0] },
      { speaker: b[0], title: b[1], text: M.speeches[1] },
      { speaker: c[0], title: c[1], text: M.speeches[2] },
      { speaker: '', title: '', text: M.narrEnd },
    ],
    execution: { status: exec, note: meta.kind === 'command' ? `판정 ${tier}에 따른 점검용 결과` : '' },
    news: M.news,
    status: {
      ...st,
      [K.mood]: Math.max(0, Math.min(100, st[K.mood] + (meta.kind === 'command' ? opinionShift : 0))),
      [K.cost]: st[K.cost] - (meta.kind === 'command' ? M.cost : 0),
      [K.grow]:
        st[K.grow] + (meta.kind === 'time' ? Math.round(st[K.grow] * 0.004 * ((meta.months || 0) + (meta.days || 0) / 30)) : 0),
    },
    units: s.units ? undefined : M.units,
    eraLabel: M.eraLabel,
    updates: {
      characters: M.people.map(([name, title, faction, disposition]) => ({ name, title, faction, disposition, status: '재직' })),
      policies:
        meta.kind === 'command'
          ? [{ name: meta.command.slice(0, 16), status: exec === '보류' ? '보류' : exec === '반려' ? '좌초' : '시행', summary: '점검용 안건' }]
          : meta.kind === 'opening'
            ? [{ ...M.openingPolicy, status: '논의중' }]
            : [],
      factions: meta.kind === 'opening' ? M.factions : [{ name: hit.name, support: hit.support - 4 }],
      regions: [],
    },
    chronicle: meta.kind === 'command' ? `${meta.command.slice(0, 20)} — ${exec}` : M.narrEnd,
    suggestions: M.suggestions,
  };
}

function mockBackend() {
  return {
    id: 'mock',
    label: '점검용 가짜 기록자',
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
