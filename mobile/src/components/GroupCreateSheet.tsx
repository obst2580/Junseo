import Ionicons from '@expo/vector-icons/Ionicons';
import { useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Avatar, ErrorText } from '@/components/ui';
import { api, ApiError, type GroupChat, type UserSummary } from '@/lib/api';
import { linkKey, withAnd } from '@/lib/groups';
import { colors, radius } from '@/lib/theme';

const MIN_OTHERS = 2;

/**
 * 단체방 만들기. 모두가 서로 친구여야 해서, 이미 고른 친구와 친구가 아닌 사람은 고를 수 없게 흐리게 둔다.
 * links: 내 친구들 중 서로 친구인 쌍 (linkKey).
 */
export function GroupCreateSheet({
  friends,
  links,
  onClose,
  onCreated,
}: {
  friends: UserSummary[];
  links: ReadonlySet<string>;
  onClose: () => void;
  onCreated: (group: GroupChat) => void;
}) {
  const insets = useSafeAreaInsets();
  const [chosen, setChosen] = useState<number[]>([]);
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const strangersTo = (f: UserSummary) => chosen.filter((id) => !links.has(linkKey(id, f.id)));
  const nameOf = (id: number) => friends.find((f) => f.id === id)?.displayName ?? '';

  const toggle = (f: UserSummary) => {
    setError(null);
    setChosen((list) => (list.includes(f.id) ? list.filter((id) => id !== f.id) : [...list, f.id]));
  };

  const create = async () => {
    setBusy(true);
    setError(null);
    try {
      onCreated(await api.createGroup(chosen, name.trim() || undefined));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '만들지 못했어요.');
    } finally {
      setBusy(false);
    }
  };

  const ready = chosen.length >= MIN_OTHERS && !busy;

  return (
    <Modal transparent animationType="slide" visible onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose} accessibilityLabel="닫기" />
      <View style={[styles.sheet, { paddingBottom: insets.bottom + 12 }]}>
        <View style={styles.grabber} />
        <Text style={styles.title}>단체방 만들기</Text>
        <Text style={styles.subtitle}>
          {chosen.length === 0 ? '서로 친구인 사람끼리만 만들 수 있어요' : `${chosen.length}명 골랐어요 · 나까지 ${chosen.length + 1}명`}
        </Text>
        <TextInput
          value={name}
          onChangeText={setName}
          placeholder="방 이름 (안 쓰면 이름들로 보여요)"
          placeholderTextColor={colors.textFaint}
          maxLength={30}
          style={styles.name}
          accessibilityLabel="방 이름"
        />
        {friends.length < MIN_OTHERS ? (
          <Text style={styles.empty}>친구가 2명 이상 있어야 단체방을 만들 수 있어요.</Text>
        ) : (
          <ScrollView style={styles.list}>
            {friends.map((f) => {
              const checked = chosen.includes(f.id);
              const strangers = checked ? [] : strangersTo(f);
              const blocked = strangers.length > 0;
              return (
                <Pressable
                  key={f.id}
                  onPress={() => toggle(f)}
                  disabled={blocked}
                  style={[styles.row, blocked && styles.rowBlocked]}
                  accessibilityRole="checkbox"
                  aria-checked={checked}
                  aria-disabled={blocked}
                  accessibilityLabel={f.displayName}>
                  <Avatar id={f.id} name={f.displayName} size={40} />
                  <View style={styles.rowText}>
                    <Text style={styles.rowName} numberOfLines={1}>
                      {f.displayName}
                    </Text>
                    {blocked && (
                      <Text style={styles.rowNote} numberOfLines={1}>
                        {withAnd(strangers.length > 1 ? `${nameOf(strangers[0])} 외 ${strangers.length - 1}명` : nameOf(strangers[0]))} 친구가 아니에요
                      </Text>
                    )}
                  </View>
                  <Ionicons name={checked ? 'checkmark-circle' : 'ellipse-outline'} size={26} color={checked ? colors.accent : colors.textDim} />
                </Pressable>
              );
            })}
          </ScrollView>
        )}
        {error && <ErrorText>{error}</ErrorText>}
        <View style={styles.actions}>
          <Pressable onPress={onClose} style={[styles.button, styles.secondary]} accessibilityRole="button">
            <Text style={styles.secondaryText}>취소</Text>
          </Pressable>
          <Pressable onPress={create} disabled={!ready} style={[styles.button, styles.primary, !ready && { opacity: 0.4 }]} accessibilityRole="button">
            <Text style={styles.primaryText}>{chosen.length >= MIN_OTHERS ? `${chosen.length + 1}명으로 만들기` : '2명 이상 골라 주세요'}</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)' },
  sheet: {
    maxHeight: '80%',
    backgroundColor: colors.surface,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    paddingHorizontal: 20,
    paddingTop: 10,
  },
  grabber: { alignSelf: 'center', width: 40, height: 5, borderRadius: 3, backgroundColor: colors.surfaceHigh, marginBottom: 12 },
  title: { color: colors.text, fontSize: 20, fontWeight: '800' },
  subtitle: { color: colors.textDim, fontSize: 14, marginTop: 4, marginBottom: 12 },
  name: { height: 46, borderRadius: 14, paddingHorizontal: 14, backgroundColor: colors.bg, color: colors.text, fontSize: 15, marginBottom: 8 },
  empty: { color: colors.textDim, fontSize: 14, paddingVertical: 20, textAlign: 'center' },
  list: { flexGrow: 0 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 8 },
  rowBlocked: { opacity: 0.4 },
  rowText: { flex: 1, gap: 2 },
  rowName: { color: colors.text, fontSize: 16, fontWeight: '600' },
  rowNote: { color: colors.textDim, fontSize: 12 },
  actions: { flexDirection: 'row', gap: 10, marginTop: 14 },
  button: { flex: 1, height: 50, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center' },
  secondary: { flex: 0.6, backgroundColor: colors.surfaceHigh },
  secondaryText: { color: colors.text, fontSize: 16, fontWeight: '700' },
  primary: { backgroundColor: colors.accent },
  primaryText: { color: colors.accentText, fontSize: 16, fontWeight: '800' },
});
