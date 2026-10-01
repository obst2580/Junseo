import Ionicons from '@expo/vector-icons/Ionicons';
import { CameraView, useCameraPermissions, type CameraType } from 'expo-camera';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Avatar, Button, IconButton } from '@/components/ui';
import { api, ApiError, type Moment } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { toSquareJpeg } from '@/lib/capture';
import { absoluteUrl } from '@/lib/config';
import { events } from '@/lib/events';
import { colors, radius } from '@/lib/theme';
import { widgetBridge } from '@/lib/widgetBridge';

type Shot = { uri: string };

export default function CameraScreen() {
  const { me, refreshMe } = useAuth();
  const { width } = useWindowDimensions();
  const size = Math.min(width - 24, 520);

  const [permission, requestPermission] = useCameraPermissions();
  const cameraRef = useRef<CameraView>(null);
  const [facing, setFacing] = useState<CameraType>('back');
  const [shot, setShot] = useState<Shot | null>(null);
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [unread, setUnread] = useState(0);
  const [latest, setLatest] = useState<Moment | null>(null);

  const load = useCallback(() => {
    api.conversations().then((r) => setUnread(r.items.reduce((sum, c) => sum + c.unreadCount, 0))).catch(() => {});
    api.moments({ limit: 1 }).then((r) => setLatest(r.items[0] ?? null)).catch(() => {});
    refreshMe().catch(() => {});
  }, [refreshMe]);

  useFocusEffect(load);
  useEffect(() => {
    const offs = [events.on('messages', load), events.on('moments', load)];
    return () => offs.forEach((off) => off());
  }, [load]);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2200);
    return () => clearTimeout(t);
  }, [toast]);

  const capture = async () => {
    if (!cameraRef.current || busy) return;
    setBusy(true);
    try {
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch(() => {});
      const picture = await cameraRef.current.takePictureAsync({ quality: 0.9, shutterSound: false });
      setShot({ uri: await toSquareJpeg(picture.uri, picture.width, picture.height) });
    } catch {
      setToast('사진을 찍지 못했어요.');
    } finally {
      setBusy(false);
    }
  };

  const send = async () => {
    if (!shot) return;
    setBusy(true);
    try {
      if (Platform.OS === 'web') {
        const blob = await (await fetch(shot.uri)).blob();
        await api.uploadMomentBlob(blob);
      } else {
        await api.uploadMoment(shot.uri);
      }
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
      setShot(null);
      setToast(`친구 ${me?.friendCount ?? 0}명에게 보냈어요`);
      widgetBridge.reload();
      events.emit('moments');
    } catch (e) {
      setToast(e instanceof ApiError ? e.message : '보내지 못했어요.');
    } finally {
      setBusy(false);
    }
  };

  const noFriends = (me?.friendCount ?? 0) === 0;

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.topBar}>
        <Pressable onPress={() => router.push('/profile')} accessibilityLabel="내 정보">
          {me && <Avatar id={me.id} name={me.displayName} size={44} />}
        </Pressable>
        <Pressable style={styles.friendsPill} onPress={() => router.push('/friends')}>
          <Ionicons name="people" size={16} color={colors.text} />
          <Text style={styles.friendsPillText}>{noFriends ? '친구 추가' : `친구 ${me?.friendCount}명`}</Text>
        </Pressable>
        <IconButton icon="chatbubble-ellipses" onPress={() => router.push('/messages')} badge={unread} label="메시지" />
      </View>

      <View style={styles.center}>
        <View style={[styles.viewport, { width: size, height: size }]}>
          {shot ? (
            <Image source={{ uri: shot.uri }} style={StyleSheet.absoluteFill} contentFit="cover" />
          ) : permission?.granted ? (
            <CameraView ref={cameraRef} style={StyleSheet.absoluteFill} facing={facing} mirror={facing === 'front'} />
          ) : (
            <View style={styles.permission}>
              <Ionicons name="camera" size={36} color={colors.textDim} />
              <Text style={styles.permissionText}>사진을 찍으려면{'\n'}카메라 권한이 필요해요</Text>
              <Button title="권한 허용" onPress={requestPermission} style={{ height: 44 }} />
            </View>
          )}
          {busy && (
            <View style={styles.busy}>
              <ActivityIndicator color={colors.text} size="large" />
            </View>
          )}
        </View>

        {noFriends && !shot && (
          <Pressable style={styles.hint} onPress={() => router.push('/friends')}>
            <Text style={styles.hintText}>친구를 추가하면 사진이 친구의 홈 화면 위젯에 떠요 ›</Text>
          </Pressable>
        )}
      </View>

      <View style={styles.controls}>
        {shot ? (
          <>
            <IconButton icon="close" size={28} onPress={() => setShot(null)} label="다시 찍기" style={styles.sideButton} />
            <Pressable
              onPress={send}
              disabled={busy || noFriends}
              style={({ pressed }) => [styles.sendButton, (busy || noFriends) && { opacity: 0.4 }, pressed && { opacity: 0.7 }]}
              accessibilityLabel="보내기">
              <Ionicons name="paper-plane" size={34} color={colors.accentText} />
            </Pressable>
            <View style={styles.sideButton} />
          </>
        ) : (
          <>
            <View style={styles.sideButton} />
            <Pressable onPress={capture} disabled={!permission?.granted || busy} style={styles.shutterOuter} accessibilityLabel="촬영">
              {({ pressed }) => <View style={[styles.shutterInner, pressed && { transform: [{ scale: 0.9 }] }]} />}
            </Pressable>
            <IconButton
              icon="camera-reverse"
              size={26}
              onPress={() => setFacing((f) => (f === 'back' ? 'front' : 'back'))}
              label="카메라 전환"
              style={styles.sideButton}
            />
          </>
        )}
      </View>

      <Pressable style={styles.historyHandle} onPress={() => router.push('/history')}>
        {latest ? (
          <Image source={{ uri: absoluteUrl(latest.thumbUrl) }} style={styles.historyThumb} />
        ) : (
          <Ionicons name="images" size={18} color={colors.textDim} />
        )}
        <Text style={styles.historyText}>히스토리</Text>
        <Ionicons name="chevron-up" size={16} color={colors.textDim} />
      </Pressable>

      {toast && (
        <View style={styles.toast} pointerEvents="none">
          <Text style={styles.toastText}>{toast}</Text>
        </View>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  topBar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 16, paddingTop: 8 },
  friendsPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 40,
    paddingHorizontal: 16,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceHigh,
  },
  friendsPillText: { color: colors.text, fontSize: 15, fontWeight: '700' },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 14 },
  viewport: { borderRadius: radius.photo, overflow: 'hidden', backgroundColor: colors.surface },
  permission: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 14 },
  permissionText: { color: colors.textDim, fontSize: 15, textAlign: 'center', lineHeight: 22 },
  busy: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.35)', alignItems: 'center', justifyContent: 'center' },
  hint: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: radius.pill, backgroundColor: colors.surface },
  hintText: { color: colors.accent, fontSize: 13, fontWeight: '600' },
  controls: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-evenly', paddingVertical: 16 },
  sideButton: { width: 52, height: 52, borderRadius: 26 },
  shutterOuter: { width: 84, height: 84, borderRadius: 42, borderWidth: 4, borderColor: colors.accent, alignItems: 'center', justifyContent: 'center' },
  shutterInner: { width: 66, height: 66, borderRadius: 33, backgroundColor: colors.text },
  sendButton: { width: 84, height: 84, borderRadius: 42, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center' },
  historyHandle: { flexDirection: 'row', alignSelf: 'center', alignItems: 'center', gap: 8, paddingVertical: 10, paddingHorizontal: 14, marginBottom: 4 },
  historyThumb: { width: 26, height: 26, borderRadius: 8 },
  historyText: { color: colors.textDim, fontSize: 15, fontWeight: '700' },
  toast: {
    position: 'absolute',
    top: 72,
    alignSelf: 'center',
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceHigh,
  },
  toastText: { color: colors.text, fontSize: 14, fontWeight: '600' },
});
