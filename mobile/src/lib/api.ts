import { API_BASE_URL } from './config';

// docs/api.md 의 객체들
export type UserSummary = { id: number; displayName: string };
export type Me = UserSummary & { email: string; inviteCode: string; friendCount: number; friendLimit: number };
export type ReactionCount = { emoji: string; count: number };
export type Comment = { id: number; momentId: number; author: UserSummary; text: string; createdAt: string };
export type Moment = {
  id: number;
  sender: UserSummary;
  createdAt: string;
  imageUrl: string;
  thumbUrl: string;
  reactions: ReactionCount[];
  myReactions: ReactionCount[]; // 내가 누른 이모지별 횟수
  commentCount: number;
  recentComments: Comment[];
};
export type MomentDetail = Moment & { comments: Comment[] };
export type Message = {
  id: number;
  senderId: number;
  receiverId: number;
  text: string;
  createdAt: string;
  readAt: string | null;
  moment: { id: number; thumbUrl: string } | null;
  /** 내가 보낸 메시지에만: 보낼 때 앱이 붙인 ID (보내는 중인 말풍선과 짝을 맞춘다) */
  clientId?: string | null;
};
export type Conversation = { peer: UserSummary; lastMessage: Message; unreadCount: number };
/** 단챗. members 에는 나도 들어 있다. name 이 없으면 나를 뺀 사람들 이름으로 부른다 (lib/groups.ts). */
export type GroupChat = { id: number; name: string | null; members: UserSummary[]; createdAt: string };
/** unreadCount: 보낸 사람을 빼고 아직 안 읽은 사람 수 */
export type GroupMessage = {
  id: number;
  groupId: number;
  senderId: number;
  text: string;
  createdAt: string;
  unreadCount: number;
  clientId?: string | null;
};
export type GroupConversation = { group: GroupChat; lastMessage: GroupMessage | null; unreadCount: number };
export type Page<T> = { items: T[]; nextCursor: string | null };
export type AuthResponse = { accessToken: string; expiresAt: string; user: Me };
export type WidgetLatest = {
  version: string;
  moment: { id: number; sender: UserSummary; createdAt: string; thumbUrl: string };
  reactions: ReactionCount[];
  reactionCount: number;
  comments: { author: string; text: string }[];
  commentCount: number;
};
/** 위젯이 넘겨 보는 사진들 (최신 → 오래된 순, 최근 24시간 최대 5장. 없으면 가장 최근 1장) */
export type WidgetFeed = { version: string; items: WidgetLatest[] };

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

let tokenProvider: () => string | null = () => null;
let onUnauthorized: () => void = () => {};

/** 지금 로그인 토큰 (실시간 연결이 첫 메시지로 보낸다) */
export const currentToken = () => tokenProvider();

export function configureApi(options: { getToken: () => string | null; onUnauthorized: () => void }) {
  tokenProvider = options.getToken;
  onUnauthorized = options.onUnauthorized;
}

/** timeoutMs: 이 시간 안에 응답이 없으면 연결 문제로 본다 (기본: 기다린다) */
type RequestOptions = { method?: string; body?: unknown; form?: FormData; timeoutMs?: number };

// 메시지 보내기는 이 시간이 지나면 「보내지 못했어요」로 바꾼다. 같은 ID로 다시 보내므로 두 번 가지 않는다.
const SEND_TIMEOUT_MS = 15_000;

async function request<T>(path: string, { method = 'GET', body, form, timeoutMs }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const token = tokenProvider();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const abort = timeoutMs ? new AbortController() : null;
  const timer = abort ? setTimeout(() => abort.abort(), timeoutMs) : null;
  let res: Response;
  try {
    res = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
      signal: abort?.signal,
    });
  } catch {
    throw new ApiError(0, 'NETWORK', '서버에 연결할 수 없어요. 네트워크를 확인해 주세요.');
  } finally {
    if (timer) clearTimeout(timer);
  }

  if (res.status === 204) return undefined as T;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    if (res.status === 401 && token) onUnauthorized();
    throw new ApiError(res.status, data?.code ?? 'UNKNOWN', data?.message ?? '문제가 생겼어요. 잠시 후 다시 시도해 주세요.');
  }
  return data as T;
}

const q = (params: Record<string, string | number | null | undefined>) => {
  const s = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&');
  return s ? `?${s}` : '';
};

