import { useEffect, useMemo, useRef, useSyncExternalStore } from 'react';

import { ApiError, type Page } from '@/lib/api';
import { events } from '@/lib/events';
import { realtime, type Signal } from '@/lib/realtime';

/** 보내는 중이거나 보내지 못한 내 메시지 (서버에 아직 없다). clientId 는 다시 보내도 그대로라 서버가 한 번만 저장한다. */
export type LocalMessage = {
  localId: string;
  clientId: string;
  text: string;
  createdAt: string;
  status: 'sending' | 'failed';
  reason?: string;
};

type ServerMessage = { id: number; clientId?: string | null };

/** 대화 하나를 서버에서 읽고 쓰는 방법 */
export type ThreadSource<T extends ServerMessage> = {
  fetchPage: (cursor?: string | null) => Promise<Page<T>>;
  sendText: (text: string, clientId: string) => Promise<T>;
  /** 화면을 다시 그릴 만큼 바뀌지 않았으면 true */
  same: (a: T, b: T) => boolean;
};

type Snapshot<T> = {
  items: T[] | null;
  local: LocalMessage[];
  /** undefined: 아직 모름, null: 더 오래된 메시지 없음 */
  cursor: string | null | undefined;
};

// 실시간 연결이 끊겨 있을 때만 이 간격으로 확인한다. 연결돼 있어도 30초마다 한 번은 맞춰 본다.
const FALLBACK_POLL_MS = 5000;
const SAFETY_MS = 30_000;
// 기억해 두는 대화 수 (보내는 중인 메시지가 있거나 열려 있는 대화는 지우지 않는다)
const MAX_THREADS = 30;
let localSeq = 0;

const newClientId = () =>
  `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}${Math.random().toString(36).slice(2, 10)}`;

/**
 * 대화 하나의 상태. 화면 밖(모듈)에 있어서
 * - 방을 나갔다 들어와도 마지막으로 본 메시지가 바로 뜨고 (그 뒤 새로 받아 맞춘다),
 * - 보내는 중이던 메시지는 방을 나가도 끝까지 보내지고, 실패한 메시지도 남아 있다.
 * 바뀐 게 없으면 목록을 그대로 둬서 다시 그리지 않는다 (안 바뀐 메시지는 같은 객체를 유지한다).
 */
class Thread<T extends ServerMessage> {
  private snap: Snapshot<T> = { items: null, local: [], cursor: undefined };
  private listeners = new Set<() => void>();
  private inFlight: Promise<void> | null = null;
  private again = false;
  private queue: Promise<void> = Promise.resolve();
  private loadingMore = false;
  /** 화면이 열려 있을 때만: 새로 받은 최신 페이지로 할 일 (읽음 처리). 미리 받기에서는 읽음 처리하지 않는다. */
  private reader: ((page: T[]) => Promise<void> | void) | null = null;
  lastFetch = 0;

