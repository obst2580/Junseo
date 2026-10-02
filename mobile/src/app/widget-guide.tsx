import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { useHeaderHeight } from 'expo-router/react-navigation';

import { WidgetPreview } from '@/components/WidgetPreview';
import { api, type WidgetFeed } from '@/lib/api';
import { colors, radius } from '@/lib/theme';

const STEPS = [
  '홈 화면의 빈 곳을 길게 눌러요.',
  '왼쪽 위의 [편집] → [위젯 추가]를 눌러요.',
  '목록에서 junseo를 찾아 작은 크기나 큰 크기를 골라요.',
  '[위젯 추가]를 누르면 끝! 친구가 사진을 보내면 여기에 떠요.',
  '한 친구 사진만 보고 싶으면 위젯을 길게 눌러 [위젯 편집]에서 친구를 골라요.',
];

export default function WidgetGuideScreen() {
  // 헤더가 바탕(그라데이션) 위에 투명하게 떠 있어서 그만큼 내려서 시작한다
  const headerHeight = useHeaderHeight();
  const [feed, setFeed] = useState<WidgetFeed | null>(null);
  useEffect(() => {
    api.widgetFeed().then((d) => setFeed(d ?? null)).catch(() => {});
  }, []);

  return (
    <ScrollView style={[styles.flex, { paddingTop: headerHeight }]} contentContainerStyle={styles.content}>
      <Text style={styles.title}>친구 사진이{'\n'}홈 화면에 바로 떠요</Text>
      <View style={styles.previews}>
        <WidgetPreview feed={feed} size={150} />
        <View style={styles.previewCaption}>
          <Text style={styles.dim}>친구가 남긴 댓글도 사진 아래에 함께 보여요.</Text>
          <Text style={styles.dim}>양옆을 누르면 하루 동안 받은 사진을 5장까지 넘겨 봐요.</Text>
        </View>
      </View>
      <View style={styles.steps}>
        {STEPS.map((s, i) => (
          <View key={i} style={styles.step}>
            <View style={styles.stepNo}>
              <Text style={styles.stepNoText}>{i + 1}</Text>
            </View>
            <Text style={styles.stepText}>{s}</Text>
          </View>
        ))}
      </View>
      <Text style={styles.note}>
        알림을 허용하면 사진이 몇 초 안에 위젯에 떠요. 알림을 끄면 iOS가 정한 주기(보통 15분 안팎)에 맞춰 갱신돼요.
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 20, gap: 24, maxWidth: 560, width: '100%', alignSelf: 'center' },
  title: { color: colors.text, fontSize: 26, fontWeight: '800', lineHeight: 34 },
  previews: { flexDirection: 'row', alignItems: 'center', gap: 16 },
  previewCaption: { flex: 1, gap: 8 },
  dim: { color: colors.textDim, fontSize: 14, lineHeight: 20 },
  steps: { gap: 14, backgroundColor: colors.surface, padding: 18, borderRadius: radius.card },
  step: { flexDirection: 'row', gap: 12, alignItems: 'center' },
  stepNo: { width: 26, height: 26, borderRadius: 13, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center' },
  stepNoText: { color: colors.accentText, fontWeight: '800' },
  stepText: { flex: 1, color: colors.text, fontSize: 15, lineHeight: 21 },
  note: { color: colors.textFaint, fontSize: 13, lineHeight: 19 },
});
