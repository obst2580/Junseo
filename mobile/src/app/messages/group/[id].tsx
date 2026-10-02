import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useHeaderHeight } from 'expo-router/react-navigation';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, KeyboardAvoidingView, Platform, Pressable, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ChatBubble } from '@/components/ChatBubble';
import { ChatComposer } from '@/components/ChatComposer';
import { Icon } from '@/components/Icon';
import { api, type GroupChat, type GroupMessage, type UserSummary } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { isLocal, useChatThread, type LocalMessage } from '@/lib/chatThread';
import { groupKey, groupSource, knownGroup, rememberGroup } from '@/lib/chats';
import { events } from '@/lib/events';
import { groupTitle } from '@/lib/groups';
import { useOpenChat } from '@/lib/push';
import { colors } from '@/lib/theme';

export default function GroupChatScreen() {
  // 헤더가 바탕(그라데이션) 위에 투명하게 떠 있어서 그만큼 내려서 시작한다
  const headerHeight = useHeaderHeight();
  const { id } = useLocalSearchParams<{ id: string }>();
  const groupId = Number(id);
  const { me } = useAuth();
  // 목록에서 들어왔으면 이름·멤버를 이미 안다. 멤버가 바뀌었을 수 있으니 뒤에서 한 번 더 받는다.
  const [group, setGroup] = useState<GroupChat | null>(() => knownGroup(groupId));
  // 읽음 처리를 보낸 마지막 메시지. 같은 메시지로 여러 번 보내지 않는다.
  const markedUpTo = useRef(0);
  useOpenChat(groupKey(groupId));

  const { rows, loadOlder, send, retry, discard } = useChatThread<GroupMessage>({
    key: groupKey(groupId),
    source: groupSource(groupId),
    // 남이 보낸 새 메시지가 있을 때만 읽음 처리 (내가 보내면 서버가 이미 읽은 것으로 친다)
    afterFetch: async (page) => {
      const newest = page.find((m) => m.senderId !== me?.id)?.id ?? 0;
      if (newest > markedUpTo.current) {
        markedUpTo.current = newest;
        await api.markGroupRead(groupId).catch(() => {});
        events.emit('unread');
      }
    },
    matches: (s) => (s.type === 'group-message' || s.type === 'group-read') && s.groupId === groupId,
  });

  useEffect(() => {
    api
      .group(groupId)
      .then((g) => {
        rememberGroup(g);
        setGroup(g);
      })
      .catch(() => {});
  }, [groupId]);

  // 같은 사람 객체를 계속 써야 말풍선이 다시 그려지지 않는다
  const people = useMemo(() => new Map<number, UserSummary>(group?.members.map((m) => [m.id, m])), [group]);

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

  const renderItem = useCallback(
    ({ item, index }: { item: GroupMessage | LocalMessage; index: number }) => {
      if (isLocal(item)) {
        return (
          <ChatBubble
            mine
            text={item.text}
            createdAt={item.createdAt}
            unread={0}
            status={item.status}
            reason={item.reason}
            onRetry={() => retry(item.localId)}
            onDiscard={() => discard(item.localId)}
          />
        );
      }
      const mine = item.senderId === me?.id;
      // 같은 사람이 이어서 보낸 말에는 얼굴·이름을 한 번만 (목록이 뒤집혀 있어서 바로 앞 말은 index + 1)
      const older = rows?.[index + 1];
      const firstOfRun = !older || isLocal(older) || older.senderId !== item.senderId;
      const sender = people.get(item.senderId) ?? LEFT;
      return <ChatBubble mine={mine} text={item.text} createdAt={item.createdAt} unread={item.unreadCount} sender={firstOfRun ? sender : null} />;
    },
    [me?.id, people, retry, discard, rows],
  );

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

const LEFT: UserSummary = { id: 0, displayName: '나간 친구' };
const keyOf = (m: GroupMessage | LocalMessage) => (isLocal(m) ? m.localId : String(m.id));

const styles = StyleSheet.create({
  flex: { flex: 1 },
  list: { padding: 12, gap: 10 },
});
