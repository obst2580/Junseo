import { Image } from 'expo-image';
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { Empty } from '@/components/ui';
import { api, type Moment, type UserSummary } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { absoluteUrl } from '@/lib/config';
import { events } from '@/lib/events';
import { timeAgo } from '@/lib/format';
import { colors, radius } from '@/lib/theme';

const COLUMNS = 3;
const GAP = 4;

export default function HistoryScreen() {
  const { me } = useAuth();
  const { width } = useWindowDimensions();
  const tile = (Math.min(width, 640) - GAP * (COLUMNS + 1)) / COLUMNS;

  const [friends, setFriends] = useState<UserSummary[]>([]);
  const [filter, setFilter] = useState<number | null>(null);
  const [items, setItems] = useState<Moment[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const loadingMore = useRef(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const page = await api.moments({ userId: filter });
      setItems(page.items);
      setCursor(page.nextCursor);
    } finally {
      setLoading(false);
    }
  }, [filter]);

  const loadMore = async () => {
    if (!cursor || loadingMore.current) return;
    loadingMore.current = true;
    try {
      const page = await api.moments({ userId: filter, cursor });
      setItems((prev) => [...prev, ...page.items]);
      setCursor(page.nextCursor);
    } finally {
      loadingMore.current = false;
    }
  };

  useFocusEffect(
    useCallback(() => {
      reload().catch(() => {});
      api.friends().then((r) => setFriends(r.friends)).catch(() => {});
    }, [reload]),
  );
  useEffect(() => events.on('moments', () => void reload().catch(() => {})), [reload]);

  const chips: { id: number | null; label: string }[] = [
    { id: null, label: '전체' },
    ...(me ? [{ id: me.id, label: '나' }] : []),
    ...friends.map((f) => ({ id: f.id, label: f.displayName })),
  ];

  return (
    <View style={styles.flex}>
      <View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
          {chips.map((c) => (
            <Pressable key={String(c.id)} onPress={() => setFilter(c.id)} style={[styles.chip, filter === c.id && styles.chipActive]}>
              <Text style={[styles.chipText, filter === c.id && styles.chipTextActive]}>{c.label}</Text>
            </Pressable>
          ))}
        </ScrollView>
      </View>
      {loading && items.length === 0 ? (
        <ActivityIndicator color={colors.accent} style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          data={items}
          keyExtractor={(m) => String(m.id)}
          numColumns={COLUMNS}
          contentContainerStyle={styles.grid}
          columnWrapperStyle={{ gap: GAP }}
          onEndReached={loadMore}
          onEndReachedThreshold={0.5}
          ListEmptyComponent={<Empty icon="images-outline" title={'아직 주고받은 사진이 없어요.\n첫 사진을 찍어 보내 보세요!'} />}
          renderItem={({ item }) => (
            <Pressable onPress={() => router.push(`/moments/${item.id}`)} style={{ width: tile, height: tile }}>
              <Image source={{ uri: absoluteUrl(item.thumbUrl) }} style={styles.thumb} contentFit="cover" transition={120} />
              <View style={styles.meta}>
                <Text style={styles.metaText} numberOfLines={1}>
                  {item.sender.id === me?.id ? '나' : item.sender.displayName} · {timeAgo(item.createdAt)}
                </Text>
              </View>
              {(item.reactions.length > 0 || item.commentCount > 0) && (
                <View style={styles.activity}>
                  <Text style={styles.activityText}>
                    {item.reactions.slice(0, 2).map((r) => r.emoji).join('')}
                    {item.commentCount > 0 ? ` 💬${item.commentCount}` : ''}
                  </Text>
                </View>
              )}
            </Pressable>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg },
  chips: { paddingHorizontal: 12, paddingVertical: 10, gap: 8 },
  chip: { paddingHorizontal: 14, height: 34, justifyContent: 'center', borderRadius: radius.pill, backgroundColor: colors.surfaceHigh },
  chipActive: { backgroundColor: colors.text },
  chipText: { color: colors.text, fontSize: 14, fontWeight: '600' },
  chipTextActive: { color: colors.bg },
  grid: { padding: GAP, gap: GAP, alignSelf: 'center', width: '100%', maxWidth: 640 },
  thumb: { flex: 1, borderRadius: 14, backgroundColor: colors.surface },
  meta: { position: 'absolute', left: 6, bottom: 6, right: 6 },
  metaText: { color: '#fff', fontSize: 11, fontWeight: '700', textShadowColor: 'rgba(0,0,0,0.7)', textShadowRadius: 4 },
  activity: { position: 'absolute', top: 6, right: 6, paddingHorizontal: 6, paddingVertical: 2, borderRadius: 999, backgroundColor: 'rgba(0,0,0,0.5)' },
  activityText: { color: '#fff', fontSize: 11, fontWeight: '700' },
});
