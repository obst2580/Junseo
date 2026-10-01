import * as Clipboard from 'expo-clipboard';
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Alert, Platform, Pressable, ScrollView, Share, StyleSheet, Text, TextInput, View } from 'react-native';
import { useHeaderHeight } from 'expo-router/react-navigation';

import { Icon } from '@/components/Icon';
import { Avatar, Button, ErrorText } from '@/components/ui';
import { api, ApiError, type UserSummary } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { events } from '@/lib/events';
import { colors, radius } from '@/lib/theme';

export default function FriendsScreen() {
  // 헤더가 바탕(그라데이션) 위에 투명하게 떠 있어서 그만큼 내려서 시작한다
  const headerHeight = useHeaderHeight();
  const { me, refreshMe } = useAuth();
  const [friends, setFriends] = useState<UserSummary[]>([]);
  const [limit, setLimit] = useState(20);
  const [code, setCode] = useState('');
  const [adding, setAdding] = useState(false);
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);

  const load = useCallback(() => {
    api
      .friends()
      .then((r) => {
        setFriends(r.friends);
        setLimit(r.limit);
      })
      .catch(() => {});
  }, []);
  useFocusEffect(load);

  const changed = async () => {
    load();
    await refreshMe().catch(() => {});
    events.emit('friends');
    events.emit('moments');
  };

  const add = async () => {
    setAdding(true);
    setMessage(null);
    try {
      const friend = await api.addFriend(code);
      setCode('');
      setMessage({ text: `${friend.displayName}님과 친구가 됐어요!`, error: false });
      await changed();
    } catch (e) {
      setMessage({ text: e instanceof ApiError ? e.message : '추가하지 못했어요.', error: true });
    } finally {
      setAdding(false);
    }
  };

  const remove = (f: UserSummary) => {
    const run = async () => {
      await api.removeFriend(f.id).catch(() => {});
      await changed();
    };
    const title = `${f.displayName}님을 친구에서 삭제할까요?`;
    const detail = '서로의 사진이 위젯과 히스토리에서 사라져요.';
    if (Platform.OS === 'web') {
      if (globalThis.confirm?.(`${title}\n${detail}`)) run();
      return;
    }
    Alert.alert(title, detail, [
      { text: '취소', style: 'cancel' },
      { text: '삭제', style: 'destructive', onPress: run },
    ]);
  };

  const shareCode = async () => {
    if (!me) return;
    const text = `junseo에서 친구 해요! 초대 코드: ${me.inviteCode}`;
    if (Platform.OS === 'web') {
      await Clipboard.setStringAsync(me.inviteCode);
      setMessage({ text: '초대 코드를 복사했어요.', error: false });
    } else {
      await Share.share({ message: text });
    }
  };

  return (
    <ScrollView style={[styles.flex, { paddingTop: headerHeight }]} contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      {me && (
        <Pressable style={styles.me} onPress={() => router.push('/profile')} accessibilityRole="button" accessibilityLabel="내 정보">
          <Avatar id={me.id} name={me.displayName} size={44} />
          <View style={styles.meText}>
            <Text style={styles.friendName} numberOfLines={1}>
              {me.displayName}
            </Text>
            <Text style={styles.dim}>내 정보 · 위젯 · 로그아웃</Text>
          </View>
          <Icon name="next" size={18} color={colors.textFaint} />
        </Pressable>
      )}
      <View style={styles.card}>
        <Text style={styles.cardLabel}>내 초대 코드</Text>
        <Text style={styles.code} selectable>
          {me?.inviteCode}
        </Text>
        <Text style={styles.dim}>이 코드를 받은 친구가 입력하면 바로 서로 친구가 돼요.</Text>
        <View style={styles.row}>
          <Button title="공유하기" onPress={shareCode} style={{ flex: 1 }} />
          <Button
            title="복사"
            variant="secondary"
            onPress={async () => {
              if (me) await Clipboard.setStringAsync(me.inviteCode);
              setMessage({ text: '초대 코드를 복사했어요.', error: false });
            }}
            style={{ flex: 1 }}
          />
        </View>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardLabel}>친구 코드 입력</Text>
        <View style={styles.row}>
          <TextInput
            value={code}
            onChangeText={(t) => setCode(t.toUpperCase())}
            placeholder="예: K7Q2MX9A"
            placeholderTextColor={colors.textFaint}
            autoCapitalize="characters"
            autoCorrect={false}
            maxLength={12}
            style={styles.codeInput}
            onSubmitEditing={add}
          />
          <Button title="추가" onPress={add} loading={adding} disabled={code.trim().length < 8} style={{ width: 80, paddingHorizontal: 0 }} />
        </View>
        {message && (message.error ? <ErrorText>{message.text}</ErrorText> : <Text style={styles.ok}>{message.text}</Text>)}
      </View>

      <View style={styles.listHeader}>
        <Text style={styles.sectionTitle}>친구</Text>
        <Text style={styles.dim}>
          {friends.length} / {limit}
        </Text>
      </View>
      {friends.length === 0 && <Text style={[styles.dim, { paddingHorizontal: 4 }]}>아직 친구가 없어요. 초대 코드를 공유해 보세요.</Text>}
      {friends.map((f) => (
        <View key={f.id} style={styles.friend}>
          <Avatar id={f.id} name={f.displayName} size={40} />
          <Text style={styles.friendName}>{f.displayName}</Text>
          <Pressable onPress={() => remove(f)} hitSlop={10} accessibilityLabel={`${f.displayName} 삭제`}>
            <Icon name="x" size={22} color={colors.textFaint} />
          </Pressable>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 16, gap: 14, maxWidth: 560, width: '100%', alignSelf: 'center' },
  me: { flexDirection: 'row', alignItems: 'center', gap: 12, backgroundColor: colors.surface, borderRadius: radius.card, paddingVertical: 12, paddingHorizontal: 14 },
  meText: { flex: 1, gap: 2 },
  card: { backgroundColor: colors.surface, borderRadius: radius.card, padding: 18, gap: 12 },
  cardLabel: { color: colors.textDim, fontSize: 13, fontWeight: '700' },
  code: { color: colors.accent, fontSize: 34, fontWeight: '900', letterSpacing: 4 },
  dim: { color: colors.textDim, fontSize: 14, lineHeight: 20 },
  ok: { color: colors.accent, fontSize: 14, textAlign: 'center' },
  row: { flexDirection: 'row', gap: 10, alignItems: 'center' },
  codeInput: {
    flex: 1,
    minWidth: 0,
    height: 52,
    borderRadius: 16,
    paddingHorizontal: 16,
    backgroundColor: colors.sunk,
    color: colors.text,
    fontSize: 18,
    fontWeight: '700',
    letterSpacing: 2,
  },
  listHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', marginTop: 8, paddingHorizontal: 4 },
  sectionTitle: { color: colors.text, fontSize: 18, fontWeight: '800' },
  friend: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 8, paddingHorizontal: 4 },
  friendName: { flex: 1, color: colors.text, fontSize: 16, fontWeight: '600' },
});
