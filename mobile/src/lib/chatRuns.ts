import { isLocal, type LocalMessage } from '@/lib/chatThread';

/**
 * 같은 사람이 같은 분에 이어서 보낸 말은 한 묶음이다 (카카오톡처럼):
 * 말풍선을 바짝 붙이고, 시간은 묶음의 마지막 말에만 쓰고, 단챗의 얼굴·이름은 묶음의 첫 말에만 쓴다.
 */
type Line = { senderId: number; createdAt: string };

const minuteOf = (iso: string) => Math.floor(Date.parse(iso) / 60000);

function sameRun(a: Line | LocalMessage | undefined, b: Line | LocalMessage | undefined, me: number | undefined): boolean {
  if (!a || !b) return false;
  // 아직 서버에 없는 말(보내는 중 · 실패)은 내가 보낸 말이다
  const sa = isLocal(a) ? me : a.senderId;
  const sb = isLocal(b) ? me : b.senderId;
  return sa !== undefined && sa === sb && minuteOf(a.createdAt) === minuteOf(b.createdAt);
}

/**
 * rows 는 최신이 0 인 뒤집힌 목록이라 바로 앞(위) 말은 index + 1, 바로 뒤(아래) 말은 index - 1.
 * joinAbove: 위 말풍선에 붙는다. last: 묶음의 마지막 말 (시간을 쓴다).
 */
export function runAt(rows: readonly (Line | LocalMessage)[] | null, index: number, me: number | undefined) {
  if (!rows) return { joinAbove: false, last: true };
  return { joinAbove: sameRun(rows[index], rows[index + 1], me), last: !sameRun(rows[index], rows[index - 1], me) };
}
