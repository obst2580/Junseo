import { Image } from 'expo-image';
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useHeaderHeight } from 'expo-router/react-navigation';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, KeyboardAvoidingView, Platform, Pressable, StyleSheet, Text } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ChatBubble } from '@/components/ChatBubble';
import { ChatComposer } from '@/components/ChatComposer';
import { api, type Message, type UserSummary } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { isLocal, useChatThread, type LocalMessage } from '@/lib/chatThread';
import { absoluteUrl } from '@/lib/config';
import { events } from '@/lib/events';
import { colors, radius } from '@/lib/theme';

// 읽음(readAt)이나 글이 바뀌었을 때만 다시 그린다
const sameMessage = (a: Message, b: Message) => a.readAt === b.readAt && a.text === b.text && a.moment?.id === b.moment?.id;

export default function ChatScreen() {
  // 헤더가 바탕(그라데이션) 위에 투명하게 떠 있어서 그만큼 내려서 시작한다
  const headerHeight = useHeaderHeight();
  const { peerId } = useLocalSearchParams<{ peerId: string }>();
  const peer = Number(peerId);
  const { me } = useAuth();
  const [peerInfo, setPeerInfo] = useState<UserSummary | null>(null);

  const { rows, loadOlder, send, retry } = useChatThread<Message>({
    fetchPage: (cursor) => api.messages(peer, cursor),
    sendText: (text) => api.sendMessage(peer, text),
    // 상대가 보낸 안 읽은 메시지가 있으면 읽음 처리 (목록·탭 배지만 다시 불러온다)
    afterFetch: async (page) => {
      if (page.some((m) => m.senderId === peer && !m.readAt)) {
        await api.markRead(peer).catch(() => {});
        events.emit('unread');
      }
    },
    same: sameMessage,
    matches: (s) => (s.type === 'message' || s.type === 'read') && s.peerId === peer,
  });

  useEffect(() => {
    api
      .conversations()
      .then((r) => setPeerInfo(r.items.find((c) => c.peer.id === peer)?.peer ?? null))
      .catch(() => {});
    api
      .friends()
      .then((r) => setPeerInfo((p) => p ?? r.friends.find((f) => f.id === peer) ?? null))
      .catch(() => {});
  }, [peer]);

  const renderItem = useCallback(
    ({ item }: { item: Message | LocalMessage }) => {
      if (isLocal(item)) {
        return <ChatBubble mine text={item.text} createdAt={item.createdAt} unread={0} status={item.status} reason={item.reason} onRetry={() => retry(item.localId)} />;
      }
      const mine = item.senderId === me?.id;
      // 상대가 아직 안 읽은 내 메시지에는 1
      return (
        <ChatBubble mine={mine} text={item.text} createdAt={item.createdAt} unread={mine && !item.readAt ? 1 : 0}>
          {item.moment && (
            <Pressable onPress={() => router.push(`/moments/${item.moment!.id}`)}>
              <Image source={{ uri: absoluteUrl(item.moment.thumbUrl) }} style={styles.momentThumb} contentFit="cover" />
              <Text style={[styles.replyLabel, mine && { textAlign: 'right' }]}>{mine ? '사진에 답장했어요' : '내 사진에 답장했어요'}</Text>
            </Pressable>
          )}
        </ChatBubble>
      );
    },
    [me?.id, retry],
  );

  return (
    <SafeAreaView style={[styles.flex, { paddingTop: headerHeight }]} edges={['bottom']}>
      <Stack.Screen options={{ title: peerInfo?.displayName ?? '' }} />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.flex} keyboardVerticalOffset={90}>
        {rows === null ? (
          <ActivityIndicator color={colors.accent} style={{ marginTop: 40 }} />
        ) : (
          <FlatList
            inverted
            data={rows}
            keyExtractor={keyOf}
            renderItem={renderItem}
            contentContainerStyle={styles.list}
            onEndReached={loadOlder}
            onEndReachedThreshold={0.3}
            // 긴 대화도 가볍게: 화면 근처만 그리고, 스크롤 중에도 키보드를 끌어 내릴 수 있게
            initialNumToRender={20}
            maxToRenderPerBatch={12}
            windowSize={9}
            keyboardDismissMode="interactive"
            keyboardShouldPersistTaps="handled"
          />
        )}
        <ChatComposer onSend={send} />
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const keyOf = (m: Message | LocalMessage) => (isLocal(m) ? m.localId : String(m.id));

const styles = StyleSheet.create({
  flex: { flex: 1 },
  list: { padding: 12, gap: 10 },
  momentThumb: { width: 120, height: 120, borderRadius: Math.round(radius.photo * 0.4), backgroundColor: colors.surface },
  replyLabel: { color: colors.textFaint, fontSize: 12, marginTop: 4 },
});
