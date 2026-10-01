import { router } from 'expo-router';
import { useHeaderHeight } from 'expo-router/react-navigation';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { PressScale } from '@/components/PressScale';
import { TemplateCanvas } from '@/components/TemplateCanvas';
import { TEMPLATES } from '@/lib/templates';
import { colors, radius } from '@/lib/theme';

const CARD = 150;

/** 템플릿 고르기. 오늘 찍은 사진·받은 사진을 골라 넣고, 광고를 한 번 보면 한 장 만들어진다. */
export default function TemplatesScreen() {
  const headerHeight = useHeaderHeight();
  return (
    <ScrollView style={[styles.flex, { paddingTop: headerHeight }]} contentContainerStyle={styles.content}>
      <Text style={styles.lead}>오늘 찍은 사진과 받은 사진으로 꾸며요.{'\n'}광고를 한 번 보면 한 장 만들 수 있어요.</Text>
      <View style={styles.grid}>
        {TEMPLATES.map((t) => (
          <PressScale
            key={t.id}
            onPress={() => router.push(`/templates/${t.id}`)}
            style={styles.card}
            accessibilityRole="button"
            accessibilityLabel={`${t.name} 템플릿`}>
            <View style={styles.thumb}>
              <TemplateCanvas template={t} photos={[]} width={CARD} />
            </View>
            <Text style={styles.name}>{t.name}</Text>
            <Text style={styles.count}>사진 {t.slots.length}장</Text>
          </PressScale>
        ))}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 16, gap: 18, paddingBottom: 40 },
  lead: { color: colors.textDim, fontSize: 15, lineHeight: 22 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 16 },
  card: { width: CARD, gap: 6 },
  thumb: { borderRadius: radius.card * 0.6, overflow: 'hidden', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.28)', boxShadow: '0 10px 24px -8px rgba(0,0,0,0.75)' },
  name: { color: colors.text, fontSize: 15, fontWeight: '700' },
  count: { color: colors.textFaint, fontSize: 13 },
});
