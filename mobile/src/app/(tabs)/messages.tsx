import { router, useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';

import { Avatar, Empty } from '@/components/ui';
import { api, type Conversation } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { events } from '@/lib/events';
import { timeAgo } from '@/lib/format';
import { colors } from '@/lib/theme';

export default function ConversationsScreen() {
  const { me } = useAuth();
  const [items, setItems] = useState<Conversation[] | null>(null);

  const load = useCallback(() => {
    api.conversations().then((r) => setItems(r.items)).catch(() => setItems((prev) => prev ?? []));
  }, []);
  useFocusEffect(load);
  useEffect(() => events.on('messages', load), [load]);

  return (
    <FlatList
      style={styles.flex}
      data={items ?? []}
      keyExtractor={(c) => String(c.peer.id)}
      ListEmptyComponent={items ? <Empty icon="chatbubbles-outline" title={'친구 사진에 답장하면\n여기에서 이어서 이야기할 수 있어요.'} /> : null}
      renderItem={({ item }) => {
        const last = item.lastMessage;
        const preview = last.moment && !last.text ? '사진에 답장' : last.text;
        return (
          <Pressable style={({ pressed }) => [styles.row, pressed && { backgroundColor: colors.surface }]} onPress={() => router.push(`/messages/${item.peer.id}`)}>
            <Avatar id={item.peer.id} name={item.peer.displayName} size={48} />
            <View style={styles.body}>
              <Text style={styles.name}>{item.peer.displayName}</Text>
              <Text style={[styles.preview, item.unreadCount > 0 && styles.previewUnread]} numberOfLines={1}>
                {last.senderId === me?.id ? '나: ' : ''}
                {last.moment ? '📷 ' : ''}
                {preview}
              </Text>
            </View>
            {/* 시간 아래 안 읽은 수. 배지가 있든 없든 시간이 같은 오른쪽 끝에 맞춰진다. */}
            <View style={styles.side}>
              <Text style={styles.time}>{timeAgo(last.createdAt)}</Text>
              {item.unreadCount > 0 && (
                <View style={styles.badge}>
                  <Text style={styles.badgeText}>{item.unreadCount}</Text>
                </View>
              )}
            </View>
          </Pressable>
        );
      }}
    />
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 16, paddingVertical: 12 },
  body: { flex: 1, gap: 3 },
  side: { alignSelf: 'stretch', alignItems: 'flex-end', gap: 6, paddingTop: 3, minWidth: 48 },
  name: { color: colors.text, fontSize: 16, fontWeight: '700' },
  time: { color: colors.textFaint, fontSize: 12 },
  preview: { color: colors.textDim, fontSize: 14 },
  previewUnread: { color: colors.text, fontWeight: '600' },
  badge: { minWidth: 22, height: 22, borderRadius: 11, paddingHorizontal: 6, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center' },
  badgeText: { color: colors.accentText, fontSize: 12, fontWeight: '800' },
});
