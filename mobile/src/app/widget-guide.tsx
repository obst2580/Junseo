import { router, useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { AppState, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useHeaderHeight } from 'expo-router/react-navigation';

import { Button } from '@/components/ui';
import { WidgetPreview } from '@/components/WidgetPreview';
import { api, type WidgetFeed } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { installedWidgets, markWidgetSetupDone } from '@/lib/homeWidget';
import { colors, radius } from '@/lib/theme';
import { widgetBridge } from '@/lib/widgetBridge';

const STEPS = Platform.OS === 'android' ? [
  '홈 화면의 빈 곳을 길게 눌러요.',
  '[위젯] 목록에서 junseo 사진을 찾아 홈 화면에 놓아요.',
  '위젯의 테두리를 드래그해 작은 크기나 큰 크기로 조절해요.',
  '오른쪽 아래 톱니바퀴를 눌러 모든 친구 또는 한 친구를 골라요.',
] : [
  '홈 화면의 빈 곳을 길게 눌러요.',
  '왼쪽 위의 [편집] → [위젯 추가]를 눌러요.',
  '목록에서 junseo를 찾아 작은 크기나 큰 크기를 골라요.',
  '[위젯 추가]를 누르면 끝! 친구가 사진을 보내면 여기에 떠요.',
];

/**
 * 홈 화면 위젯 놓기. 가입 · 로그인 직후 위젯이 하나도 없으면 바로 뜨고(first=1), 내 정보에서도 연다.
 * Android 는 버튼 한 번으로 시스템의 「홈 화면에 추가」 창을 띄운다. iOS 는 앱이 위젯을 놓을 수 없어서 순서를 안내하고,
 * 홈 화면에서 놓고 돌아오면 놓인 것을 알아채 「놓았어요」로 바뀐다.
 */
export default function WidgetGuideScreen() {
  // 헤더가 바탕(그라데이션) 위에 투명하게 떠 있어서 그만큼 내려서 시작한다
  const headerHeight = useHeaderHeight();
  const { first } = useLocalSearchParams<{ first?: string }>();
  const onboarding = first === '1';
  const { me } = useAuth();
  const [feed, setFeed] = useState<WidgetFeed | null>(null);
  const [placed, setPlaced] = useState(false);
  const [pinUnsupported, setPinUnsupported] = useState(false);
  const meId = useRef(me?.id);

  useEffect(() => {
    api.widgetFeed().then((d) => setFeed(d ?? null)).catch(() => {});
  }, []);

  // 위젯이 놓였는지 계속 본다: Android 는 추가 창에서 돌아올 때, iOS 는 홈 화면에서 놓고 앱으로 돌아올 때
  useEffect(() => {
    let active = true;
    const check = () => void installedWidgets().then((count) => {
      if (active && count !== null && count > 0) setPlaced(true);
    });
    check();
    const timer = setInterval(check, 1500);
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') check();
    });
    return () => {
      active = false;
      clearInterval(timer);
      sub.remove();
    };
  }, []);

  // 놓았거나 「나중에」(또는 닫기)면 가입 · 로그인 때 다시 띄우지 않는다
  useEffect(() => {
    const id = meId.current;
    return () => {
      if (onboarding && id !== undefined) void markWidgetSetupDone(id);
    };
  }, [onboarding]);
  useEffect(() => {
    if (placed && me) void markWidgetSetupDone(me.id);
  }, [placed, me]);

  const close = () => (router.canGoBack() ? router.back() : router.replace('/'));

  const addToHome = () => {
    if (!widgetBridge.requestPin()) setPinUnsupported(true);
  };

  return (
    <ScrollView style={[styles.flex, { paddingTop: headerHeight }]} contentContainerStyle={styles.content}>
      <Text style={styles.title}>
        {placed ? '위젯을 놓았어요!' : onboarding ? '홈 화면에\n위젯을 놓아 주세요' : '친구 사진이\n홈 화면에 바로 떠요'}
      </Text>
      <Text style={styles.lead}>
        {placed
          ? Platform.OS === 'android'
            ? '친구가 사진을 보내면 이제 홈 화면에 바로 떠요. 위젯을 길게 누르면 크기를 바꿀 수 있고, 톱니바퀴로 한 친구의 사진만 보이게 할 수도 있어요.'
            : '친구가 사진을 보내면 이제 홈 화면에 바로 떠요. 위젯을 길게 눌러 한 친구의 사진만 보이게 할 수도 있어요.'
          : 'junseo는 앱을 열지 않아도 친구가 보낸 사진이 홈 화면 위젯에 떠요. 위젯을 놓아야 시작돼요.'}
      </Text>
      <View style={styles.previews}>
        <WidgetPreview feed={feed} size={150} />
        <View style={styles.previewCaption}>
          <Text style={styles.dim}>친구가 남긴 댓글도 사진 아래에 함께 보여요.</Text>
          <Text style={styles.dim}>양옆을 누르면 하루 동안 받은 사진을 5장까지 넘겨 봐요.</Text>
        </View>
      </View>

      {placed ? (
        <Button title={onboarding ? '시작하기' : '닫기'} onPress={close} />
      ) : (
        <>
          {Platform.OS === 'android' && !pinUnsupported && (
            <Pressable style={styles.addButton} onPress={addToHome} accessibilityRole="button">
              <Text style={styles.addButtonText}>홈 화면에 위젯 추가</Text>
            </Pressable>
          )}
          {Platform.OS === 'android' && pinUnsupported && (
            <Text style={styles.warn}>이 홈 화면 앱은 바로 추가를 지원하지 않아요. 아래 순서대로 놓아 주세요.</Text>
          )}
          <View style={styles.steps}>
            {Platform.OS === 'ios' && <Text style={styles.stepsTitle}>홈 화면으로 가서</Text>}
            {Platform.OS === 'android' && !pinUnsupported && <Text style={styles.stepsTitle}>버튼이 안 되면 직접 놓아요</Text>}
            {STEPS.map((s, i) => (
              <View key={i} style={styles.step}>
                <View style={styles.stepNo}>
                  <Text style={styles.stepNoText}>{i + 1}</Text>
                </View>
                <Text style={styles.stepText}>{s}</Text>
              </View>
            ))}
            {Platform.OS === 'ios' && <Text style={styles.dim}>놓고 앱으로 돌아오면 저절로 확인돼요.</Text>}
          </View>
          {onboarding && (
            <Pressable onPress={close} hitSlop={8} style={styles.later} accessibilityRole="button">
              <Text style={styles.laterText}>나중에 할게요</Text>
            </Pressable>
          )}
        </>
      )}

      <Text style={styles.note}>
        {Platform.OS === 'android'
          ? '알림을 허용하면 새 사진과 메시지를 받을 수 있어요. 위젯은 앱을 열거나 새로고침 버튼을 눌러 갱신할 수 있고, 백그라운드에서도 주기적으로 확인해요. 절전 설정과 네트워크 상태에 따라 갱신이 늦어질 수 있어요.'
          : '알림을 허용하면 사진이 몇 초 안에 위젯에 떠요. 알림을 끄면 iOS가 정한 주기에 맞춰 갱신돼요.'}
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  addButton: { backgroundColor: colors.accent, padding: 16, borderRadius: radius.card, alignItems: 'center' },
  addButtonText: { color: colors.accentText, fontWeight: '800', fontSize: 16 },
  content: { padding: 20, gap: 20, maxWidth: 560, width: '100%', alignSelf: 'center' },
  title: { color: colors.text, fontSize: 26, fontWeight: '800', lineHeight: 34 },
  lead: { color: colors.textDim, fontSize: 15, lineHeight: 22, marginTop: -8 },
  previews: { flexDirection: 'row', alignItems: 'center', gap: 16 },
  previewCaption: { flex: 1, gap: 8 },
  dim: { color: colors.textDim, fontSize: 14, lineHeight: 20 },
  warn: { color: colors.text, fontSize: 14, lineHeight: 20 },
  steps: { gap: 14, backgroundColor: colors.surface, padding: 18, borderRadius: radius.card },
  stepsTitle: { color: colors.textDim, fontSize: 13, fontWeight: '700' },
  step: { flexDirection: 'row', gap: 12, alignItems: 'center' },
  stepNo: { width: 26, height: 26, borderRadius: 13, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center' },
  stepNoText: { color: colors.accentText, fontWeight: '800' },
  stepText: { flex: 1, color: colors.text, fontSize: 15, lineHeight: 21 },
  later: { alignSelf: 'center', paddingVertical: 6 },
  laterText: { color: colors.textDim, fontSize: 15, textDecorationLine: 'underline' },
  note: { color: colors.textFaint, fontSize: 13, lineHeight: 19 },
});
