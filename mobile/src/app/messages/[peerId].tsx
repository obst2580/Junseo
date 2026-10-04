import { Image } from 'expo-image';
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useHeaderHeight } from 'expo-router/react-navigation';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, KeyboardAvoidingView, Platform, Pressable, StyleSheet, Text } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ChatBubble } from '@/components/ChatBubble';
import { ChatComposer } from '@/components/ChatComposer';
import { IconButton } from '@/components/ui';
import { api, type Message, type UserSummary } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { runAt } from '@/lib/chatRuns';
import { isLocal, useChatThread, type LocalMessage } from '@/lib/chatThread';
import { dmKey, dmSource, knownPeer, rememberPeer } from '@/lib/chats';
import { absoluteUrl } from '@/lib/config';
import { events } from '@/lib/events';
import { useOpenChat } from '@/lib/push';
import { useSafety } from '@/lib/safety';
import { colors, radius } from '@/lib/theme';

export default function ChatScreen() {
  // 헤더가 바탕(그라데이션) 위에 투명하게 떠 있어서 그만큼 내려서 시작한다
  const headerHeight = useHeaderHeight();
  const { peerId } = useLocalSearchParams<{ peerId: string }>();
  const peer = Number(peerId);
  const { me } = useAuth();
  // 목록에서 들어왔으면 이름을 이미 안다
  const [peerInfo, setPeerInfo] = useState<UserSummary | null>(() => knownPeer(peer));
  useOpenChat(dmKey(peer));
  // 신고 · 차단. 차단하면 이 대화는 목록에서 사라지므로 닫는다
  const safety = useSafety(() => (router.canGoBack() ? router.back() : router.replace('/messages')));
  const { menu: safetyMenu } = safety;
  const peerRef = useRef(peerInfo);
  useEffect(() => {
    peerRef.current = peerInfo;
  });
  // 상대 말 꾹 → 메시지 신고 · 차단 (모든 말풍선이 같은 함수를 쓴다)
  const messageMenu = useCallback(
    (id: number) => {
      const p = peerRef.current;
      if (p) safetyMenu({ kind: 'message', targetId: id, user: p, what: '메시지' });
    },
    [safetyMenu],
  );

  const { rows, loadOlder, send, retry, discard } = useChatThread<Message>({
    key: dmKey(peer),
    source: dmSource(peer),
    // 상대가 보낸 안 읽은 메시지가 있으면 읽음 처리 (목록·탭 배지만 다시 불러온다)
    afterFetch: async (page) => {
      if (page.some((m) => m.senderId === peer && !m.readAt)) {
        await api.markRead(peer).catch(() => {});
        events.emit('unread');
      }
    },
    matches: (s) => (s.type === 'message' || s.type === 'read') && s.peerId === peer,
  });

  // 알림을 눌러 바로 들어왔을 때처럼 이름을 모르면 찾아 온다 (친구가 아니게 된 상대는 대화 목록에만 있다)
  useEffect(() => {
    if (knownPeer(peer)) return;
    const found = (p: UserSummary | null | undefined) => {
      if (!p) return;
      rememberPeer(p);
      setPeerInfo((cur) => cur ?? p);
    };
    api.friends().then((r) => found(r.friends.find((f) => f.id === peer))).catch(() => {});
    api.conversations().then((r) => found(r.items.find((c) => c.peer.id === peer)?.peer)).catch(() => {});
  }, [peer]);

  const renderItem = useCallback(
    ({ item, index }: { item: Message | LocalMessage; index: number }) => {
      // 같은 사람 · 같은 분에 이어 보낸 말은 붙이고 시간은 마지막 말에만
      const { joinAbove, last } = runAt(rows, index, me?.id);
      if (isLocal(item)) {
        return (
          <ChatBubble
            mine
            text={item.text}
            createdAt={item.createdAt}
            unread={0}
            joinAbove={joinAbove}
            showTime={last}
            status={item.status}
            reason={item.reason}
            onRetry={() => retry(item.localId)}
            onDiscard={() => discard(item.localId)}
          />
        );
      }
      const mine = item.senderId === me?.id;
      // 상대가 아직 안 읽은 내 메시지에는 1
      return (
        <ChatBubble
          mine={mine}
          text={item.text}
          createdAt={item.createdAt}
          unread={mine && !item.readAt ? 1 : 0}
          joinAbove={joinAbove}
          showTime={last}
          onLongPress={mine ? undefined : messageMenu}
          pressKey={item.id}>
          {item.moment && (
            <Pressable onPress={() => router.push(`/moments/${item.moment!.id}`)}>
              <Image source={{ uri: absoluteUrl(item.moment.thumbUrl) }} style={styles.momentThumb} contentFit="cover" />
              <Text style={[styles.replyLabel, mine && { textAlign: 'right' }]}>{mine ? '사진에 답장했어요' : '내 사진에 답장했어요'}</Text>
            </Pressable>
          )}
        </ChatBubble>
      );
    },
    [me?.id, retry, discard, rows, messageMenu],
  );

  return (
    <SafeAreaView style={[styles.flex, { paddingTop: headerHeight }]} edges={['bottom']}>
      <Stack.Screen
        options={{
          title: peerInfo?.displayName ?? '',
          headerRight: peerInfo
            ? () => <IconButton icon="more" size={22} onPress={() => safety.menu({ kind: 'user', user: peerInfo })} label="신고 · 차단" />
            : undefined,
        }}
      />
      {/* 이 화면은 화면 맨 위(투명 헤더 밑)부터 시작하므로 키보드 보정값은 0 이다 */}
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.flex}>
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
      {safety.element}
    </SafeAreaView>
  );
}

const keyOf = (m: Message | LocalMessage) => (isLocal(m) ? m.localId : String(m.id));

const styles = StyleSheet.create({
  flex: { flex: 1 },
  // 말풍선끼리 간격은 말풍선이 스스로 띄운다 (ChatBubble)
  list: { paddingHorizontal: 12, paddingVertical: 8 },
  momentThumb: { width: 120, height: 120, borderRadius: Math.round(radius.photo * 0.4), backgroundColor: colors.surface },
  replyLabel: { color: colors.textFaint, fontSize: 12, marginTop: 4 },
});
