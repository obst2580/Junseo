// 국가의 시대·기업의 시대 — 로컬 서버.
// dist/의 게임 화면(/, /business)을 내주고, /api/turn에서 Anthropic SDK로 Claude를 불러 기록을 스트리밍한다.
// API 키는 서버에만 있고 브라우저로 나가지 않는다.
//
//   ANTHROPIC_API_KEY=sk-ant-... npm start
//   GUKGA_MODEL (기본 claude-opus-5-5), PORT (기본 8787)
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import Anthropic from '@anthropic-ai/sdk';

const root = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT) || 8787;
const HOST = process.env.HOST || '127.0.0.1';
const MODEL = process.env.GUKGA_MODEL || 'claude-opus-5-5';
const MAX_BODY = 512 * 1024;

// 주소 → 게임 화면 (국가의 시대는 /, 기업의 시대는 /business)
const PAGES = {
  '/': 'index.html',
  '/index.html': 'index.html',
  '/business': 'business.html',
  '/business.html': 'business.html',
};

// 화면의 '사관의 깊이' → 생각의 깊이(effort)
const EFFORT = { quick: 'low', default: 'medium', complex: 'high' };

// 거절 시 서버 쪽 대체 모델(fallbacks: "default")을 받는 모델
const FALLBACK_MODELS = new Set(['claude-opus-5-5', 'claude-opus-5', 'claude-sonnet-5-5', 'claude-fable-5-1']);

let client = null;
let clientError = null;
try {
  client = new Anthropic();
} catch (e) {
  clientError = e;
}

function sendJson(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
  res.end(JSON.stringify(body));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY) {
        reject(Object.assign(new Error('요청이 너무 큽니다.'), { code: 'prompt_too_large' }));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

function errorCode(e) {
  if (e instanceof Anthropic.AuthenticationError || e instanceof Anthropic.PermissionDeniedError) return 'auth';
  if (e instanceof Anthropic.RateLimitError) return 'rate_limited';
  if (e instanceof Anthropic.BadRequestError) return 'upstream_error';
  if (e instanceof Anthropic.APIError) return 'upstream_error';
  return 'upstream_error';
}

async function handleTurn(req, res) {
  if (!client) {
    sendJson(res, 503, { code: 'auth', message: String(clientError?.message || 'API 키가 없습니다.') });
    return;
  }
  let input;
  try {
    input = JSON.parse(await readBody(req));
  } catch (e) {
    sendJson(res, 400, { code: e.code || 'invalid_request', message: '요청 본문을 읽지 못했습니다.' });
    return;
  }
  const prompt = typeof input?.prompt === 'string' ? input.prompt : '';
  if (!prompt.trim()) {
    sendJson(res, 400, { code: 'invalid_request', message: 'prompt가 비어 있습니다.' });
    return;
  }
  const effort = EFFORT[input.tier] || EFFORT.default;

  const abort = new AbortController();
  res.on('close', () => {
    if (!res.writableFinished) abort.abort();
  });
  res.writeHead(200, { 'content-type': 'application/x-ndjson; charset=utf-8', 'cache-control': 'no-store' });
  const send = (obj) => res.write(`${JSON.stringify(obj)}\n`);

  const params = {
    model: MODEL,
    max_tokens: 16000,
    thinking: { type: 'adaptive' },
    output_config: { effort },
    messages: [{ role: 'user', content: prompt }],
  };
  try {
    const stream = FALLBACK_MODELS.has(MODEL)
      ? client.beta.messages.stream(
          { ...params, betas: ['server-side-fallback-2026-07-01'], fallbacks: 'default' },
          { signal: abort.signal }
        )
      : client.messages.stream(params, { signal: abort.signal });

    // 대체 모델로 넘어가도 이미 받은 글은 무효가 되지 않고 이어 쓰이므로 순서대로 이어 붙인다
    let text = '';
    for await (const event of stream) {
      if (event.type === 'content_block_delta' && event.delta.type === 'text_delta') {
        text += event.delta.text;
        send({ t: 'delta', d: event.delta.text });
      }
    }
    const final = await stream.finalMessage();
    if (final.stop_reason === 'refusal') {
      send({ t: 'error', code: 'refused', message: final.stop_details?.explanation || '' });
    } else if (final.stop_reason === 'max_tokens') {
      send({ t: 'error', code: 'truncated' });
    } else {
      send({ t: 'done', text });
    }
  } catch (e) {
    if (abort.signal.aborted) return res.end();
    console.error('[api/turn]', e?.status || '', e?.message || e);
    send({ t: 'error', code: errorCode(e), message: e?.message || '' });
  }
  res.end();
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  try {
    if (req.method === 'GET' && url.pathname === '/api/health') {
      sendJson(res, 200, client ? { service: 'gukga', ok: true, model: MODEL } : { service: 'gukga', ok: false, model: MODEL, reason: 'no_api_key' });
      return;
    }
    if (req.method === 'POST' && url.pathname === '/api/turn') {
      await handleTurn(req, res);
      return;
    }
    const page = PAGES[url.pathname];
    if (req.method === 'GET' && page) {
      try {
        const html = await readFile(path.join(root, 'dist', page));
        res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-cache' });
        res.end(html);
      } catch {
        res.writeHead(500, { 'content-type': 'text/plain; charset=utf-8' });
        res.end(`dist/${page}이 없습니다. 먼저 npm run build를 실행하십시오.`);
      }
      return;
    }
    res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
    res.end('Not found');
  } catch (e) {
    console.error(e);
    if (!res.headersSent) sendJson(res, 500, { code: 'upstream_error', message: '서버 오류' });
    else res.end();
  }
});

server.listen(PORT, HOST, () => {
  console.log(`국가의 시대: http://${HOST}:${PORT}/`);
  console.log(`기업의 시대: http://${HOST}:${PORT}/business`);
  console.log(`모델 ${MODEL}${client ? '' : ' (API 키 없음)'}`);
});
