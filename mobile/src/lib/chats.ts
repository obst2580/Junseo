import { useSyncExternalStore } from 'react';

import { api, type Conversation, type GroupChat, type GroupConversation, type GroupMessage, type Message, type UserSummary } from '@/lib/api';
import { prefetchThread, resetChatThreads, type ThreadSource } from '@/lib/chatThread';
import { events } from '@/lib/events';

/** 1:1 · 단챗을 구분하는 이름 (대화 저장소·알림에서 같이 쓴다) */
export const dmKey = (peerId: number) => `dm:${peerId}`;
export const groupKey = (groupId: number) => `group:${groupId}`;

// 읽음(readAt)이나 글이 바뀌었을 때만 다시 그린다
const sameMessage = (a: Message, b: Message) => a.readAt === b.readAt && a.text === b.text && a.moment?.id === b.moment?.id;
// 안 읽은 사람 수나 글이 바뀌었을 때만 다시 그린다
const sameGroupMessage = (a: GroupMessage, b: GroupMessage) => a.unreadCount === b.unreadCount && a.text === b.text;

export const dmSource = (peerId: number): ThreadSource<Message> => ({
  fetchPage: (cursor) => api.messages(peerId, cursor),
  sendText: (text, clientId) => api.sendMessage(peerId, text, clientId),
  same: sameMessage,
});

export const groupSource = (groupId: number): ThreadSource<GroupMessage> => ({
  fetchPage: (cursor) => api.groupMessages(groupId, cursor),
  sendText: (text, clientId) => api.sendGroupMessage(groupId, text, clientId),
  same: sameGroupMessage,
});

// 목록에서 이미 알고 있는 이름·멤버. 방에 들어가자마자 제목과 얼굴을 띄운다.
const peers = new Map<number, UserSummary>();
const groups = new Map<number, GroupChat>();

export const knownPeer = (peerId: number) => peers.get(peerId) ?? null;
export const knownGroup = (groupId: number) => groups.get(groupId) ?? null;
export const rememberPeer = (peer: UserSummary) => void peers.set(peer.id, peer);
export const rememberGroup = (group: GroupChat) => void groups.set(group.id, group);

/** 목록에서 누르는 순간: 이름을 기억하고 메시지를 미리 받기 시작한다 */
export function prefetchDm(peer: UserSummary) {
  rememberPeer(peer);
  prefetchThread(dmKey(peer.id), dmSource(peer.id));
}

export function prefetchGroup(group: GroupChat) {
  rememberGroup(group);
  prefetchThread(groupKey(group.id), groupSource(group.id));
}

/**
 * 대화 목록 (챗 탭 목록과 탭 배지가 같이 쓴다). 메시지 하나가 오면 「새 메시지」·「읽음」 신호가 연달아 오는데,
 * 잠깐 모아서 한 번만 받는다 — 목록과 배지가 따로 두 번씩 받던 것을 한 번으로.
 */
type Conversations = { items: Conversation[]; groups: GroupConversation[] };
const BURST_MS = 300;
const BURST_MAX_MS = 1000;
let conversations: Conversations | null = null;
const conversationListeners = new Set<() => void>();
let offEvents: (() => void) | null = null;
let loading = false;
let loadAgain = false;
let soonTimer: ReturnType<typeof setTimeout> | null = null;
let burstStart = 0;
// 로그아웃하면 올라간다: 그 전에 보낸 요청의 응답은 버린다
let generation = 0;

function loadConversations() {
  if (loading) {
    loadAgain = true;
    return;
  }
  loading = true;
  const gen = generation;
  api
    .conversations()
    .then((r) => {
      if (gen !== generation) return;
      // 방에 들어가자마자(알림으로 들어와도) 이름·멤버를 바로 띄우도록 기억해 둔다
      r.items.forEach((c) => rememberPeer(c.peer));
      r.groups.forEach((g) => rememberGroup(g.group));
      conversations = r;
    })
    .catch(() => {
      if (gen === generation) conversations ??= { items: [], groups: [] };
    })
    .finally(() => {
      loading = false;
      conversationListeners.forEach((l) => l());
      if (loadAgain) {
        loadAgain = false;
        loadConversations();
      }
    });
}

/** soon: 신호가 몰려올 때 — 조용해지면(최대 1초 안에) 한 번만 받는다 */
export function refreshConversations({ soon = false } = {}) {
  // 아직 한 번도 못 받았으면 기다리지 않는다
  if (!soon || conversations === null) {
    if (soonTimer) clearTimeout(soonTimer);
    soonTimer = null;
    return loadConversations();
  }
  const now = Date.now();
  if (!soonTimer) burstStart = now;
  else clearTimeout(soonTimer);
  soonTimer = setTimeout(
    () => {
      soonTimer = null;
      loadConversations();
    },
    Math.max(0, Math.min(BURST_MS, burstStart + BURST_MAX_MS - now)),
  );
}

function subscribeConversations(listener: () => void) {
  conversationListeners.add(listener);
  if (!offEvents) {
    const soon = () => refreshConversations({ soon: true });
    const offs = [events.on('messages', soon), events.on('unread', soon)];
    offEvents = () => offs.forEach((off) => off());
  }
  return () => {
    conversationListeners.delete(listener);
    if (conversationListeners.size === 0) {
      offEvents?.();
      offEvents = null;
    }
  };
}

export function useConversations(): Conversations | null {
  return useSyncExternalStore(subscribeConversations, () => conversations);
}

/** 로그아웃: 다른 사람 대화가 남지 않게 */
export function resetChats() {
  peers.clear();
  groups.clear();
  conversations = null;
  generation++;
  if (soonTimer) clearTimeout(soonTimer);
  soonTimer = null;
  resetChatThreads();
}
