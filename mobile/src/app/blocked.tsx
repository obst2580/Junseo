import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useHeaderHeight } from 'expo-router/react-navigation';

import { Avatar, Empty } from '@/components/ui';
import { api, type UserSummary } from '@/lib/api';
import { colors, radius } from '@/lib/theme';

/** 차단한 사람. 차단을 풀어도 친구로 돌아오지는 않는다 (초대 코드로 다시 친구가 될 수 있다). */
export default function BlockedScreen() {
  const headerHeight = useHeaderHeight();
  const [list, setList] = useState<UserSummary[] | null>(null);

  const load = useCallback(() => {
    api
      .blocks()
      .then((r) => setList(r.items))
      .catch(() => setList([]));
  }, []);
  useFocusEffect(load);

  const unblock = async (u: UserSummary) => {
    await api.unblock(u.id).catch(() => {});
    load();
  };

  if (list === null) return <ActivityIndicator color={colors.accent} style={{ marginTop: headerHeight + 40 }} />;
  if (list.length === 0) return <Empty icon="eyeOff" title="차단한 사람이 없어요." />;
  return (
    <ScrollView style={[styles.flex, { paddingTop: headerHeight }]} contentContainerStyle={styles.content}>
      <Text style={styles.lead}>차단을 풀어도 친구로 돌아오지는 않아요. 다시 친구가 되려면 초대 코드로 추가해 주세요.</Text>
      {list.map((u) => (
        <View key={u.id} style={styles.row}>
          <Avatar id={u.id} name={u.displayName} size={40} />
          <Text style={styles.name}>{u.displayName}</Text>
          <Pressable onPress={() => void unblock(u)} style={styles.button} accessibilityRole="button" accessibilityLabel={`${u.displayName} 차단 풀기`}>
            <Text style={styles.buttonText}>차단 풀기</Text>
          </Pressable>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 16, gap: 12, maxWidth: 560, width: '100%', alignSelf: 'center' },
  lead: { color: colors.textDim, fontSize: 14, lineHeight: 20, marginBottom: 4 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 12, borderRadius: radius.card, backgroundColor: colors.surface },
  name: { flex: 1, color: colors.text, fontSize: 16, fontWeight: '600' },
  button: { height: 34, paddingHorizontal: 14, borderRadius: radius.button, backgroundColor: colors.surfaceHigh, justifyContent: 'center' },
  buttonText: { color: colors.text, fontSize: 14, fontWeight: '700' },
});
