import { Image } from 'expo-image';
import { createContext, useContext, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { Animated, Easing, PanResponder, Platform, StyleSheet, View, type GestureResponderEvent, type ViewStyle } from 'react-native';

type Point = { x: number; y: number };
type Frame = { x: number; y: number; width: number; height: number };
type Zoom = { uri: string; frame: Frame; radius: number; scale: Animated.Value; tx: Animated.Value; ty: Animated.Value; dim: Animated.Value };

const MAX_SCALE = 4;
const ZoomContext = createContext<(zoom: Zoom | null) => void>(() => {});

/**
 * 화면 맨 위에 확대한 사진을 띄우는 자리. 루트 레이아웃에서 한 번 감싼다.
 * 스크롤 뷰 안에서 키우면 잘리니까, 확대하는 동안만 사진을 여기로 옮겨 그린다.
 */
export function ZoomProvider({ children }: { children: ReactNode }) {
  const [zoom, setZoom] = useState<Zoom | null>(null);
  return (
    <ZoomContext.Provider value={setZoom}>
      {children}
      {zoom && <ZoomLayer zoom={zoom} />}
    </ZoomContext.Provider>
  );
}

function ZoomLayer({ zoom: { uri, frame, radius, scale, tx, ty, dim } }: { zoom: Zoom }) {
  return (
    <View style={styles.layer}>
      <Animated.View style={[StyleSheet.absoluteFill, styles.dim, { opacity: dim }]} />
      <Animated.View
        style={{
          position: 'absolute',
          left: frame.x,
          top: frame.y,
          width: frame.width,
          height: frame.height,
          borderRadius: radius,
          overflow: 'hidden',
          transform: [{ translateX: tx }, { translateY: ty }, { scale }],
        }}>
        <Image source={{ uri }} style={StyleSheet.absoluteFill} contentFit="cover" />
      </Animated.View>
    </View>
  );
}

function touchesOf(e: GestureResponderEvent): Point[] {
  return (e.nativeEvent.touches ?? []).slice(0, 2).map((t) => ({ x: t.pageX, y: t.pageY }));
}
const two = (e: GestureResponderEvent) => (e.nativeEvent.touches?.length ?? 0) >= 2;
const dist = (p: Point, q: Point) => Math.hypot(q.x - p.x, q.y - p.y);
const mid = (p: Point, q: Point) => ({ x: (p.x + q.x) / 2, y: (p.y + q.y) / 2 });

// 웹: 한 손가락 세로 스크롤은 그대로, 두 손가락은 브라우저 화면 확대 대신 이 사진 확대로
const webTouch = (Platform.OS === 'web' ? { touchAction: 'pan-y' } : {}) as ViewStyle;

/**
 * 인스타처럼 두 손가락으로 벌리면 사진이 커지고 손가락을 따라 움직인다. 손을 떼면 제자리로 돌아간다.
 * 한 손가락은 건드리지 않아서 스크롤은 그대로 된다. 누르기로 확대하지는 않는다.
 */
export function PinchToZoom({
  uri,
  radius,
  onZoomingChange,
  children,
}: {
  uri: string;
  radius: number;
  /** 확대하는 동안 스크롤을 막으려고 */
  onZoomingChange?: (zooming: boolean) => void;
  children: ReactNode;
}) {
  const setZoom = useContext(ZoomContext);
  const ref = useRef<View>(null);
  const latest = useRef({ uri, radius, onZoomingChange, setZoom });
  useLayoutEffect(() => {
    latest.current = { uri, radius, onZoomingChange, setZoom };
  });
  const [shown] = useState(() => new Animated.Value(1));

  // 핸들러는 손가락이 움직일 때만 latest/ref 를 읽는다 (렌더 중에는 읽지 않는다).
  // eslint-disable-next-line react-hooks/refs
  const [responder] = useState(() => {
    let g: { d0: number; f0: Point; center: Point; zoom: Zoom | null; active: boolean } | null = null;

    const begin = (e: GestureResponderEvent) => {
      const [p, q] = touchesOf(e);
      if (!p || !q) return;
      const gesture = { d0: dist(p, q) || 1, f0: mid(p, q), center: { x: 0, y: 0 }, zoom: null as Zoom | null, active: true };
      g = gesture;
      // 사진이 화면 어디에 있는지 재서 같은 자리에 띄운 다음 키운다
      ref.current?.measureInWindow((x, y, width, height) => {
        if (!gesture.active) return;
        const { uri: src, radius: r, setZoom: show, onZoomingChange: changed } = latest.current;
        const zoom: Zoom = {
          uri: src,
          frame: { x, y, width, height },
          radius: r,
          scale: new Animated.Value(1),
          tx: new Animated.Value(0),
          ty: new Animated.Value(0),
          dim: new Animated.Value(0),
        };
        gesture.center = { x: x + width / 2, y: y + height / 2 };
        gesture.zoom = zoom;
        show(zoom);
        shown.setValue(0);
        changed?.(true);
      });
    };

    const move = (e: GestureResponderEvent) => {
      const zoom = g?.zoom;
      if (!g || !zoom) return;
      const [p, q] = touchesOf(e);
      if (!p || !q) return; // 한 손가락만 남으면 그 자리에 둔다
      const s = Math.min(MAX_SCALE, Math.max(1, dist(p, q) / g.d0));
      const c = mid(p, q);
      // 처음 집은 곳(f0)이 늘 두 손가락 가운데(c)에 오도록: t = (c - f0) + (s - 1)(사진 가운데 - f0)
      zoom.scale.setValue(s);
      zoom.tx.setValue(c.x - g.f0.x + (s - 1) * (g.center.x - g.f0.x));
      zoom.ty.setValue(c.y - g.f0.y + (s - 1) * (g.center.y - g.f0.y));
      zoom.dim.setValue(Math.min(0.6, (s - 1) * 0.6));
    };

    const end = () => {
      const gesture = g;
      g = null;
      if (!gesture) return;
      gesture.active = false;
      const zoom = gesture.zoom;
      if (!zoom) return;
      // 살짝 넘쳤다 돌아오는 짧은 복귀. 스프링은 멈췄다고 알리기까지 오래 걸려서 그동안 스크롤이 막혔다.
      const back = (v: Animated.Value, to: number) =>
        Animated.timing(v, { toValue: to, duration: 280, easing: Easing.out(Easing.back(1.2)), useNativeDriver: false });
      Animated.parallel([
        back(zoom.scale, 1),
        back(zoom.tx, 0),
        back(zoom.ty, 0),
        Animated.timing(zoom.dim, { toValue: 0, duration: 200, useNativeDriver: false }),
      ]).start(() => {
        shown.setValue(1);
        latest.current.setZoom(null);
        latest.current.onZoomingChange?.(false);
      });
    };

    return PanResponder.create({
      // 두 손가락일 때만 가져간다. 한 손가락은 스크롤에 맡긴다.
      onStartShouldSetPanResponder: two,
      onStartShouldSetPanResponderCapture: two,
      onMoveShouldSetPanResponder: two,
      onMoveShouldSetPanResponderCapture: two,
      onPanResponderTerminationRequest: () => false,
      onShouldBlockNativeResponder: () => true,
      onPanResponderGrant: begin,
      onPanResponderMove: move,
      onPanResponderRelease: end,
      onPanResponderTerminate: end,
    });
  });

  return (
    <Animated.View ref={ref} collapsable={false} style={[webTouch, { opacity: shown }]} {...responder.panHandlers}>
      {children}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  layer: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, pointerEvents: 'none' },
  dim: { backgroundColor: '#000' },
});
