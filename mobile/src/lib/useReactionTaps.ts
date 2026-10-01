import { useCallback, useEffect, useRef, useState } from 'react';

import { api } from './api';

// 마지막으로 누르고 이만큼 쉬면 모아 둔 걸 보낸다.
const FLUSH_MS = 600;
// 서버가 한 번에 받는 최대 횟수 (ReactionEmojis.MAX_TAPS_PER_REQUEST)
const MAX_PER_REQUEST = 20;

type Counts = Record<string, number>;

/**
 * 이모지를 빠르게 여러 번 누르면(꾹 눌러 한 번에 여러 개를 보내도) 화면에는 바로 반영하고, 서버에는 모아서 한 번에 보낸다.
 * `unsent` 는 아직 서버 응답에 반영되지 않은 횟수다. 화면은 서버 값에 이걸 더해서 그린다.
 */
export function useReactionTaps(momentId: number, onSent: () => Promise<void>, onError: (e: unknown) => void) {
  const pending = useRef<Counts>({});
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [unsent, setUnsent] = useState<Counts>({});

  const flush = useCallback(async () => {
    timer.current = null;
    const batch = pending.current;
    pending.current = {};
    if (!Object.keys(batch).length) return;
    try {
      for (const [emoji, total] of Object.entries(batch)) {
        for (let left = total; left > 0; left -= MAX_PER_REQUEST) {
          await api.react(momentId, emoji, Math.min(left, MAX_PER_REQUEST));
        }
      }
      await onSent();
    } catch (e) {
      onError(e);
    }
    setUnsent((current) => {
      const next = { ...current };
      for (const [emoji, count] of Object.entries(batch)) {
        next[emoji] = (next[emoji] ?? 0) - count;
        if (next[emoji] <= 0) delete next[emoji];
      }
      return next;
    });
  }, [momentId, onSent, onError]);

  const tap = useCallback(
    (emoji: string, count = 1) => {
      pending.current[emoji] = (pending.current[emoji] ?? 0) + count;
      setUnsent((current) => ({ ...current, [emoji]: (current[emoji] ?? 0) + count }));
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => void flush(), FLUSH_MS);
    },
    [flush],
  );

  // 보내기 전에 화면을 떠나도 누른 건 보낸다.
  useEffect(() => {
    const pendingRef = pending;
    const timerRef = timer;
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
      const batch = pendingRef.current;
      pendingRef.current = {};
      for (const [emoji, total] of Object.entries(batch)) {
        for (let left = total; left > 0; left -= MAX_PER_REQUEST) {
          api.react(momentId, emoji, Math.min(left, MAX_PER_REQUEST)).catch(() => {});
        }
      }
    };
  }, [momentId]);

  return { unsent, tap };
}
