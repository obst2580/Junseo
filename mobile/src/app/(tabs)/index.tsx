import Ionicons from '@expo/vector-icons/Ionicons';
import { CameraView, useCameraPermissions, type CameraType } from 'expo-camera';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Platform, Pressable, StyleSheet, Text, View, useWindowDimensions } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { LayerPinchArea, PhotoLayerView, type LayerControl } from '@/components/PhotoLayerView';
import { RecipientSheet } from '@/components/RecipientSheet';
import { TextLayerEditor } from '@/components/TextLayerEditor';
import { Button, IconButton } from '@/components/ui';
import { api, ApiError, type GroupChat, type UserSummary } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { flattenPhoto, toSquareJpeg } from '@/lib/capture';
import { applyLens, preloadLens } from '@/lib/lensEffect';
import { pickLenses, type Zoom } from '@/lib/lenses';
import { events } from '@/lib/events';
import { newBar, newText, type PhotoLayer, type TextFont } from '@/lib/photoLayers';
import { colors, radius } from '@/lib/theme';
import { widgetBridge } from '@/lib/widgetBridge';

// lensUri: 렌즈 굴곡 효과를 켰을 때의 사진 (끄면 원본으로 돌아간다)
type Shot = { uri: string; lensUri?: string };

const TRASH_SIZE = 46;
const TRASH_BOTTOM = 12;
// 처음 한 번만 쓰는 법을 알려 준다
let layerHintShown = false;
// 새 글자는 마지막에 고른 모양으로 시작한다
let lastTextFont: TextFont = 'plain';
// 고르기 점선이 빠진 화면이 그려질 때까지 기다린다
const nextFrames = () => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));

