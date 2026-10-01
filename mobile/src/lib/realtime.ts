import { AppState, type NativeEventSubscription } from 'react-native';

import { currentToken } from '@/lib/api';
import { API_BASE_URL } from '@/lib/config';
import { events } from '@/lib/events';

/**
 * 서버와 열어 두는 실시간 연결 (/ws). 메시지가 오거나 읽히면 서버가 「바뀌었다」는 신호만 보내고,
 * 열려 있는 채팅 화면이 그 대화만 바로 다시 불러온다. 내용은 늘 REST 로 가져온다.
 * 앱이 뒤로 가면 끊고, 앞으로 나오면 다시 잇는다. 끊겨 있는 동안 채팅 화면은 5초마다 확인한다.
 */
export type Signal =
  | { type: 'message' | 'read'; peerId: number }
  | { type: 'group-message' | 'group-read'; groupId: number };

const WS_URL = `${API_BASE_URL.replace(/^http/, 'ws')}/ws`;
const PING_MS = 25_000;
const MAX_RETRY_MS = 30_000;

let socket: WebSocket | null = null;
let wanted = false;
let connected = false;
let retryMs = 1000;
let retryTimer: ReturnType<typeof setTimeout> | null = null;
let pingTimer: ReturnType<typeof setInterval> | null = null;
let appState: NativeEventSubscription | null = null;
const listeners = new Set<(signal: Signal) => void>();

function open() {
  const token = currentToken();
  if (!wanted || socket || !token) return;
  const ws = new WebSocket(WS_URL);
  socket = ws;
  ws.onopen = () => ws.send(JSON.stringify({ type: 'auth', token }));
  ws.onmessage = (e) => {
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
      pingTimer = setInterval(() => ws.readyState === WebSocket.OPEN && ws.send('{"type":"ping"}'), PING_MS);
      // 끊겨 있던 동안 바뀐 것을 따라잡는다
      events.emit('messages');
      return;
    }
    listeners.forEach((l) => l(data as Signal));
    events.emit('unread');
  };
  ws.onerror = () => ws.close();
  ws.onclose = () => {
    if (socket === ws) socket = null;
    connected = false;
    if (pingTimer) clearInterval(pingTimer);
    pingTimer = null;
    if (wanted && AppState.currentState === 'active') {
      if (retryTimer) clearTimeout(retryTimer);
      retryTimer = setTimeout(open, retryMs);
      retryMs = Math.min(retryMs * 2, MAX_RETRY_MS);
    }
  };
}

function close() {
  if (retryTimer) clearTimeout(retryTimer);
  retryTimer = null;
  const ws = socket;
  socket = null;
  connected = false;
  ws?.close();
}

export const realtime = {
  start() {
    wanted = true;
    appState ??= AppState.addEventListener('change', (state) => (state === 'active' ? open() : close()));
    open();
  },
  stop() {
    wanted = false;
    appState?.remove();
    appState = null;
    close();
  },
  isConnected: () => connected,
  on(listener: (signal: Signal) => void) {
    listeners.add(listener);
    return () => void listeners.delete(listener);
  },
};
