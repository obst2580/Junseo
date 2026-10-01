import Ionicons from '@expo/vector-icons/Ionicons';
import type { ReactNode } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { GroupAvatar } from '@/components/GroupAvatar';
import { Avatar } from '@/components/ui';
import type { GroupChat, UserSummary } from '@/lib/api';
import { groupTitle } from '@/lib/groups';
import { colors, radius } from '@/lib/theme';

/**
 * 사진을 보낼 친구 고르기. 기본은 친구 전체이고, 보내기 싫은 친구를 눌러 뺀다.
 * 단체방이 있으면 단체방도 고를 수 있다: 그 방 사람들을 한꺼번에 넣고 빼는 묶음이다
 * (방 사람이 모두 들어 있으면 체크로 보인다). 고른 상태는 바로 반영된다 (완료는 닫기만 한다).
 */
export function RecipientSheet({
  friends,
  groups,
  meId,
  excluded,
  onChange,
  onClose,
  onManage,
}: {
  friends: UserSummary[];
  groups: GroupChat[];
  meId?: number;
  excluded: ReadonlySet<number>;
  onChange: (excluded: Set<number>) => void;
  onClose: () => void;
  onManage: () => void;
}) {
  const insets = useSafeAreaInsets();
  const chosen = friends.filter((f) => !excluded.has(f.id)).length;
  const all = chosen === friends.length;

  const toggle = (id: number) => {
    const next = new Set(excluded);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    onChange(next);
  };

  // 단체방마다 지금도 내 친구인 사람만 (나간 친구·친구 끊은 사람은 사진을 받을 수 없다)
  const friendIds = new Set(friends.map((f) => f.id));
  const rooms = groups
    .map((g) => ({ group: g, ids: g.members.filter((m) => m.id !== meId && friendIds.has(m.id)).map((m) => m.id) }))
    .filter((r) => r.ids.length > 0);
  const toggleRoom = (ids: number[], on: boolean) => {
    const next = new Set(excluded);
    ids.forEach((id) => (on ? next.add(id) : next.delete(id)));
    onChange(next);
  };

  return (
    <Modal transparent animationType="slide" visible onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose} accessibilityLabel="닫기" />
      <View style={[styles.sheet, { paddingBottom: insets.bottom + 12 }]}>
        <View style={styles.grabber} />
        <Text style={styles.title}>보낼 친구</Text>
        <Text style={styles.subtitle}>{chosen === 0 ? '받을 친구를 한 명 이상 골라 주세요' : all ? '친구 전체에게 보내요' : `${friends.length}명 중 ${chosen}명에게 보내요`}</Text>

        <Row label="전체" checked={all} onPress={() => onChange(all ? new Set(friends.map((f) => f.id)) : new Set())} />
        <View style={styles.divider} />
        <ScrollView style={styles.list}>
          {rooms.length > 0 && (
            <>
              <Text style={styles.section}>단체방</Text>
              {rooms.map(({ group, ids }) => {
                const on = ids.every((id) => !excluded.has(id));
                return (
                  <Row
                    key={`g${group.id}`}
                    label={groupTitle(group, meId)}
                    detail={`${ids.length}명`}
                    icon={<GroupAvatar members={group.members.filter((m) => m.id !== meId)} size={40} />}
                    checked={on}
                    onPress={() => toggleRoom(ids, on)}
                  />
                );
              })}
              <Text style={styles.section}>친구</Text>
            </>
          )}
          {friends.map((f) => (
            <Row key={f.id} label={f.displayName} avatar={f} checked={!excluded.has(f.id)} onPress={() => toggle(f.id)} />
          ))}
        </ScrollView>

        <View style={styles.actions}>
          <Pressable onPress={onManage} style={[styles.button, styles.secondary]} accessibilityRole="button">
            <Text style={styles.secondaryText}>친구 관리</Text>
          </Pressable>
          <Pressable onPress={onClose} style={[styles.button, styles.primary]} accessibilityRole="button">
            <Text style={styles.primaryText}>완료</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

function Row({
  label,
  detail,
  avatar,
  icon,
  checked,
  onPress,
}: {
  label: string;
  detail?: string;
  avatar?: UserSummary;
  icon?: ReactNode;
  checked: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable onPress={onPress} style={styles.row} accessibilityRole="checkbox" aria-checked={checked} accessibilityLabel={label}>
      {icon ??
        (avatar ? (
          <Avatar id={avatar.id} name={avatar.displayName} size={40} />
        ) : (
          <View style={styles.allIcon}>
            <Ionicons name="people" size={20} color={colors.text} />
          </View>
        ))}
      <Text style={[styles.rowText, !checked && styles.rowTextOff]} numberOfLines={1}>
        {label}
        {detail && <Text style={styles.rowDetail}> {detail}</Text>}
      </Text>
      <Ionicons name={checked ? 'checkmark-circle' : 'ellipse-outline'} size={26} color={checked ? colors.accent : colors.textDim} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)' },
  sheet: {
    maxHeight: '75%',
    backgroundColor: colors.surface,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    paddingHorizontal: 20,
    paddingTop: 10,
  },
  grabber: { alignSelf: 'center', width: 40, height: 5, borderRadius: 3, backgroundColor: colors.surfaceHigh, marginBottom: 12 },
  title: { color: colors.text, fontSize: 20, fontWeight: '800' },
  subtitle: { color: colors.textDim, fontSize: 14, marginTop: 4, marginBottom: 12 },
  list: { flexGrow: 0 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 8 },
  allIcon: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.surfaceHigh, alignItems: 'center', justifyContent: 'center' },
  rowText: { flex: 1, color: colors.text, fontSize: 16, fontWeight: '600' },
  rowTextOff: { color: colors.textDim },
  rowDetail: { color: colors.textFaint, fontSize: 14, fontWeight: '600' },
  section: { color: colors.textDim, fontSize: 13, fontWeight: '700', marginTop: 8, marginBottom: 2 },
  divider: { height: StyleSheet.hairlineWidth, backgroundColor: colors.border, marginVertical: 4 },
  actions: { flexDirection: 'row', gap: 10, marginTop: 14 },
  button: { flex: 1, height: 50, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center' },
  secondary: { backgroundColor: colors.surfaceHigh },
  secondaryText: { color: colors.text, fontSize: 16, fontWeight: '700' },
  primary: { backgroundColor: colors.accent },
  primaryText: { color: colors.accentText, fontSize: 16, fontWeight: '800' },
});