export default function CameraScreen() {
  const { me, refreshMe } = useAuth();
  const { width } = useWindowDimensions();
  const size = Math.min(width - 24, 520);

  const [permission, requestPermission] = useCameraPermissions();
  const cameraRef = useRef<CameraView>(null);
  const [facing, setFacing] = useState<CameraType>('back');
  const [lenses, setLenses] = useState<string[]>([]);
  const [zoom, setZoom] = useState<Zoom>('wide');
  const { ultra, wide } = pickLenses(lenses);
  const selectedLens = zoom === 'ultra' && ultra ? ultra : wide;
  const [shot, setShot] = useState<Shot | null>(null);
  // 찍은 사진 위에 얹은 눈 가리개·텍스트. 보낼 때 사진에 합성한다.
  const photoRef = useRef<View>(null);
  const [layers, setLayers] = useState<PhotoLayer[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const layerControls = useRef(new Map<number, LayerControl>());
  const [dragging, setDragging] = useState<{ overTrash: boolean } | null>(null);
  const [editing, setEditing] = useState<{ id: number | null; text: string; font: TextFont } | null>(null);
  const [busy, setBusy] = useState(false);
  const [lensBusy, setLensBusy] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  // 받는 친구: 기본은 전체, 뺀 친구만 기억한다 (새로 사귄 친구는 자동으로 들어간다). 앱을 다시 켜면 전체로 돌아간다.
  const [friends, setFriends] = useState<UserSummary[] | null>(null);
  // 보낼 친구 고르기에서 단체방 사람들을 한꺼번에 고를 수 있다
  const [groups, setGroups] = useState<GroupChat[]>([]);
  const [excluded, setExcluded] = useState<Set<number>>(() => new Set());
  const [picking, setPicking] = useState(false);
  // 친구가 바뀌었을 수 있다 (친구 화면에서 돌아올 때 등)
  const load = useCallback(() => {
    refreshMe().catch(() => {});
    api
      .friends()
      .then((r) => setFriends(r.friends))
      .catch(() => {});
    api
      .groups()
      .then((r) => setGroups(r.items))
      .catch(() => {});
  }, [refreshMe]);
  useFocusEffect(load);

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
      preloadLens();
      setLayers([]);
      setSelectedId(null);
    } catch {
      setToast('사진을 찍지 못했어요.');
    } finally {
      setBusy(false);
    }
  };

  const retake = () => {
    setShot(null);
    setLayers([]);
    setSelectedId(null);
  };

  // 새로 올린 것은 바로 골라 둔다
  const addLayer = (layer: PhotoLayer) => {
    setLayers((list) => [...list, layer]);
    setSelectedId(layer.id);
    if (!layerHintShown) {
      layerHintShown = true;
      setToast('눌러서 고르고, 두 손가락으로 크기·각도');
    }
  };

  // 만진 요소는 맨 앞으로 온다
  const changeLayer = (next: PhotoLayer) => setLayers((list) => [...list.filter((l) => l.id !== next.id), next]);
  const removeLayer = (id: number) => {
    setLayers((list) => list.filter((l) => l.id !== id));
    setSelectedId((current) => (current === id ? null : current));
  };
  const toggleLens = async () => {
    if (!shot || lensBusy) return;
    Haptics.selectionAsync().catch(() => {});
    if (shot.lensUri) {
      setShot({ uri: shot.uri });
      return;
    }
    setLensBusy(true);
    try {
      const lensUri = await applyLens(shot.uri);
      // 그사이 다시 찍었으면 버린다
      setShot((current) => (current?.uri === shot.uri ? { ...current, lensUri } : current));
    } catch {
      setToast('렌즈 효과를 넣지 못했어요.');
    } finally {
      setLensBusy(false);
    }
  };

  const finishText = (text: string, font: TextFont) => {
    const target = editing;
    setEditing(null);
    lastTextFont = font;
    if (!target) return;
    if (target.id === null) {
      if (text) addLayer(newText(text, font));
      return;
    }
    if (text) setLayers((list) => list.map((l) => (l.id === target.id && l.kind === 'text' ? { ...l, text, font } : l)));
    else removeLayer(target.id);
  };

  const totalFriends = friends?.length ?? me?.friendCount ?? 0;
  const recipients = friends?.filter((f) => !excluded.has(f.id)) ?? null;
  const chosenCount = recipients?.length ?? totalFriends;
  const someExcluded = chosenCount < totalFriends;

  const send = async () => {
    if (!shot) return;
    if (chosenCount === 0) {
      setToast('받을 친구를 한 명 이상 골라 주세요');
      return;
    }
    // 전체에게 보낼 때는 목록을 보내지 않는다 (서버가 그때의 친구 전체로 정한다)
    const recipientIds = someExcluded && recipients ? recipients.map((f) => f.id) : undefined;
    setBusy(true);
    try {
      let uri = shot.lensUri ?? shot.uri;
      if (layers.length) {
        setSelectedId(null);
        await nextFrames();
        uri = await flattenPhoto(photoRef);
      }
      if (Platform.OS === 'web') {
        const blob = await (await fetch(uri)).blob();
        await api.uploadMomentBlob(blob, recipientIds);
      } else {
        await api.uploadMoment(uri, recipientIds);
      }
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success).catch(() => {});
      setShot(null);
      setLayers([]);
      setSelectedId(null);
      setToast(`친구 ${chosenCount}명에게 보냈어요`);
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
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.topBar}>
        {/* 누르면 보낼 친구를 고른다. 친구가 없으면 친구 추가로 */}
        <Pressable
          style={[styles.friendsPill, someExcluded && styles.friendsPillPartial]}
          onPress={() => (noFriends || !friends ? router.push('/friends') : setPicking(true))}
          accessibilityRole="button"
          accessibilityLabel={noFriends ? '친구 추가' : '보낼 친구 고르기'}>
          <Ionicons name="people" size={16} color={someExcluded ? colors.accent : colors.text} />
          <Text style={[styles.friendsPillText, someExcluded && { color: colors.accent }]}>
            {noFriends ? '친구 추가' : someExcluded ? `${totalFriends}명 중 ${chosenCount}명` : `친구 ${totalFriends}명`}
          </Text>
          {!noFriends && <Ionicons name="chevron-down" size={14} color={someExcluded ? colors.accent : colors.textDim} />}
        </Pressable>
      </View>

      <View style={styles.body}>
      <View style={styles.center}>
        <View style={[styles.viewport, { width: size, height: size }]}>
          {shot ? (
            <>
              {/* 이 뷰가 그대로 합성된다. 도구·휴지통은 바깥에 둔다. */}
              <View ref={photoRef} collapsable={false} style={StyleSheet.absoluteFill}>
                <Image source={{ uri: shot.lensUri ?? shot.uri }} style={StyleSheet.absoluteFill} contentFit="cover" />
                <LayerPinchArea controls={layerControls} selectedId={selectedId} onTapEmpty={() => setSelectedId(null)} />
                {layers
                  .filter((l) => l.id !== editing?.id)
                  .map((l) => (
                    <PhotoLayerView
                      key={l.id}
                      layer={l}
                      selected={l.id === selectedId}
                      onSelect={setSelectedId}
                      controls={layerControls}
                      size={size}
                      photoRef={photoRef}
                      trash={{ x: size / 2, y: size - TRASH_BOTTOM - TRASH_SIZE / 2, radius: 44 }}
                      onDrag={setDragging}
                      onChange={changeLayer}
                      onRemove={removeLayer}
                      onTap={(t, wasSelected) => (t.kind === 'text' && wasSelected ? setEditing({ id: t.id, text: t.text, font: t.font }) : changeLayer(t))}
                    />
                  ))}
              </View>
              {dragging ? (
                <View style={[styles.trash, dragging.overTrash && styles.trashHot]}>
                  <Ionicons name="trash-outline" size={22} color="#fff" />
                </View>
              ) : (
                <View style={styles.zoomRow}>
                  <Pressable
                    onPress={() => {
                      Haptics.selectionAsync().catch(() => {});
                      addLayer(newBar());
                    }}
                    style={styles.tool}
                    accessibilityRole="button"
                    accessibilityLabel="눈 가리개 추가">
                    <View style={styles.barIcon} />
                    <Text style={styles.toolText}>눈 가리개</Text>
                  </Pressable>
                  <Pressable onPress={() => setEditing({ id: null, text: '', font: lastTextFont })} style={styles.tool} accessibilityRole="button" accessibilityLabel="텍스트 추가">
                    <Text style={styles.aa}>Aa</Text>
                    <Text style={styles.toolText}>텍스트</Text>
                  </Pressable>
                  <Pressable
                    onPress={toggleLens}
                    style={[styles.tool, shot.lensUri && styles.toolOn]}
                    accessibilityRole="switch"
                    aria-checked={!!shot.lensUri}
                    accessibilityLabel="렌즈 굴곡 효과">
                    {lensBusy ? (
                      <ActivityIndicator size="small" color="#fff" style={styles.lensIcon} />
                    ) : (
                      <Ionicons name="aperture" size={17} color={shot.lensUri ? colors.accent : '#fff'} style={styles.lensIcon} />
                    )}
                    <Text style={[styles.toolText, shot.lensUri && { color: colors.accent }]}>렌즈</Text>
                  </Pressable>
                </View>
              )}
            </>
          ) : permission?.granted ? (
            <>
              <CameraView
                ref={cameraRef}
                style={StyleSheet.absoluteFill}
                facing={facing}
                mirror={facing === 'front'}
                selectedLens={selectedLens}
                onAvailableLensesChanged={(e) => setLenses(e.lenses)}
              />
              {ultra && (
                <View style={styles.zoomRow}>
                  {(['ultra', 'wide'] as const).map((z) => {
                    const on = zoom === z;
                    return (
                      <Pressable
                        key={z}
                        onPress={() => {
                          Haptics.selectionAsync().catch(() => {});
                          setZoom(z);
                        }}
                        style={[styles.zoomPill, on && styles.zoomPillOn]}
                        accessibilityRole="button"
                        accessibilityState={{ selected: on }}
                        accessibilityLabel={z === 'ultra' ? '광각' : '1배'}>
                        <Text style={[styles.zoomText, on && styles.zoomTextOn]}>{z === 'ultra' ? (on ? '0.5×' : '.5') : on ? '1×' : '1'}</Text>
                      </Pressable>
                    );
                  })}
                </View>
              )}
            </>
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
            <IconButton icon="close" size={28} onPress={retake} label="다시 찍기" style={styles.sideButton} />
            <Pressable
              onPress={send}
              disabled={busy || noFriends}
              style={({ pressed }) => [styles.sendButton, (busy || noFriends || chosenCount === 0) && { opacity: 0.4 }, pressed && { opacity: 0.7 }]}
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
              onPress={() => {
                // 전면·후면은 렌즈 목록이 달라서 1배로 돌아간다.
                setLenses([]);
                setZoom('wide');
                setFacing((f) => (f === 'back' ? 'front' : 'back'));
              }}
              label="카메라 전환"
              style={styles.sideButton}
            />
          </>
        )}
      </View>
      </View>
      {toast && (
        <View style={styles.toast} pointerEvents="none">
          <Text style={styles.toastText}>{toast}</Text>
        </View>
      )}
      {editing && <TextLayerEditor initial={editing.text} initialFont={editing.font} onDone={finishText} />}
      {picking && friends && (
        <RecipientSheet
          friends={friends}
          groups={groups}
          meId={me?.id}
          excluded={excluded}
          onChange={setExcluded}
          onClose={() => setPicking(false)}
          onManage={() => {
            setPicking(false);
            router.push('/friends');
          }}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  // 내 정보는 친구 화면 맨 위로 옮겼다. 여기는 보낼 친구 버튼만.
  topBar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16, paddingTop: 8, minHeight: 52 },
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
  // 일부만 고르면 강조색 테두리로 표시한다
  friendsPillPartial: { borderWidth: 1.5, borderColor: colors.accent },
  // 사진 칸 + 셔터 묶음은 상단 바와 탭 바 사이 가운데에 둔다.
  body: { flex: 1, justifyContent: 'center' },
  center: { alignItems: 'center', gap: 14 },
  viewport: { borderRadius: radius.photo, overflow: 'hidden', backgroundColor: colors.surface },
  zoomRow: {
    position: 'absolute',
    bottom: 14,
    alignSelf: 'center',
    flexDirection: 'row',
    gap: 4,
    padding: 4,
    borderRadius: radius.pill,
    backgroundColor: 'rgba(0,0,0,0.35)',
  },
  zoomPill: { minWidth: 34, height: 34, borderRadius: 17, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 6 },
  zoomPillOn: { backgroundColor: 'rgba(0,0,0,0.55)' },
  zoomText: { color: '#fff', fontSize: 12, fontWeight: '700' },
  zoomTextOn: { color: colors.accent, fontSize: 13 },
  // 찍은 뒤에는 렌즈 버튼 자리에 꾸미기 도구가 뜬다
  tool: { height: 34, borderRadius: 17, flexDirection: 'row', alignItems: 'center', gap: 7, paddingHorizontal: 12 },
  toolText: { color: '#fff', fontSize: 13, fontWeight: '700' },
  toolOn: { backgroundColor: 'rgba(0,0,0,0.55)' },
  lensIcon: { width: 17, height: 17 },
  barIcon: { width: 18, height: 6, borderRadius: 1, backgroundColor: '#000', borderWidth: 1.5, borderColor: '#fff' },
  aa: { color: '#fff', fontSize: 14, fontWeight: '800', letterSpacing: -0.3 },
  trash: {
    position: 'absolute',
    bottom: TRASH_BOTTOM,
    alignSelf: 'center',
    width: TRASH_SIZE,
    height: TRASH_SIZE,
    borderRadius: TRASH_SIZE / 2,
    backgroundColor: 'rgba(0,0,0,0.5)',
    alignItems: 'center',
    justifyContent: 'center',
    pointerEvents: 'none',
  },
  trashHot: { backgroundColor: colors.danger, transform: [{ scale: 1.18 }] },
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
  // 상단 친구 버튼을 가리지 않게 탭 바 바로 위에 띄운다.
  toast: {
    position: 'absolute',
    bottom: 16,
    alignSelf: 'center',
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceHigh,
  },
  toastText: { color: colors.text, fontSize: 14, fontWeight: '600' },
});
