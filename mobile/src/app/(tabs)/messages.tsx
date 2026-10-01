import { router, useFocusEffect, useNavigation } from 'expo-router';
import { useCallback, useEffect, useLayoutEffect, useState, type ReactNode } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';

import { PressScale } from '@/components/PressScale';
import { Icon } from '@/components/Icon';
import { GroupAvatar } from '@/components/GroupAvatar';
import { GroupCreateSheet } from '@/components/GroupCreateSheet';
import { useTabBarSpace } from '@/components/TabBar';
import { Avatar, Empty } from '@/components/ui';
import { api, type Conversation, type GroupConversation, type UserSummary } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { events } from '@/lib/events';
import { timeAgo } from '@/lib/format';
import { groupTitle, linkKey } from '@/lib/groups';
import { colors } from '@/lib/theme';

// 1:1 과 단챗을 한 목록에 최근 대화 순으로 섞는다.
type Row = { key: string; at: string } & ({ kind: 'peer'; c: Conversation } | { kind: 'group'; g: GroupConversation });

export default function ConversationsScreen() {
  const tabBarSpace = useTabBarSpace();
  const { me } = useAuth();
  const navigation = useNavigation();
  const [rows, setRows] = useState<Row[] | null>(null);
  // 단챗 만들기: 열 때 친구 목록과 서로 친구인 쌍을 가져온다
  const [creating, setCreating] = useState<{ friends: UserSummary[]; links: Set<string> } | null>(null);

  const load = useCallback(() => {
    api
      .conversations()
      .then((r) =>
        setRows(
          [
            ...r.items.map((c): Row => ({ kind: 'peer', key: `p${c.peer.id}`, at: c.lastMessage.createdAt, c })),
            ...r.groups.map((g): Row => ({ kind: 'group', key: `g${g.group.id}`, at: g.lastMessage?.createdAt ?? g.group.createdAt, g })),
          ].sort((a, b) => Date.parse(b.at) - Date.parse(a.at)),
        ),
      )
      .catch(() => setRows((prev) => prev ?? []));
  }, []);
  useFocusEffect(load);
  useEffect(() => {
    const offMessages = events.on('messages', load);
    const offUnread = events.on('unread', load);
    return () => {
      offMessages();
      offUnread();
    };
  }, [load]);

  const openCreate = useCallback(() => {
    Promise.all([api.friends(), api.friendLinks()])
      .then(([f, l]) => setCreating({ friends: f.friends, links: new Set(l.pairs.map(([a, b]) => linkKey(a, b))) }))
      .catch(() => {});
  }, []);

  useLayoutEffect(() => {
    navigation.setOptions({
      headerRight: () => (
        <Pressable onPress={openCreate} hitSlop={10} style={styles.newGroup} accessibilityRole="button" accessibilityLabel="단챗 만들기">
          <Icon name="chats" color={colors.text} />
          <View style={styles.plus}>
            <Icon name="plus" size={12} strokeWidth={3} color={colors.accentText} />
          </View>
        </Pressable>
      ),
    });
  }, [navigation, openCreate]);

  return (
    <>
      <FlatList
        style={styles.flex}
        data={rows ?? []}
        keyExtractor={(r) => r.key}
        contentContainerStyle={{ paddingBottom: tabBarSpace }}
        ListEmptyComponent={rows ? <Empty icon="chats" title={'친구 사진에 답장하면\n여기에서 이어서 이야기할 수 있어요.'} /> : null}
        renderItem={({ item }) => (item.kind === 'peer' ? <PeerRow c={item.c} meId={me?.id} /> : <GroupRow g={item.g} meId={me?.id} />)}
      />
      {creating && (
        <GroupCreateSheet
          friends={creating.friends}
          links={creating.links}
          onClose={() => setCreating(null)}
          onCreated={(group) => {
            setCreating(null);
            events.emit('messages');
            router.push(`/messages/group/${group.id}`);
          }}
        />
      )}
    </>
  );
}

function PeerRow({ c, meId }: { c: Conversation; meId?: number }) {
  const last = c.lastMessage;
  const preview = last.moment && !last.text ? '사진에 답장' : last.text;
  return (
    <ConversationRow
      avatar={<Avatar id={c.peer.id} name={c.peer.displayName} size={48} />}
      name={c.peer.displayName}
      preview={`${last.senderId === meId ? '나: ' : ''}${last.moment ? '📷 ' : ''}${preview}`}
      at={last.createdAt}
      unread={c.unreadCount}
      onPress={() => router.push(`/messages/${c.peer.id}`)}
    />
  );
}

function GroupRow({ g, meId }: { g: GroupConversation; meId?: number }) {
  const last = g.lastMessage;
  const others = g.group.members.filter((m) => m.id !== meId);
  const sender = last && (last.senderId === meId ? '나' : (g.group.members.find((m) => m.id === last.senderId)?.displayName ?? '나간 친구'));
  return (
    <ConversationRow
      avatar={<GroupAvatar members={others} />}
      name={groupTitle(g.group, meId)}
      count={g.group.members.length}
      preview={last ? `${sender}: ${last.text}` : '단챗을 만들었어요'}
      at={last?.createdAt ?? g.group.createdAt}
      unread={g.unreadCount}
      onPress={() => router.push(`/messages/group/${g.group.id}`)}
    />
  );
}

function ConversationRow({
  avatar,
  name,
  count,
  preview,
  at,
  unread,
  onPress,
}: {
  avatar: ReactNode;
  name: string;
  count?: number;
  preview: string;
  at: string;
  unread: number;
  onPress: () => void;
}) {
  return (
    <PressScale style={styles.row} onPress={onPress}>
      {avatar}
      <View style={styles.body}>
        <View style={styles.nameRow}>
          <Text style={styles.name} numberOfLines={1}>
            {name}
          </Text>
          {count !== undefined && <Text style={styles.count}>{count}</Text>}
        </View>
        <Text style={[styles.preview, unread > 0 && styles.previewUnread]} numberOfLines={1}>
          {preview}
        </Text>
      </View>
      {/* 시간 아래 안 읽은 수. 배지가 있든 없든 시간이 같은 오른쪽 끝에 맞춰진다. */}
      <View style={styles.side}>
        <Text style={styles.time}>{timeAgo(at)}</Text>
        {unread > 0 && (
          <View style={styles.badge}>
            <Text style={styles.badgeText}>{unread}</Text>
          </View>
        )}
      </View>
    </PressScale>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  newGroup: { marginRight: 16, width: 32, height: 32, alignItems: 'center', justifyContent: 'center' },
  plus: {
    position: 'absolute',
    right: -2,
    top: 0,
    width: 15,
    height: 15,
    borderRadius: 8,
    backgroundColor: colors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 12 },
  body: { flex: 1, gap: 3 },
  nameRow: { flexDirection: 'row', alignItems: 'baseline', gap: 6 },
  side: { alignSelf: 'stretch', alignItems: 'flex-end', gap: 6, paddingTop: 3, minWidth: 48 },
  name: { flexShrink: 1, color: colors.text, fontSize: 16, fontWeight: '700' },
  count: { color: colors.textFaint, fontSize: 14, fontWeight: '600' },
  time: { color: colors.textFaint, fontSize: 12 },
  preview: { color: colors.textDim, fontSize: 14 },
  previewUnread: { color: colors.text, fontWeight: '600' },
  badge: { minWidth: 22, height: 22, borderRadius: 11, paddingHorizontal: 6, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center' },
  badgeText: { color: colors.accentText, fontSize: 12, fontWeight: '800' },
});
