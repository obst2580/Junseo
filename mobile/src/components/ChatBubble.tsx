import { memo, type ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Avatar } from '@/components/ui';
import type { UserSummary } from '@/lib/api';
import { clockTime } from '@/lib/format';
import { colors } from '@/lib/theme';

/**
 * 말풍선 하나. 옆의 숫자는 아직 안 읽은 사람 수다 (1:1 이면 상대가 안 읽었을 때 1, 다 읽으면 사라진다).
 * sender: 단챗에서 남이 보낸 말의 첫 줄이면 얼굴·이름, 이어지는 줄이면 null(자리만), 1:1 은 넘기지 않는다.
 * status: 내가 방금 보낸 말이 아직 서버에 없을 때 (보내는 중 · 실패). 실패하면 눌러서 다시 보낸다.
 * 값이 같으면 다시 그리지 않는다 (memo) — 새 메시지가 와도 이미 있는 말풍선은 그대로 둔다.
 */
export const ChatBubble = memo(function ChatBubble({
  mine,
  text,
  createdAt,
  unread,
  sender,
  status,
  reason,
  onRetry,
  children,
}: {
  mine: boolean;
  text: string;
  createdAt: string;
  unread: number;
  sender?: UserSummary | null;
  status?: 'sending' | 'failed';
  reason?: string;
  onRetry?: () => void;
  /** 말풍선 위에 붙는 것 (사진 답장 등) */
  children?: ReactNode;
}) {
  const grouped = sender !== undefined && !mine;
  return (
    <View style={[styles.row, mine ? styles.right : styles.left]}>
      {grouped && (sender ? <Avatar id={sender.id} name={sender.displayName} size={32} /> : <View style={styles.avatarSpace} />)}
      <View style={[styles.column, mine ? styles.alignEnd : styles.alignStart]}>
        {grouped && sender && <Text style={styles.sender}>{sender.displayName}</Text>}
        {children}
        <View style={[styles.line, mine && styles.lineMine]}>
          <View style={[styles.bubble, mine ? styles.bubbleMine : styles.bubbleTheirs, status === 'sending' && styles.sending, status === 'failed' && styles.failed]}>
            <Text style={[styles.text, mine && { color: colors.accentText }]}>{text}</Text>
          </View>
          {!status && unread > 0 && (
            <Text style={styles.unread} accessibilityLabel={`안 읽은 사람 ${unread}명`}>
              {unread}
            </Text>
          )}
        </View>
        {status === 'failed' ? (
          <Pressable onPress={onRetry} hitSlop={8} accessibilityRole="button" accessibilityLabel="다시 보내기">
            <Text style={styles.failText}>{reason ?? '보내지 못했어요'} · 다시 보내기</Text>
          </Pressable>
        ) : (
          <Text style={styles.time}>{status === 'sending' ? '보내는 중' : clockTime(createdAt)}</Text>
        )}
      </View>
    </View>
  );
});

const styles = StyleSheet.create({
  row: { maxWidth: '84%', flexDirection: 'row', gap: 8 },
  left: { alignSelf: 'flex-start' },
  right: { alignSelf: 'flex-end' },
  avatarSpace: { width: 32 },
  column: { flexShrink: 1, gap: 4 },
  alignStart: { alignItems: 'flex-start' },
  alignEnd: { alignItems: 'flex-end' },
  sender: { color: colors.textDim, fontSize: 12, fontWeight: '600', marginLeft: 2 },
  // 숫자는 말풍선 바깥쪽 아래에 붙는다 (내 말이면 왼쪽)
  line: { flexDirection: 'row', alignItems: 'flex-end', gap: 6 },
  lineMine: { flexDirection: 'row-reverse' },
  bubble: { flexShrink: 1, paddingHorizontal: 14, paddingVertical: 10, borderRadius: 20 },
  bubbleMine: { backgroundColor: colors.accent, borderBottomRightRadius: 6 },
  bubbleTheirs: { backgroundColor: colors.surfaceHigh, borderBottomLeftRadius: 6 },
  sending: { opacity: 0.6 },
  failed: { opacity: 0.5 },
  text: { color: colors.text, fontSize: 15, lineHeight: 21 },
  unread: { color: colors.accent, fontSize: 12, fontWeight: '800', marginBottom: 2 },
  time: { color: colors.textFaint, fontSize: 11 },
  failText: { color: colors.danger, fontSize: 12, fontWeight: '700' },
});