  constructor(public source: ThreadSource<T>) {}

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => void this.listeners.delete(listener);
  };
  getSnapshot = () => this.snap;
  get idle() {
    return this.listeners.size === 0 && this.snap.local.length === 0 && !this.inFlight;
  }

  private set(patch: Partial<Snapshot<T>>) {
    this.snap = { ...this.snap, ...patch };
    this.listeners.forEach((l) => l());
  }

  attach(reader: (page: T[]) => Promise<void> | void) {
    this.reader = reader;
    return () => {
      if (this.reader === reader) this.reader = null;
    };
  }

  /** 최신 페이지를 다시 받는다. 받는 중에 또 부르면 끝난 뒤 한 번만 더 받는다. */
  refresh = (): Promise<void> => {
    if (this.inFlight) {
      this.again = true;
      return this.inFlight;
    }
    this.inFlight = (async () => {
      try {
        do {
          this.again = false;
          const page = await this.source.fetchPage();
          this.lastFetch = Date.now();
          this.apply(page);
          if (!realtime.isConnected()) realtime.kick();
          await this.reader?.(page.items);
        } while (this.again);
      } finally {
        this.inFlight = null;
      }
    })();
    return this.inFlight;
  };

  /** 처음 불러오기가 실패해도 빈 목록으로 보여 준다 (다음 확인 때 채워진다) */
  showEmptyIfUnknown = () => {
    if (this.snap.items === null) this.set({ items: [] });
  };

  private apply(page: Page<T>) {
    const prev = this.snap.items;
    const newestKnown = prev?.[0]?.id ?? 0;
    const oldestInPage = page.items[page.items.length - 1]?.id ?? 0;
    // 오래 비운 사이에 한 페이지보다 많이 쌓였으면 이어 붙이면 가운데가 빈다: 새 페이지로 다시 시작한다.
    const gap = !!prev && prev.length > 0 && page.nextCursor !== null && oldestInPage > newestKnown;
    const items = gap ? page.items : mergeLatest(prev, page.items, this.source.same);
    const cursor = gap || this.snap.cursor === undefined ? page.nextCursor : this.snap.cursor;
    // 서버에 이미 들어간 내 메시지 (응답을 못 받았거나 아직 오는 중) 는 보내는 중 말풍선을 지운다
    // (지울 게 없으면 같은 배열을 둬서 다시 그리지 않는다)
    const arrived = new Set(page.items.map((m) => m.clientId).filter(Boolean));
    const local = this.snap.local.some((m) => arrived.has(m.clientId))
      ? this.snap.local.filter((m) => !arrived.has(m.clientId))
      : this.snap.local;
    if (items !== prev || cursor !== this.snap.cursor || local !== this.snap.local) this.set({ items, cursor, local });
  }

  loadOlder = async () => {
    const cursor = this.snap.cursor;
    if (!cursor || this.loadingMore) return;
    this.loadingMore = true;
    try {
      const page = await this.source.fetchPage(cursor);
      const prev = this.snap.items ?? [];
      const have = new Set(prev.map((m) => m.id));
      this.set({ items: [...prev, ...page.items.filter((m) => !have.has(m.id))], cursor: page.nextCursor });
    } catch {
      // 다음에 끝까지 내리면 다시 시도한다
    } finally {
      this.loadingMore = false;
    }
  };

  /** 바로 화면에 띄우고 순서대로 하나씩 보낸다 */
  send = (text: string) => {
    const msg: LocalMessage = {
      localId: `local-${++localSeq}`,
      clientId: newClientId(),
      text,
      createdAt: new Date().toISOString(),
      status: 'sending',
    };
    this.set({ local: [msg, ...this.snap.local] });
    this.deliver(msg);
  };

  /** 다시 보내는 메시지는 맨 아래(가장 최근)로 간다. 같은 clientId 라 먼저 것이 사실 도착했어도 하나만 남는다. */
  retry = (localId: string) => {
    const target = this.snap.local.find((m) => m.localId === localId);
    if (!target || target.status !== 'failed') return;
    const resend: LocalMessage = { ...target, status: 'sending', reason: undefined, createdAt: new Date().toISOString() };
    this.set({ local: [resend, ...this.snap.local.filter((m) => m.localId !== localId)] });
    this.deliver(resend);
  };

  discard = (localId: string) => {
    if (!this.snap.local.some((m) => m.localId === localId && m.status === 'failed')) return;
    this.set({ local: this.snap.local.filter((m) => m.localId !== localId) });
  };

  private deliver(msg: LocalMessage) {
    this.queue = this.queue.then(async () => {
      try {
        const saved = await this.source.sendText(msg.text, msg.clientId);
        this.set({
          local: this.snap.local.filter((m) => m.localId !== msg.localId),
          items: mergeLatest(this.snap.items, [saved], this.source.same),
        });
        events.emit('unread');
        // 대화 중이니 실시간 연결이 살아 있는지 바로 확인한다 (끊겨 있으면 바로 다시 잇는다)
        if (realtime.isConnected()) realtime.probe();
        else realtime.kick();
      } catch (e) {
        // 서버가 거절한 이유만 보여 준다 (예: 친구가 아니에요). 연결 문제·시간 초과는 짧게 「보내지 못했어요」.
        const reason = e instanceof ApiError && e.code !== 'NETWORK' ? e.message : undefined;
        this.set({ local: this.snap.local.map((m) => (m.localId === msg.localId ? { ...m, status: 'failed', reason } : m)) });
      }
    });
  }
}

const threads = new Map<string, Thread<ServerMessage>>();

