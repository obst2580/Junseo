import { router, Stack, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, KeyboardAvoidingView, Platform, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useHeaderHeight } from 'expo-router/react-navigation';

import { Icon } from '@/components/Icon';
import { ChatBubble } from '@/components/ChatBubble';
import { ErrorText } from '@/components/ui';
import { api, ApiError, type GroupChat, type GroupMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { events } from '@/lib/events';
import { groupTitle } from '@/lib/groups';
import { colors } from '@/lib/theme';

// 1:1 챗과 같이, 화면이 열려 있는 동안 주기적으로 새 메시지와 읽음 수를 가져온다. 푸시가 오면 바로 갱신한다.
const POLL_MS = 5000;

export default function GroupChatScreen() {
  // 헤더가 바탕(그라데이션) 위에 투명하게 떠 있어서 그만큼 내려서 시작한다
  const headerHeight = useHeaderHeight();
  const { id } = useLocalSearchParams<{ id: string }>();
  const groupId = Number(id);
  const { me } = useAuth();

  const [group, setGroup] = useState<GroupChat | null>(null);
  const [items, setItems] = useState<GroupMessage[] | null>(null);
  const [cursor, setCursor] = useState<string | null>(null);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const loadingMore = useRef(false);
  // 읽음 처리를 보낸 마지막 메시지. 같은 메시지로 여러 번 보내지 않는다.
  const markedUpTo = useRef(0);

  const refresh = useCallback(async () => {
    const page = await api.groupMessages(groupId);
    setItems((prev) => {
      // 이미 불러온 예전 메시지는 두고 최신 페이지(읽음 수 포함)만 새로 바꾼다.
      const byId = new Map((prev ?? []).map((m) => [m.id, m]));
      page.items.forEach((m) => byId.set(m.id, m));
      return [...byId.values()].sort((a, b) => b.id - a.id);
    });
    setCursor((c) => c ?? page.nextCursor);
    const newest = page.items[0]?.id ?? 0;
    if (newest > markedUpTo.current) {
      markedUpTo.current = newest;
      await api.markGroupRead(groupId).catch(() => {});
      events.emit('messages');
    }
  }, [groupId]);

  useEffect(() => {
    api
      .group(groupId)
      .then(setGroup)
      .catch(() => {});
    const timer = setInterval(() => refresh().catch(() => {}), POLL_MS);
    const off = events.on('messages', () => void refresh().catch(() => {}));
    return () => {
      clearInterval(timer);
      off();
    };
  }, [groupId, refresh]);

  useFocusEffect(
    useCallback(() => {
      refresh().catch(() => setItems((prev) => prev ?? []));
    }, [refresh]),
  );

  const loadOlder = async () => {
    if (!cursor || loadingMore.current) return;
    loadingMore.current = true;
    try {
      const page = await api.groupMessages(groupId, cursor);
      setItems((prev) => [...(prev ?? []), ...page.items.filter((m) => !prev?.some((p) => p.id === m.id))]);
      setCursor(page.nextCursor);
    } finally {
      loadingMore.current = false;
    }
  };

  const send = async () => {
    const body = text.trim();
    if (!body) return;
    setSending(true);
    setError(null);
    try {
      const msg = await api.sendGroupMessage(groupId, body);
      markedUpTo.current = Math.max(markedUpTo.current, msg.id);
      setItems((prev) => [msg, ...(prev ?? [])]);
      setText('');
      events.emit('messages');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '보내지 못했어요.');
    } finally {
      setSending(false);
    }
  };

  const leave = () => {
    const run = async () => {
      await api.leaveGroup(groupId).catch(() => {});
      events.emit('messages');
      router.back();
    };
    const title = '단챗에서 나갈까요?';
    const detail = '나가면 대화 내용이 내 목록에서 사라져요.';
    if (Platform.OS === 'web') {
      if (globalThis.confirm?.(`${title}\n${detail}`)) run();
      return;
    }
    Alert.alert(title, detail, [
      { text: '취소', style: 'cancel' },
      { text: '나가기', style: 'destructive', onPress: run },
    ]);
  };

  const people = new Map(group?.members.map((m) => [m.id, m]));

  return (
    <SafeAreaView style={[styles.flex, { paddingTop: headerHeight }]} edges={['bottom']}>
      <Stack.Screen
        options={{
          title: group ? `${groupTitle(group, me?.id)} ${group.members.length}` : '',
          headerRight: () => (
            <Pressable onPress={leave} hitSlop={10} accessibilityRole="button" accessibilityLabel="단챗 나가기">
              <Icon name="exit" size={22} color={colors.textDim} />
            </Pressable>
          ),
        }}
      />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.flex} keyboardVerticalOffset={90}>
        {items === null ? (
          <ActivityIndicator color={colors.accent} style={{ marginTop: 40 }} />
        ) : (
          <FlatList
            inverted
            data={items}
            keyExtractor={(m) => String(m.id)}
            contentContainerStyle={styles.list}
            onEndReached={loadOlder}
            onEndReachedThreshold={0.3}
            renderItem={({ item, index }) => {
              const mine = item.senderId === me?.id;
              // 같은 사람이 이어서 보낸 말에는 얼굴·이름을 한 번만 (목록이 뒤집혀 있어서 바로 앞 말은 index + 1)
              const firstOfRun = items[index + 1]?.senderId !== item.senderId;
              const sender = people.get(item.senderId) ?? { id: item.senderId, displayName: '나간 친구' };
              return (
                <ChatBubble mine={mine} text={item.text} createdAt={item.createdAt} unread={item.unreadCount} sender={firstOfRun ? sender : null} />
              );
            }}
          />
        )}
        {error && <ErrorText>{error}</ErrorText>}
        <View style={styles.inputRow}>
          <TextInput
            value={text}
            onChangeText={setText}
            placeholder="메시지 보내기"
            placeholderTextColor={colors.textFaint}
            maxLength={500}
            style={styles.input}
            onSubmitEditing={send}
            returnKeyType="send"
          />
          <Pressable onPress={send} disabled={sending || !text.trim()} style={[styles.sendButton, (!text.trim() || sending) && { opacity: 0.4 }]}>
            <Icon name="send" size={20} color={colors.accentText} />
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  list: { padding: 12, gap: 10 },
  inputRow: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.border },
  input: { flex: 1, height: 44, borderRadius: 22, paddingHorizontal: 16, backgroundColor: colors.surface, color: colors.text, fontSize: 15 },
  sendButton: { width: 44, height: 44, borderRadius: 22, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center' },
});
