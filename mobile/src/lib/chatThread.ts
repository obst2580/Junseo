import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { ApiError, type Page } from '@/lib/api';
import { events } from '@/lib/events';
import { realtime, type Signal } from '@/lib/realtime';

/** 보내는 중이거나 보내지 못한 내 메시지 (서버에 아직 없다) */
export type LocalMessage = { localId: string; text: string; createdAt: string; status: 'sending' | 'failed'; reason?: string };

// 실시간 연결이 끊겨 있을 때만 이 간격으로 확인한다. 연결돼 있어도 30초마다 한 번은 맞춰 본다.
const FALLBACK_POLL_MS = 5000;
const SAFETY_MS = 30_000;
let localSeq = 0;

/**
 * 1:1 · 단챗 공통: 메시지 불러오기·합치기·보내기.
 * - 바뀐 게 없으면 목록을 그대로 둬서 다시 그리지 않는다 (안 바뀐 메시지는 같은 객체를 유지한다).
 * - 보낸 메시지는 바로 뜨고, 순서대로 하나씩 보낸다. 실패하면 다시 보낼 수 있다.
 * - 불러오는 중에 또 부르면 끝난 뒤 한 번만 더 부른다.
 */
export function useChatThread<T extends { id: number }>({
  fetchPage,
  sendText,
  afterFetch,
  same,
  matches,
}: {
  fetchPage: (cursor?: string | null) => Promise<Page<T>>;
  sendText: (text: string) => Promise<T>;
  /** 새로 받은 최신 페이지로 할 일 (읽음 처리 등) */
  afterFetch: (page: T[]) => Promise<void> | void;
  /** 화면을 다시 그릴 만큼 바뀌지 않았으면 true */
  same: (a: T, b: T) => boolean;
  /** 이 대화의 실시간 신호인지 */
  matches: (signal: Signal) => boolean;
}) {
  const [items, setItems] = useState<T[] | null>(null);
  const [local, setLocal] = useState<LocalMessage[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const latest = useRef({ fetchPage, sendText, afterFetch, same, matches });
  useEffect(() => {
    latest.current = { fetchPage, sendText, afterFetch, same, matches };
  });
  const inFlight = useRef(false);
  const again = useRef(false);
  const lastFetch = useRef(0);
  const queue = useRef<Promise<void>>(Promise.resolve());
  const loadingMore = useRef(false);
  const localRef = useRef<LocalMessage[]>([]);
  useEffect(() => {
    localRef.current = local;
  }, [local]);

  const refresh = useCallback(async () => {
    if (inFlight.current) {
      again.current = true;
      return;
    }
    inFlight.current = true;
    try {
      do {
        again.current = false;
        const page = await latest.current.fetchPage();
        lastFetch.current = Date.now();
        setItems((prev) => mergeLatest(prev, page.items, latest.current.same));
        setCursor((c) => c ?? page.nextCursor);
        await latest.current.afterFetch(page.items);
      } while (again.current);
    } finally {
      inFlight.current = false;
    }
  }, []);

  useEffect(() => {
    refresh().catch(() => setItems((prev) => prev ?? []));
    const timer = setInterval(() => {
      if (!realtime.isConnected() || Date.now() - lastFetch.current > SAFETY_MS) refresh().catch(() => {});
    }, FALLBACK_POLL_MS);
    const offSignal = realtime.on((signal) => {
      if (latest.current.matches(signal)) refresh().catch(() => {});
    });
    const offEvents = events.on('messages', () => void refresh().catch(() => {}));
    return () => {
      clearInterval(timer);
      offSignal();
      offEvents();
    };
  }, [refresh]);

  const loadOlder = useCallback(async () => {
    if (!cursor || loadingMore.current) return;
    loadingMore.current = true;
    try {
      const page = await latest.current.fetchPage(cursor);
      setItems((prev) => {
        const have = new Set((prev ?? []).map((m) => m.id));
        return [...(prev ?? []), ...page.items.filter((m) => !have.has(m.id))];
      });
      setCursor(page.nextCursor);
    } finally {
      loadingMore.current = false;
    }
  }, [cursor]);

  const deliver = useCallback((msg: LocalMessage) => {
    queue.current = queue.current.then(async () => {
      try {
        const saved = await latest.current.sendText(msg.text);
        setLocal((list) => list.filter((m) => m.localId !== msg.localId));
        setItems((prev) => (prev?.some((m) => m.id === saved.id) ? prev : [saved, ...(prev ?? [])]));
        events.emit('unread');
      } catch (e) {
        // 서버가 거절한 이유만 보여 준다 (예: 친구가 아니에요). 연결 문제는 짧게 「보내지 못했어요」.
        const reason = e instanceof ApiError && e.code !== 'NETWORK' ? e.message : undefined;
        setLocal((list) => list.map((m) => (m.localId === msg.localId ? { ...m, status: 'failed', reason } : m)));
      }
    });
  }, []);

  /** 바로 화면에 띄우고 순서대로 보낸다 */
  const send = useCallback(
    (text: string) => {
      const msg: LocalMessage = { localId: `local-${++localSeq}`, text, createdAt: new Date().toISOString(), status: 'sending' };
      setLocal((list) => [msg, ...list]);
      deliver(msg);
    },
    [deliver],
  );

  const retry = useCallback(
    (localId: string) => {
      const target = localRef.current.find((m) => m.localId === localId);
      if (!target || target.status !== 'failed') return;
      const resend: LocalMessage = { ...target, status: 'sending', reason: undefined, createdAt: new Date().toISOString() };
      localRef.current = [resend, ...localRef.current.filter((m) => m.localId !== localId)];
      // 다시 보내는 메시지는 맨 아래(가장 최근)로 간다
      setLocal(localRef.current);
      deliver(resend);
    },
    [deliver],
  );

  // 화면에 그릴 목록 (뒤집힌 목록이라 0번이 맨 아래 = 가장 최근)
  const rows = useMemo<(T | LocalMessage)[] | null>(() => (items ? [...local, ...items] : null), [items, local]);

  return { rows, items, refresh, loadOlder, send, retry };
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
