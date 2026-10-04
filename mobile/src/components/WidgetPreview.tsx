import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Icon } from '@/components/Icon';
import type { WidgetFeed } from '@/lib/api';
import { absoluteUrl } from '@/lib/config';
import { timeAgo } from '@/lib/format';
import { colors } from '@/lib/theme';

/**
 * 홈 화면 위젯을 앱 안에서 똑같이 그려 보여준다.
 * 실제 위젯은 targets/widget/MomentWidget.swift 이고, 배치를 이것과 맞춰 둔다.
 * 사진이 여러 장이면 위젯처럼 양옆을 눌러 넘겨 본다 (오른쪽 위 점은 몇 번째 사진인지).
 */
export function WidgetPreview({ feed, size = 170 }: { feed: WidgetFeed | null; size?: number }) {
  const large = size >= 300;
  const items = feed?.items ?? [];
  // 새 사진이 오면 맨 앞으로 (위젯과 같음)
  const newest = items[0]?.moment.id ?? 0;
  const [paging, setPaging] = useState({ newest, page: 0 });
  const page = paging.newest === newest ? Math.min(paging.page, items.length - 1) : 0;
  const flip = (step: number) => setPaging({ newest, page: (page + step + items.length) % items.length });

  const data = items[page];
  if (!data) {
    return (
      <View style={[styles.frame, styles.center, { width: size, height: size }]}>
        <Text style={styles.emptyEmoji}>📷</Text>
        <Text style={styles.emptyText}>친구가 사진을 보내면{'\n'}여기에 떠요</Text>
      </View>
    );
  }
  const comments = data.comments.slice(large ? -2 : -1);
  const flipWidth = large ? 56 : 34;
  return (
    <View style={[styles.frame, { width: size, height: size }]}>
      <Image source={{ uri: absoluteUrl(data.moment.thumbUrl) }} style={StyleSheet.absoluteFill} contentFit="cover" transition={150} />
      {/* 보낸 사람은 글자만 (위쪽 그늘 + 그림자) */}
      <LinearGradient colors={['rgba(0,0,0,0.35)', 'transparent']} style={styles.topShade} />
      <View style={styles.header}>
        <View style={styles.sender}>
          <Text style={styles.senderName} numberOfLines={1}>
            {data.moment.sender.displayName}
          </Text>
          <Text style={styles.senderTime}>{timeAgo(data.moment.createdAt)}</Text>
        </View>
        {items.length > 1 && (
          <View style={styles.dots} accessibilityLabel={`${items.length}장 중 ${page + 1}번째`}>
            {items.map((m, i) => (
              <View key={m.moment.id} style={[styles.dot, large && styles.dotLarge, i === page && styles.dotOn]} />
            ))}
          </View>
        )}
      </View>
      {/* 이모지 반응은 위젯에 띄우지 않는다 (저장한 디자인: 이모지 자리 숨김) */}
      {comments.length > 0 && (
        <LinearGradient colors={['transparent', 'rgba(0,0,0,0.78)']} style={[styles.bottom, { paddingTop: large ? 48 : 28 }]}>
          {comments.map((c, i) => (
            <Text key={i} style={[styles.comment, large && styles.commentLarge]} numberOfLines={1}>
              <Text style={styles.commentAuthor}>{c.author} </Text>
              {c.text}
            </Text>
          ))}
        </LinearGradient>
      )}
      {items.length > 1 &&
        ([-1, 1] as const).map((step) => (
          <Pressable
            key={step}
            onPress={() => flip(step)}
            style={[styles.flip, { width: flipWidth }, step < 0 ? { left: 0 } : { right: 0 }]}
            accessibilityRole="button"
            accessibilityLabel={step < 0 ? '이전 사진' : '다음 사진'}>
            <Icon name={step < 0 ? 'back' : 'next'} size={large ? 18 : 14} color="rgba(255,255,255,0.75)" />
          </Pressable>
        ))}
    </View>
  );
}

const shadow = { textShadowColor: 'rgba(0,0,0,0.5)', textShadowRadius: 2, textShadowOffset: { width: 0, height: 1 } } as const;

const styles = StyleSheet.create({
  frame: { borderRadius: 24, overflow: 'hidden', backgroundColor: colors.surfaceHigh },
  center: { alignItems: 'center', justifyContent: 'center', gap: 8 },
  emptyEmoji: { fontSize: 28 },
  emptyText: { color: colors.textDim, fontSize: 12, textAlign: 'center', lineHeight: 17 },
  topShade: { position: 'absolute', left: 0, right: 0, top: 0, height: '30%' },
  header: { position: 'absolute', top: 10, left: 10, right: 10, flexDirection: 'row', alignItems: 'center', gap: 6 },
  sender: { flexShrink: 1, flexDirection: 'row', alignItems: 'baseline', gap: 4 },
  senderName: { color: '#fff', fontSize: 12, fontWeight: '700', flexShrink: 1, ...shadow },
  senderTime: { color: 'rgba(255,255,255,0.72)', fontSize: 11, ...shadow },
  dots: { marginLeft: 'auto', flexDirection: 'row', gap: 3 },
  dot: { width: 4, height: 4, borderRadius: 2, backgroundColor: 'rgba(255,255,255,0.45)' },
  dotLarge: { width: 6, height: 6, borderRadius: 3 },
  dotOn: { backgroundColor: '#fff' },
  bottom: { position: 'absolute', left: 0, right: 0, bottom: 0, paddingHorizontal: 10, paddingBottom: 10, gap: 4 },
  comment: { color: '#fff', fontSize: 12 },
  commentLarge: { fontSize: 15 },
  commentAuthor: { fontWeight: '800' },
  // 양옆 가장자리를 누르면 넘어간다 (위젯은 쓸어 넘기기가 안 되고 누르기만 된다)
  flip: { position: 'absolute', top: 0, bottom: 0, alignItems: 'center', justifyContent: 'center' },
});