export const api = {
  signup: (email: string, password: string, displayName: string) =>
    request<AuthResponse>('/api/auth/signup', { method: 'POST', body: { email, password, displayName } }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body: { email, password } }),

  me: () => request<Me>('/api/me'),
  updateMe: (displayName: string) => request<Me>('/api/me', { method: 'PATCH', body: { displayName } }),
  rotateInviteCode: () => request<Me>('/api/me/invite-code', { method: 'POST' }),

  friends: () => request<{ friends: UserSummary[]; limit: number }>('/api/friends'),
  addFriend: (inviteCode: string) => request<UserSummary>('/api/friends', { method: 'POST', body: { inviteCode } }),
  removeFriend: (userId: number) => request<void>(`/api/friends/${userId}`, { method: 'DELETE' }),

  /** recipientIds 를 주면 그 친구들에게만, 빼면 친구 전체에게 보낸다. */
  uploadMoment: (fileUri: string, recipientIds?: number[]) => {
    const form = new FormData();
    // React Native 의 FormData 는 { uri, name, type } 객체를 파일로 보낸다.
    form.append('image', { uri: fileUri, name: 'moment.jpg', type: 'image/jpeg' } as unknown as Blob);
    recipientIds?.forEach((id) => form.append('recipientIds', String(id)));
    return request<Moment>('/api/moments', { method: 'POST', form });
  },
  uploadMomentBlob: (blob: Blob, recipientIds?: number[]) => {
    const form = new FormData();
    form.append('image', blob, 'moment.jpg');
    recipientIds?.forEach((id) => form.append('recipientIds', String(id)));
    return request<Moment>('/api/moments', { method: 'POST', form });
  },
  moments: (params: { cursor?: string | null; userId?: number | null; limit?: number } = {}) =>
    request<Page<Moment>>(`/api/moments${q(params)}`),
  moment: (id: number) => request<MomentDetail>(`/api/moments/${id}`),
  deleteMoment: (id: number) => request<void>(`/api/moments/${id}`, { method: 'DELETE' }),

  /** 이모지를 count 번 누른 것으로 더한다 (한 번에 1~20). */
  react: (momentId: number, emoji: string, count = 1) =>
    request<Moment>(`/api/moments/${momentId}/reactions`, { method: 'POST', body: { emoji, count } }),
  /** 이 사진에 내가 누른 반응을 모두 지운다. */
  clearReactions: (momentId: number) => request<void>(`/api/moments/${momentId}/reactions`, { method: 'DELETE' }),

  comment: (momentId: number, text: string) =>
    request<Comment>(`/api/moments/${momentId}/comments`, { method: 'POST', body: { text } }),
  deleteComment: (commentId: number) => request<void>(`/api/comments/${commentId}`, { method: 'DELETE' }),

  reply: (momentId: number, text: string) =>
    request<Message>(`/api/moments/${momentId}/replies`, { method: 'POST', body: { text } }),
  conversations: () => request<{ items: Conversation[]; groups: GroupConversation[] }>('/api/conversations'),
  messages: (peerId: number, cursor?: string | null) =>
    request<Page<Message>>(`/api/conversations/${peerId}/messages${q({ cursor })}`),
  sendMessage: (peerId: number, text: string, clientId?: string) =>
    request<Message>(`/api/conversations/${peerId}/messages`, { method: 'POST', body: { text, clientId }, timeoutMs: SEND_TIMEOUT_MS }),
  markRead: (peerId: number) => request<void>(`/api/conversations/${peerId}/read`, { method: 'POST' }),

  /** 내 친구들 중 서로 친구인 쌍. 단챗은 모두가 서로 친구여야 만들 수 있다. */
  friendLinks: () => request<{ pairs: [number, number][] }>('/api/friends/links'),
  groups: () => request<{ items: GroupChat[] }>('/api/groups'),
  group: (id: number) => request<GroupChat>(`/api/groups/${id}`),
  createGroup: (memberIds: number[], name?: string) => request<GroupChat>('/api/groups', { method: 'POST', body: { memberIds, name } }),
  groupMessages: (id: number, cursor?: string | null) => request<Page<GroupMessage>>(`/api/groups/${id}/messages${q({ cursor })}`),
  sendGroupMessage: (id: number, text: string, clientId?: string) =>
    request<GroupMessage>(`/api/groups/${id}/messages`, { method: 'POST', body: { text, clientId }, timeoutMs: SEND_TIMEOUT_MS }),
  markGroupRead: (id: number) => request<void>(`/api/groups/${id}/read`, { method: 'POST' }),
  leaveGroup: (id: number) => request<void>(`/api/groups/${id}/members/me`, { method: 'DELETE' }),

  widgetLatest: () => request<WidgetLatest | undefined>('/api/widget/latest'),
  widgetFeed: (from?: number) => request<WidgetFeed | undefined>(`/api/widget/feed${q({ from })}`),

  registerDevice: (token: string, kind: 'app' | 'widget', environment: 'development' | 'production') =>
    request<void>('/api/devices', { method: 'PUT', body: { token, kind, environment } }),
  unregisterDevice: (token: string) => request<void>(`/api/devices/${encodeURIComponent(token)}`, { method: 'DELETE' }),
};
