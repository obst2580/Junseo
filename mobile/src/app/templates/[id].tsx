import { Image } from 'expo-image';
import * as Haptics from 'expo-haptics';
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useHeaderHeight } from 'expo-router/react-navigation';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Platform, ScrollView, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon, type IconName } from '@/components/Icon';
import { PressScale } from '@/components/PressScale';
import { TemplateCanvas } from '@/components/TemplateCanvas';
import { Button, Empty } from '@/components/ui';
import { showRewardedAd } from '@/lib/ads';
import { api, ApiError, type Moment } from '@/lib/api';
import { captureView } from '@/lib/capture';
import { absoluteUrl } from '@/lib/config';
import { events } from '@/lib/events';
import { clockTime } from '@/lib/format';
import { useTemplate } from '@/lib/templateCatalog';
import { todaysMoments, type Template } from '@/lib/templates';
import { colors, radius } from '@/lib/theme';
import { widgetBridge } from '@/lib/widgetBridge';

const COLUMNS = 4;

/**
 * 템플릿 채우기: 오늘 사진을 눌러 칸을 순서대로 채운다 (다시 누르면 빠진다).
 * 다 채우면 광고를 보고 완성 → 사진첩 저장 · 공유 · 친구에게 보내기.
 * 완성한 뒤에는 사진을 바꿀 수 없다 (새로 만들려면 광고를 한 번 더).
 */
export default function TemplateEditor() {
  const { id } = useLocalSearchParams<{ id: string }>();
  // 서버 템플릿은 목록을 받아 올 때까지 잠깐 기다린다 (링크로 바로 들어온 경우)
  const { template, ready } = useTemplate(id);
  if (!template) return ready ? <Empty icon="images" title="템플릿을 찾을 수 없어요." /> : <ActivityIndicator color={colors.accent} style={{ marginTop: 120 }} />;
  return <Editor template={template} />;
}

function Editor({ template }: { template: Template }) {
  const headerHeight = useHeaderHeight();
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const canvasRef = useRef<View>(null);

  const [today, setToday] = useState<Moment[] | null>(null);
  const [picked, setPicked] = useState<(Moment | null)[]>(() => template.slots.map(() => null));
  const [result, setResult] = useState<string | null>(null);
  const [working, setWorking] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  useEffect(() => {
    todaysMoments()
      .then(setToday)
      .catch(() => setToday([]));
  }, []);
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2200);
    return () => clearTimeout(t);
  }, [toast]);

  const filled = picked.filter(Boolean).length;
  const missing = template.slots.length - filled;
  const previewWidth = Math.min(width - 32, 360) * (template.height > template.width * 1.4 ? 0.62 : 0.9);
  const tile = (Math.min(width, 640) - 32 - 6 * (COLUMNS - 1)) / COLUMNS;

  const toggle = (m: Moment) => {
    if (result) return;
    Haptics.selectionAsync().catch(() => {});
    setPicked((list) => {
      const at = list.findIndex((x) => x?.id === m.id);
      if (at >= 0) return list.map((x, i) => (i === at ? null : x));
      const empty = list.indexOf(null);
      if (empty < 0) {
        setToast(`사진은 ${list.length}장까지 넣을 수 있어요`);
        return list;
      }
      return list.map((x, i) => (i === empty ? m : x));
    });
  };

  const unlock = async () => {
    setWorking('ad');
    try {
      const rewarded = await showRewardedAd();
      if (!rewarded) {
        setToast('광고를 끝까지 보면 만들 수 있어요');
        return;
      }
      setWorking('render');
      // 템플릿 원래 크기(px)로 찍는다
      setResult(await captureView(canvasRef, template.width, template.height));
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
    } catch {
      setToast('광고를 불러오지 못했어요. 잠시 후 다시 해 주세요.');
    } finally {
      setWorking(null);
    }
  };

  const save = async () => {
    if (!result) return;
    try {
      if (Platform.OS === 'web') {
        download(result);
      } else {
        const MediaLibrary = await import('expo-media-library');
        const { granted } = await MediaLibrary.requestPermissionsAsync(true);
        if (!granted) {
          setToast('사진첩 저장을 허용해 주세요');
          return;
        }
        await MediaLibrary.saveToLibraryAsync(result);
      }
      setToast('사진첩에 저장했어요');
    } catch {
      setToast('저장하지 못했어요.');
    }
  };

  const share = async () => {
    if (!result) return;
    try {
      if (Platform.OS === 'web') {
        const file = new File([await (await fetch(result)).blob()], 'junseo.jpg', { type: 'image/jpeg' });
        if (navigator.canShare?.({ files: [file] })) await navigator.share({ files: [file] });
        else download(result);
      } else {
        const Sharing = await import('expo-sharing');
        await Sharing.shareAsync(result, { mimeType: 'image/jpeg', UTI: 'public.jpeg' });
      }
    } catch {
      // 공유 창을 닫은 경우
    }
  };

  const send = async () => {
    if (!result) return;
    setWorking('send');
    try {
      if (Platform.OS === 'web') await api.uploadMomentBlob(await (await fetch(result)).blob());
      else await api.uploadMoment(result);
      const { friends } = await api.friends();
      setToast(`친구 ${friends.length}명에게 보냈어요`);
      widgetBridge.reload();
      events.emit('moments');
    } catch (e) {
      setToast(e instanceof ApiError ? e.message : '보내지 못했어요.');
    } finally {
      setWorking(null);
    }
  };

  return (
    <View style={[styles.flex, { paddingTop: headerHeight }]}>
      <Stack.Screen options={{ title: template.name }} />
      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: 120 + insets.bottom }]}>
        <View style={styles.preview}>
          <TemplateCanvas ref={canvasRef} template={template} photos={picked} width={previewWidth} />
        </View>
        {today === null ? (
          <ActivityIndicator color={colors.accent} style={{ marginTop: 24 }} />
        ) : today.length === 0 ? (
          <Empty icon="camera" title={'오늘 찍거나 받은 사진이 없어요.\n사진을 찍어 보내 보세요!'}>
            <Button title="카메라로" onPress={() => router.navigate('/')} style={{ paddingHorizontal: 28 }} />
          </Empty>
        ) : (
          <>
            <Text style={styles.section}>
              오늘 사진 {today.length}장 · {result ? '완성했어요' : `${template.slots.length}장을 골라 주세요`}
            </Text>
            <View style={styles.grid}>
              {today.map((m) => {
                const at = picked.findIndex((x) => x?.id === m.id);
                return (
                  <PressScale
                    key={m.id}
                    onPress={() => toggle(m)}
                    disabled={!!result}
                    style={[styles.tile, { width: tile, height: tile, borderRadius: tile * 0.16 }, at >= 0 && styles.tilePicked, !!result && at < 0 && styles.tileDim]}
                    accessibilityRole="checkbox"
                    aria-checked={at >= 0}
                    accessibilityLabel={`${m.sender.displayName} ${clockTime(m.createdAt)}`}>
                    <Image source={{ uri: absoluteUrl(m.thumbUrl) }} style={StyleSheet.absoluteFill} contentFit="cover" />
                    {at >= 0 && (
                      <View style={styles.badge}>
                        <Text style={styles.badgeText}>{at + 1}</Text>
                      </View>
                    )}
                  </PressScale>
                );
              })}
            </View>
          </>
        )}
      </ScrollView>

      <View style={[styles.bar, { paddingBottom: insets.bottom + 12 }]}>
        {result ? (
          <View style={styles.actions}>
            <Action icon="images" label="저장" onPress={save} />
            <Action icon="share" label="공유" onPress={share} />
            <Action icon="plane" label="친구에게" onPress={send} busy={working === 'send'} />
          </View>
        ) : (
          <Button
            title={missing > 0 ? `사진 ${missing}장 더 골라 주세요` : working ? '만드는 중…' : '광고 보고 완성하기'}
            onPress={unlock}
            disabled={missing > 0 || !!working}
            loading={working === 'ad' || working === 'render'}
          />
        )}
      </View>
      {toast && (
        <View style={[styles.toast, { bottom: 96 + insets.bottom }]} pointerEvents="none">
          <Text style={styles.toastText}>{toast}</Text>
        </View>
      )}
    </View>
  );
}

