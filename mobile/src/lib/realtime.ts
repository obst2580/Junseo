import { AppState, type NativeEventSubscription } from 'react-native';

import { currentToken } from '@/lib/api';
import { API_BASE_URL } from '@/lib/config';
import { events } from '@/lib/events';

/**
 * 서버와 열어 두는 실시간 연결 (/ws). 메시지가 오거나 읽히면 서버가 「바뀌었다」는 신호만 보내고,
 * 열려 있는 채팅 화면이 그 대화만 바로 다시 불러온다. 내용은 늘 REST 로 가져온다.
 * 앱이 뒤로 가면 끊고, 앞으로 나오면 다시 잇는다. 끊겨 있는 동안 채팅 화면은 5초마다 확인한다.
 *
 * 와이파이 ↔ LTE 전환처럼 연결이 소리 없이 죽는 경우: ping 에 답(pong)이 없으면 끊고 다시 잇는다.
 * 가만히 있을 때는 15초마다, 메시지를 보낸 직후에는 바로 확인한다 (대화 중이면 몇 초 안에 알아챈다).
 */
export type Signal =
  | { type: 'message' | 'read'; peerId: number }
  | { type: 'group-message' | 'group-read'; groupId: number };

const WS_URL = `${API_BASE_URL.replace(/^http/, 'ws')}/ws`;
const PING_MS = 15_000;
/** ping 을 보내고 이 안에 아무 응답이 없으면 죽은 연결로 본다 */
const PONG_TIMEOUT_MS = 6_000;
/** 연결하고 이 안에 인증(ready)이 안 끝나면 다시 시도한다 */
const READY_TIMEOUT_MS = 10_000;
const MAX_RETRY_MS = 30_000;

let socket: WebSocket | null = null;
let wanted = false;
let connected = false;
let retryMs = 1000;
let retryTimer: ReturnType<typeof setTimeout> | null = null;
let pingTimer: ReturnType<typeof setInterval> | null = null;
let deadline: ReturnType<typeof setTimeout> | null = null;
let appState: NativeEventSubscription | null = null;
const listeners = new Set<(signal: Signal) => void>();

function clearTimers() {
  if (pingTimer) clearInterval(pingTimer);
  if (deadline) clearTimeout(deadline);
  pingTimer = null;
  deadline = null;
}

function open() {
  const token = currentToken();
  if (!wanted || socket || !token) return;
  const ws = new WebSocket(WS_URL);
  socket = ws;
  deadline = setTimeout(() => drop(ws), READY_TIMEOUT_MS);
  ws.onopen = () => ws.send(JSON.stringify({ type: 'auth', token }));
  ws.onmessage = (e) => {
    if (socket !== ws) return;
    // 무엇이든 받았으면 살아 있다
    if (deadline) clearTimeout(deadline);
    deadline = null;
    let data: { type?: string };
    try {
      data = JSON.parse(String(e.data));
    } catch {
      return;
    }
    if (data.type === 'pong') return;
    if (data.type === 'ready') {
      connected = true;
      retryMs = 1000;
      if (pingTimer) clearInterval(pingTimer);
      pingTimer = setInterval(() => ping(ws), PING_MS);
      // 끊겨 있던 동안 바뀐 것을 따라잡는다
      events.emit('messages');
      return;
    }
    listeners.forEach((l) => l(data as Signal));
    events.emit('unread');
  };
  ws.onerror = () => drop(ws);
  ws.onclose = () => drop(ws);
}

/** 답이 없으면 죽은 연결로 보고 다시 잇는다 */
function ping(ws: WebSocket) {
  if (socket !== ws) return;
  if (ws.readyState !== WebSocket.OPEN) return drop(ws);
  ws.send('{"type":"ping"}');
  deadline ??= setTimeout(() => drop(ws), PONG_TIMEOUT_MS);
}

/** 이 연결을 버리고 (앱이 앞에 있으면) 잠시 뒤 다시 잇는다. 여러 번 불려도 한 번만 처리한다. */
function drop(ws: WebSocket) {
  if (socket !== ws) return;
  socket = null;
  connected = false;
  clearTimers();
  try {
    ws.close();
  } catch {
    // 이미 닫혔다
  }
  if (!wanted || AppState.currentState !== 'active') return;
  if (retryTimer) clearTimeout(retryTimer);
  retryTimer = setTimeout(() => {
    retryTimer = null;
    open();
  }, retryMs);
  retryMs = Math.min(retryMs * 2, MAX_RETRY_MS);
}

function close() {
  if (retryTimer) clearTimeout(retryTimer);
  retryTimer = null;
  clearTimers();
  const ws = socket;
  socket = null;
  connected = false;
  ws?.close();
}

export const realtime = {
  start() {
    wanted = true;
    appState ??= AppState.addEventListener('change', (state) => (state === 'active' ? realtime.kick() : close()));
    open();
  },
  stop() {
    wanted = false;
    appState?.remove();
    appState = null;
    close();
  },
  isConnected: () => connected,
  /** 대화하는 중 (방금 보냄): 다음 ping 을 기다리지 않고 지금 살아 있는지 확인한다 */
  probe() {
    if (socket && connected) ping(socket);
  },
  /** 서버에 닿는다는 걸 알았을 때 (앱이 앞으로 나옴, 요청 성공): 기다리지 말고 바로 다시 잇는다 */
  kick() {
    if (!wanted || socket) return;
    retryMs = 1000;
    if (retryTimer) clearTimeout(retryTimer);
    retryTimer = null;
    open();
  },
  on(listener: (signal: Signal) => void) {
    listeners.add(listener);
    return () => void listeners.delete(listener);
  },
};
