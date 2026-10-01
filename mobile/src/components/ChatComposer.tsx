import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, View } from 'react-native';

import { Icon } from '@/components/Icon';
import { colors } from '@/lib/theme';

/**
 * 채팅 입력줄. 보내면 바로 비우고 키보드는 그대로 둔다 (연달아 보낼 수 있게).
 * 보내는 건 기다리지 않는다 — 말풍선은 useChatThread 가 바로 띄우고 뒤에서 보낸다.
 */
export function ChatComposer({ onSend }: { onSend: (text: string) => void }) {
  const [text, setText] = useState('');
  const submit = () => {
    const body = text.trim();
    if (!body) return;
    onSend(body);
    setText('');
  };
  const empty = !text.trim();
  return (
    <View style={styles.row}>
      <TextInput
        value={text}
        onChangeText={setText}
        placeholder="메시지 보내기"
        placeholderTextColor={colors.textFaint}
        maxLength={500}
        style={styles.input}
        onSubmitEditing={submit}
        submitBehavior="submit"
        returnKeyType="send"
      />
      <Pressable onPress={submit} disabled={empty} style={[styles.send, empty && { opacity: 0.4 }]} accessibilityRole="button" accessibilityLabel="보내기">
        <Icon name="send" size={20} color={colors.accentText} />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.border },
  input: { flex: 1, height: 44, borderRadius: 22, paddingHorizontal: 16, backgroundColor: colors.surface, color: colors.text, fontSize: 15 },
  send: { width: 44, height: 44, borderRadius: 22, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center' },
});
