import Ionicons from '@expo/vector-icons/Ionicons';
import { Image } from 'expo-image';
import { router, Stack, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, KeyboardAvoidingView, Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ErrorText } from '@/components/ui';
import { api, ApiError, type Message, type UserSummary } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { absoluteUrl } from '@/lib/config';
import { events } from '@/lib/events';
import { clockTime } from '@/lib/format';
import { colors } from '@/lib/theme';

// 실시간 연결 없이 MVP 에서는 화면이 열려 있는 동안만 주기적으로 새 메시지를 가져온다. 푸시가 오면 바로 갱신한다.
const POLL_MS = 5000;

export default function ChatScreen() {
  const { peerId } = useLocalSearchParams<{ peerId: string }>();
  const peer = Number(peerId);
  const { me } = useAuth();

  const [peerInfo, setPeerInfo] = useState<UserSummary | null>(null);
  const [items, setItems] = useState<Message[] | null>(null);
  const [cursor, setCursor] = useState<string | null>(null);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const loadingMore = useRef(false);

  const refresh = useCallback(async () => {
    const page = await api.messages(peer);
    setItems((prev) => {
      // 이미 불러온 예전 메시지는 유지하고 최신 페이지만 합친다.
      const byId = new Map((prev ?? []).map((m) => [m.id, m]));
      page.items.forEach((m) => byId.set(m.id, m));
      return [...byId.values()].sort((a, b) => b.id - a.id);
    });
    setCursor((c) => c ?? page.nextCursor);
    if (page.items.some((m) => m.senderId === peer && !m.readAt)) {
      await api.markRead(peer).catch(() => {});
      events.emit('messages');
    }
  }, [peer]);

  useEffect(() => {
    api
      .conversations()
      .then((r) => setPeerInfo(r.items.find((c) => c.peer.id === peer)?.peer ?? null))
      .catch(() => {});
    api
      .friends()
      .then((r) => setPeerInfo((p) => p ?? r.friends.find((f) => f.id === peer) ?? null))
      .catch(() => {});
    const timer = setInterval(() => refresh().catch(() => {}), POLL_MS);
    const off = events.on('messages', () => void refresh().catch(() => {}));
    return () => {
      clearInterval(timer);
      off();
    };
  }, [peer, refresh]);

  useFocusEffect(
    useCallback(() => {
      refresh().catch(() => setItems((prev) => prev ?? []));
    }, [refresh]),
  );

  const loadOlder = async () => {
    if (!cursor || loadingMore.current) return;
    loadingMore.current = true;
    try {
      const page = await api.messages(peer, cursor);
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
      const msg = await api.sendMessage(peer, body);
      setItems((prev) => [msg, ...(prev ?? [])]);
      setText('');
      events.emit('messages');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '보내지 못했어요.');
    } finally {
      setSending(false);
    }
  };

  return (
    <SafeAreaView style={styles.flex} edges={['bottom']}>
      <Stack.Screen options={{ title: peerInfo?.displayName ?? '' }} />
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
            renderItem={({ item }) => {
              const mine = item.senderId === me?.id;
              return (
                <View style={[styles.bubbleRow, mine ? styles.right : styles.left]}>
                  {item.moment && (
                    <Pressable onPress={() => router.push(`/moments/${item.moment!.id}`)}>
                      <Image source={{ uri: absoluteUrl(item.moment.thumbUrl) }} style={styles.momentThumb} contentFit="cover" />
                      <Text style={[styles.replyLabel, mine && { textAlign: 'right' }]}>{mine ? '사진에 답장했어요' : '내 사진에 답장했어요'}</Text>
                    </Pressable>
                  )}
                  <View style={[styles.bubble, mine ? styles.bubbleMine : styles.bubbleTheirs]}>
                    <Text style={[styles.bubbleText, mine && { color: colors.accentText }]}>{item.text}</Text>
                  </View>
                  <Text style={styles.time}>{clockTime(item.createdAt)}</Text>
                </View>
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
            <Ionicons name="arrow-up" size={20} color={colors.accentText} />
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg },
  list: { padding: 12, gap: 10 },
  bubbleRow: { maxWidth: '78%', gap: 4 },
  left: { alignSelf: 'flex-start', alignItems: 'flex-start' },
  right: { alignSelf: 'flex-end', alignItems: 'flex-end' },
  momentThumb: { width: 120, height: 120, borderRadius: 18, backgroundColor: colors.surface },
  replyLabel: { color: colors.textFaint, fontSize: 12, marginTop: 4 },
  bubble: { paddingHorizontal: 14, paddingVertical: 10, borderRadius: 20 },
  bubbleMine: { backgroundColor: colors.accent, borderBottomRightRadius: 6 },
  bubbleTheirs: { backgroundColor: colors.surfaceHigh, borderBottomLeftRadius: 6 },
  bubbleText: { color: colors.text, fontSize: 15, lineHeight: 21 },
  time: { color: colors.textFaint, fontSize: 11 },
  inputRow: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 10, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.border },
  input: { flex: 1, height: 44, borderRadius: 22, paddingHorizontal: 16, backgroundColor: colors.surface, color: colors.text, fontSize: 15 },
  sendButton: { width: 44, height: 44, borderRadius: 22, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center' },
});
