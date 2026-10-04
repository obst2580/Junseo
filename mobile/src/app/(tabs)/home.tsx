import { Image } from 'expo-image';
import { router, useFocusEffect, useNavigation } from 'expo-router';
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, StyleSheet, Text, View, useWindowDimensions } from 'react-native';

import { Icon } from '@/components/Icon';
import { PressScale } from '@/components/PressScale';
import { useTabBarSpace } from '@/components/TabBar';
import { Empty } from '@/components/ui';
import { api, type Moment } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { absoluteUrl } from '@/lib/config';
import { events } from '@/lib/events';
import { timeAgo } from '@/lib/format';
import { colors, radius } from '@/lib/theme';

const COLUMNS = 3;
const GAP = 4;

export default function HistoryScreen() {
  const navigation = useNavigation();
  // 오늘 사진으로 템플릿 만들기 (광고 한 번에 한 장)
  useLayoutEffect(() => {
    navigation.setOptions({
      headerRight: () => (
        <PressScale onPress={() => router.push('/templates')} style={styles.templateButton} accessibilityRole="button" accessibilityLabel="오늘 템플릿 만들기">
          <Icon name="template" size={18} color={colors.accentText} />
          <Text style={styles.templateText}>오늘 템플릿</Text>
        </PressScale>
      ),
    });
  }, [navigation]);
  const tabBarSpace = useTabBarSpace();
  const { me } = useAuth();
  const { width } = useWindowDimensions();
  const tile = (Math.min(width, 640) - GAP * (COLUMNS + 1)) / COLUMNS;

  const [items, setItems] = useState<Moment[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const loadingMore = useRef(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const page = await api.moments();
      setItems(page.items);
      setCursor(page.nextCursor);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadMore = async () => {
    if (!cursor || loadingMore.current) return;
    loadingMore.current = true;
    try {
      const page = await api.moments({ cursor });
      setItems((prev) => [...prev, ...page.items]);
      setCursor(page.nextCursor);
    } finally {
      loadingMore.current = false;
    }
  };

  useFocusEffect(
    useCallback(() => {
      reload().catch(() => {});
    }, [reload]),
  );
  useEffect(() => events.on('moments', () => void reload().catch(() => {})), [reload]);

  return (
    <View style={styles.flex}>
      {loading && items.length === 0 ? (
        <ActivityIndicator color={colors.accent} style={{ marginTop: 40 }} />
      ) : (
        <FlatList
          data={items}
          keyExtractor={(m) => String(m.id)}
          numColumns={COLUMNS}
          contentContainerStyle={[styles.grid, { paddingBottom: tabBarSpace + GAP }]}
          columnWrapperStyle={{ gap: GAP }}
          onEndReached={loadMore}
          onEndReachedThreshold={0.5}
          ListEmptyComponent={<Empty icon="images" title={'아직 주고받은 사진이 없어요.\n첫 사진을 찍어 보내 보세요!'} />}
          renderItem={({ item }) => (
            <PressScale onPress={() => router.push(`/moments/${item.id}`)} style={{ width: tile, height: tile }}>
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
            </PressScale>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  templateButton: {
    marginRight: 16,
    height: 34,
    paddingHorizontal: 12,
    borderRadius: radius.button,
    backgroundColor: colors.accent,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  templateText: { color: colors.accentText, fontSize: 14, fontWeight: '800' },
  grid: { padding: GAP, gap: GAP, alignSelf: 'center', width: '100%', maxWidth: 640 },
  // 사진 모서리는 디자인의 「사진 모서리」에 비례 (디자인 랩: × 0.32)
  thumb: { flex: 1, borderRadius: Math.round(radius.photo * 0.32), backgroundColor: colors.surface },
  meta: { position: 'absolute', left: 6, bottom: 6, maxWidth: '86%', paddingHorizontal: 6, paddingVertical: 2, borderRadius: 999, backgroundColor: 'rgba(0,0,0,0.5)' },
  metaText: { color: '#fff', fontSize: 11, fontWeight: '700' },
  activity: { position: 'absolute', top: 6, right: 6, paddingHorizontal: 6, paddingVertical: 2, borderRadius: 999, backgroundColor: 'rgba(0,0,0,0.5)' },
  activityText: { color: '#fff', fontSize: 11, fontWeight: '700' },
});
