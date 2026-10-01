import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, Text, View } from 'react-native';

import type { WidgetLatest } from '@/lib/api';
import { absoluteUrl } from '@/lib/config';
import { timeAgo } from '@/lib/format';
import { colors } from '@/lib/theme';

/**
 * 홈 화면 위젯을 앱 안에서 똑같이 그려 보여준다.
 * 실제 위젯은 targets/widget/MomentWidget.swift 이고, 배치를 이것과 맞춰 둔다.
 */
export function WidgetPreview({ data, size = 170 }: { data: WidgetLatest | null; size?: number }) {
  const large = size >= 300;
  if (!data) {
    return (
      <View style={[styles.frame, styles.center, { width: size, height: size }]}>
        <Text style={styles.emptyEmoji}>📷</Text>
        <Text style={styles.emptyText}>친구가 사진을 보내면{'\n'}여기에 떠요</Text>
      </View>
    );
  }
  const comments = data.comments.slice(large ? -2 : -1);
  return (
    <View style={[styles.frame, { width: size, height: size }]}>
      <Image source={{ uri: absoluteUrl(data.moment.thumbUrl) }} style={StyleSheet.absoluteFill} contentFit="cover" transition={150} />
      {/* 보낸 사람은 글자만 (위쪽 그늘 + 그림자) */}
      <LinearGradient colors={['rgba(0,0,0,0.35)', 'transparent']} style={styles.topShade} />
      <View style={styles.sender}>
        <Text style={styles.senderName} numberOfLines={1}>
          {data.moment.sender.displayName}
        </Text>
        <Text style={styles.senderTime}>{timeAgo(data.moment.createdAt)}</Text>
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
    </View>
  );
}

const styles = StyleSheet.create({
  frame: { borderRadius: 24, overflow: 'hidden', backgroundColor: colors.surfaceHigh },
  center: { alignItems: 'center', justifyContent: 'center', gap: 8 },
  emptyEmoji: { fontSize: 28 },
  emptyText: { color: colors.textDim, fontSize: 12, textAlign: 'center', lineHeight: 17 },
  topShade: { position: 'absolute', left: 0, right: 0, top: 0, height: '30%' },
  sender: { position: 'absolute', top: 10, left: 10, maxWidth: '80%', flexDirection: 'row', alignItems: 'baseline', gap: 4 },
  senderName: { color: '#fff', fontSize: 12, fontWeight: '700', flexShrink: 1, textShadowColor: 'rgba(0,0,0,0.5)', textShadowRadius: 2, textShadowOffset: { width: 0, height: 1 } },
  senderTime: { color: 'rgba(255,255,255,0.72)', fontSize: 11, textShadowColor: 'rgba(0,0,0,0.5)', textShadowRadius: 2, textShadowOffset: { width: 0, height: 1 } },
  bottom: { position: 'absolute', left: 0, right: 0, bottom: 0, paddingHorizontal: 10, paddingBottom: 10, gap: 4 },
  comment: { color: '#fff', fontSize: 12 },
  commentLarge: { fontSize: 15 },
  commentAuthor: { fontWeight: '800' },
});
