import { Image } from 'expo-image';
import { useEffect, useSyncExternalStore } from 'react';
import { Platform } from 'react-native';

import { api, type RemoteTemplate } from '@/lib/api';
import { absoluteUrl } from '@/lib/config';
import { BUILT_IN_TEMPLATES, type Template, type TemplateSlot } from '@/lib/templates';

/**
 * 템플릿 목록 = 서버 템플릿(새로 올린 것) + 앱에 든 기본 템플릿.
 * 서버 목록은 기기에 저장해 두고 다음에 켤 때 바로 보여준 뒤 새로 받아 온다 (인터넷이 없어도 받아 둔 건 보인다).
 * 서버에 기본 템플릿과 같은 id 가 있으면 서버 것을 쓴다 (그림을 고쳐 다시 올릴 수 있다).
 */

/** 이 앱이 그릴 줄 아는 것. 서버 템플릿의 requires 에 모르는 게 있으면 (더 새 앱이 필요하면) 목록에서 뺀다. */
export const SUPPORTED_FEATURES: ReadonlySet<string> = new Set(['quad', 'glow', 'overlay']);

const CACHE_FILE = 'templates.json';
const REFRESH_MS = 60_000;

let remote: RemoteTemplate[] | null = null;
let snapshot: Template[] = BUILT_IN_TEMPLATES;
/** 이번 실행에서 서버에 한 번 물어봤는지 (받았든 실패했든) — 템플릿을 못 찾았을 때 「없음」을 띄워도 되는지.
 * 저장본만으로는 정하지 않는다: 저장본에 없는 새 템플릿을 링크로 열면 서버 답을 기다린다. */
let settled = false;
let restored: Promise<void> | null = null;
let inFlight: Promise<void> | null = null;
let fetchedAt = 0;
const listeners = new Set<() => void>();

function toTemplate(r: RemoteTemplate): Template | null {
  if (r.requires.some((f) => !SUPPORTED_FEATURES.has(f))) return null;
  return {
    id: r.id,
    name: r.name,
    width: r.width,
    height: r.height,
    backgroundColor: r.backgroundColor,
    background: { uri: absoluteUrl(r.backgroundUrl) },
    overlay: r.overlayUrl ? { uri: absoluteUrl(r.overlayUrl) } : undefined,
    slots: r.slots as TemplateSlot[],
  };
}

function publish(list: RemoteTemplate[] | null, done: boolean) {
  if (list) remote = list;
  settled = settled || done;
  const fromServer = (remote ?? []).map(toTemplate).filter((t): t is Template => t !== null);
  const ids = new Set(fromServer.map((t) => t.id));
  snapshot = [...fromServer, ...BUILT_IN_TEMPLATES.filter((t) => !ids.has(t.id))];
  listeners.forEach((l) => l());
}

async function readCache(): Promise<RemoteTemplate[] | null> {
  try {
    if (Platform.OS === 'web') {
      const raw = globalThis.localStorage?.getItem(`junseo.${CACHE_FILE}`);
      return raw ? JSON.parse(raw) : null;
    }
    const { File, Paths } = await import('expo-file-system');
    const file = new File(Paths.document, CACHE_FILE);
    return file.exists ? JSON.parse(await file.text()) : null;
  } catch {
    return null;
  }
}

async function writeCache(list: RemoteTemplate[]) {
  try {
    const raw = JSON.stringify(list);
    if (Platform.OS === 'web') {
      globalThis.localStorage?.setItem(`junseo.${CACHE_FILE}`, raw);
      return;
    }
    const { File, Paths } = await import('expo-file-system');
    new File(Paths.document, CACHE_FILE).write(raw);
  } catch {
    // 저장 못 해도 이번 실행 동안은 받아 온 목록을 쓴다
  }
}

/** 서버 목록을 새로 받는다 (1분 안에 다시 부르면 건너뛴다). 그림도 미리 받아 둬서 열자마자 보이고 저장도 된다. */
export function refreshTemplates(): Promise<void> {
  restored ??= readCache().then((cached) => {
    if (cached && !remote) publish(cached, false);
  });
  if (inFlight) return inFlight;
  if (Date.now() - fetchedAt < REFRESH_MS) return restored;
  inFlight = restored
    .then(() => api.templates())
    .then(({ items }) => {
      fetchedAt = Date.now();
      publish(items, true);
      writeCache(items);
      const urls = items.flatMap((t) => [t.backgroundUrl, t.overlayUrl]).filter((u): u is string => !!u).map(absoluteUrl);
      if (urls.length) Image.prefetch(urls).catch(() => {});
    })
    .catch(() => publish(null, true))
    .finally(() => {
      inFlight = null;
    });
  return inFlight;
}

const subscribe = (l: () => void) => {
  listeners.add(l);
  return () => {
    listeners.delete(l);
  };
};

/** 고를 수 있는 템플릿 전부 (화면에 들어올 때마다 서버 목록을 새로 받는다) */
export function useTemplates(): Template[] {
  useEffect(() => {
    refreshTemplates();
  }, []);
  return useSyncExternalStore(subscribe, () => snapshot);
}

/** 템플릿 하나. 아직 서버 목록을 못 받았으면 ready=false (알림 · 링크로 바로 들어온 경우) */
export function useTemplate(id: string): { template: Template | null; ready: boolean } {
  const list = useTemplates();
  const ready = useSyncExternalStore(subscribe, () => settled);
  const template = list.find((t) => t.id === id) ?? null;
  return { template, ready: ready || !!template };
}
