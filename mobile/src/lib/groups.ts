import type { GroupChat } from '@/lib/api';

/** 이름이 없는 단체방은 나를 뺀 사람들 이름으로 부른다 (서버 알림 제목과 같다). */
export function groupTitle(group: GroupChat, meId?: number) {
  return group.name ?? group.members.filter((m) => m.id !== meId).map((m) => m.displayName).join(', ');
}

/** 서로 친구인 두 사람을 가리키는 키 (작은 id 먼저) */
export const linkKey = (a: number, b: number) => (a < b ? `${a}-${b}` : `${b}-${a}`);

/** 「민지와」 / 「하준과」: 마지막 글자에 받침이 있으면 과, 없으면 와 */
export function withAnd(word: string) {
  const code = word.charCodeAt(word.length - 1) - 0xac00;
  const batchim = code >= 0 && code < 11172 && code % 28 !== 0;
  return word + (batchim ? '과' : '와');
}