function Action({ icon, label, onPress, busy }: { icon: IconName; label: string; onPress: () => void; busy?: boolean }) {
  return (
    <PressScale onPress={onPress} disabled={busy} style={styles.action} accessibilityRole="button" accessibilityLabel={label}>
      {busy ? <ActivityIndicator color={colors.accentText} /> : <Icon name={icon} size={22} color={colors.accentText} />}
      <Text style={styles.actionText}>{label}</Text>
    </PressScale>
  );
}

function download(uri: string) {
  const a = document.createElement('a');
  a.href = uri;
  a.download = `junseo-${Date.now()}.jpg`;
  a.click();
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 16, gap: 16 },
  // 템플릿 바탕이 앱 바탕과 섞이지 않게 밝은 테두리 + 그림자
  preview: { alignSelf: 'center', borderRadius: 14, overflow: 'hidden', borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.28)', boxShadow: '0 12px 30px -8px rgba(0,0,0,0.75)' },
  section: { color: colors.textDim, fontSize: 14, fontWeight: '600' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  tile: { overflow: 'hidden', backgroundColor: colors.surface },
  tilePicked: { borderWidth: 3, borderColor: colors.accent },
  tileDim: { opacity: 0.35 },
  badge: {
    position: 'absolute',
    top: 5,
    right: 5,
    minWidth: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: colors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { color: colors.accentText, fontSize: 12, fontWeight: '800' },
  // 버튼 뒤로 사진이 비치지 않게 거의 불투명하게
  bar: { position: 'absolute', left: 0, right: 0, bottom: 0, paddingHorizontal: 16, paddingTop: 12, backgroundColor: 'rgba(14,13,12,0.9)' },
  actions: { flexDirection: 'row', gap: 10 },
  action: {
    flex: 1,
    height: 56,
    borderRadius: radius.button,
    backgroundColor: colors.accent,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 2,
  },
  actionText: { color: colors.accentText, fontSize: 13, fontWeight: '800' },
  toast: {
    position: 'absolute',
    alignSelf: 'center',
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: radius.pill,
    backgroundColor: colors.sheet,
  },
  toastText: { color: colors.text, fontSize: 14, fontWeight: '600' },
});