function threadFor<T extends ServerMessage>(key: string, source: ThreadSource<T>): Thread<T> {
  let thread = threads.get(key) as Thread<T> | undefined;
  if (thread) {
    // 최근에 쓴 것을 뒤로 (가장 앞이 가장 오래 안 쓴 것)
    threads.delete(key);
  } else {
    thread = new Thread(source);
  }
  threads.set(key, thread as unknown as Thread<ServerMessage>);
  if (threads.size > MAX_THREADS) {
    for (const [k, t] of threads) {
      if (threads.size <= MAX_THREADS) break;
      if (t.idle) threads.delete(k);
    }
  }
  return thread;
}

/** 목록에서 누르는 순간 미리 받아 둔다 (화면이 뜰 때 이미 와 있도록). 읽음 처리는 하지 않는다. */
export function prefetchThread<T extends ServerMessage>(key: string, source: ThreadSource<T>) {
  const thread = threadFor(key, source);
  if (thread.getSnapshot().items === null || Date.now() - thread.lastFetch > FALLBACK_POLL_MS) {
    thread.refresh().catch(() => {});
  }
}

/** 로그아웃하면 다른 사람 대화가 남지 않게 모두 지운다 */
export function resetChatThreads() {
  threads.clear();
}

/**
 * 1:1 · 단챗 화면이 쓰는 훅. key 로 대화를 구분한다 (dm:12, group:7).
 * - 열려 있는 동안: 실시간 신호가 오면 바로, 연결이 끊겨 있으면 5초마다, 앱이 앞으로 나오면 바로 다시 받는다.
 * - afterFetch: 새로 받은 최신 페이지로 할 일 (읽음 처리 등)
 */
export function useChatThread<T extends ServerMessage>({
  key,
  source,
  afterFetch,
  matches,
}: {
  key: string;
  source: ThreadSource<T>;
  afterFetch: (page: T[]) => Promise<void> | void;
  /** 이 대화의 실시간 신호인지 */
  matches: (signal: Signal) => boolean;
}) {
  // source 는 key 가 같으면 같은 일을 하므로 처음 것을 쓴다
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const thread = useMemo(() => threadFor(key, source), [key]);
  const snap = useSyncExternalStore(thread.subscribe, thread.getSnapshot);
  const latest = useRef({ afterFetch, matches });
  useEffect(() => {
    latest.current = { afterFetch, matches };
  });

  useEffect(() => {
    const detach = thread.attach((page) => latest.current.afterFetch(page));
    const refresh = () => void thread.refresh().catch(() => {});
    thread.refresh().catch(thread.showEmptyIfUnknown);
    const timer = setInterval(() => {
      if (!realtime.isConnected() || Date.now() - thread.lastFetch > SAFETY_MS) refresh();
    }, FALLBACK_POLL_MS);
    const offSignal = realtime.on((signal) => {
      if (latest.current.matches(signal)) refresh();
    });
    // 'messages': 앱이 앞으로 나올 때(_layout), 실시간 연결이 다시 붙을 때, 채팅 알림이 왔을 때.
    // 다른 앱에 갔다 오면 실시간 연결을 기다리지 않고 바로 다시 받는다.
    const offEvents = events.on('messages', refresh);
    return () => {
      detach();
      clearInterval(timer);
      offSignal();
      offEvents();
    };
  }, [thread]);

  // 화면에 그릴 목록 (뒤집힌 목록이라 0번이 맨 아래 = 가장 최근)
  const rows = useMemo<(T | LocalMessage)[] | null>(
    () => (snap.items ? [...snap.local, ...snap.items] : null),
    [snap.items, snap.local],
  );

  return { rows, items: snap.items, loadOlder: thread.loadOlder, send: thread.send, retry: thread.retry, discard: thread.discard };
}

/** 받은 최신 페이지를 합친다. 바뀐 게 없으면 prev 를 그대로 돌려준다 (다시 그리지 않는다). */
export function mergeLatest<T extends { id: number }>(prev: T[] | null, page: T[], same: (a: T, b: T) => boolean): T[] {
  if (!prev) return page;
  const byId = new Map(prev.map((m) => [m.id, m]));
  let changed = false;
  for (const m of page) {
    const old = byId.get(m.id);
    if (!old || !same(old, m)) {
      byId.set(m.id, m);
      changed = true;
    }
  }
  return changed ? [...byId.values()].sort((a, b) => b.id - a.id) : prev;
}

export const isLocal = (m: object): m is LocalMessage => 'localId' in m;
