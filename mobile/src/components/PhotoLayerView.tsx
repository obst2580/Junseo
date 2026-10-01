import * as Haptics from 'expo-haptics';
import { useEffect, useLayoutEffect, useRef, useState, type RefObject } from 'react';
import { Animated, PanResponder, Platform, StyleSheet, View, type GestureResponderEvent, type ViewStyle } from 'react-native';

import { BAR, LAYER_SCALE, TEXT, type PhotoLayer } from '@/lib/photoLayers';

type Point = { x: number; y: number };

/** 고른 요소를 사진 빈 곳의 두 손가락으로 조절할 때 쓰는 손잡이 */
export type LayerControl = { begin: () => void; update: (factor: number, turn: number) => void; end: () => void };
export type LayerControls = RefObject<Map<number, LayerControl>>;

type Props = {
  layer: PhotoLayer;
  /** 골랐는지 (점선 테두리) */
  selected: boolean;
  onSelect: (id: number) => void;
  controls: LayerControls;
  /** 사진 한 변 (pt) */
  size: number;
  /** 손가락 위치를 사진 기준으로 바꾸려고 사진 뷰를 잰다 */
  photoRef: RefObject<View | null>;
  /** 사진 기준 휴지통 가운데와 반경 */
  trash: Point & { radius: number };
  /** 끌기 시작·휴지통 위 여부가 바뀔 때, 끝나면 null */
  onDrag: (state: { overTrash: boolean } | null) => void;
  onChange: (layer: PhotoLayer) => void;
  onRemove: (id: number) => void;
  /** 짧게 눌렀을 때. wasSelected 는 누르기 전에 이미 골라져 있었는지 */
  onTap: (layer: PhotoLayer, wasSelected: boolean) => void;
};

// 웹에서는 두 손가락이 사진에 닿으면 브라우저가 화면 확대로 가져간다. 사진 위에서는 막는다.
const noBrowserGestures = (Platform.OS === 'web' ? { touchAction: 'none' } : {}) as ViewStyle;

const TAP_MS = 350;
const TAP_SLOP = 6;
const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

function touchesOf(e: GestureResponderEvent): Point[] {
  const { touches } = e.nativeEvent;
  const list = touches?.length ? touches : [e.nativeEvent];
  return list.slice(0, 2).map((t) => ({ x: t.pageX, y: t.pageY }));
}

type Pose = { x: number; y: number; scale: number; rotation: number };
type Gesture = { t0: number; moved: boolean; wasSelected: boolean; origin: Point | null; now: Pose; base: Pose & { touches: Point[] }; overTrash: boolean };

const angleOf = (p: Point, q: Point) => (Math.atan2(q.y - p.y, q.x - p.x) * 180) / Math.PI;

/**
 * 사진 위 요소 하나. 누르면 골라지고, 한 손가락으로 끌어 옮기고, 두 손가락으로 크기와 각도를 바꾼다.
 * 고른 뒤에는 사진 빈 곳에서 두 손가락을 써도 된다 (LayerPinchArea).
 * 손가락이 휴지통 위에서 떨어지면 지운다. 고른 텍스트를 한 번 더 누르면 고친다.
 * 끄는 동안에는 Animated 값만 바꾸고, 손을 뗄 때 한 번만 부모에 알린다.
 */
export function PhotoLayerView(props: Props) {
  const { layer, size } = props;
  const latest = useRef(props);
  useLayoutEffect(() => {
    latest.current = props;
  });

  const [center] = useState(() => new Animated.ValueXY({ x: layer.x * size, y: layer.y * size }));
  const [scale] = useState(() => new Animated.Value(layer.scale));
  const [rotation] = useState(() => new Animated.Value(layer.rotation));
  const [fade] = useState(() => new Animated.Value(1));
  const [pop] = useState(() => new Animated.Value(0.6));

  // 처음 올라올 때 톡 튀어나온다
  useEffect(() => {
    Animated.spring(pop, { toValue: 1, friction: 5, tension: 220, useNativeDriver: false }).start();
  }, [pop]);

  // 사진 빈 곳의 두 손가락이 이 요소를 조절할 수 있게 손잡이를 걸어 둔다 (크기·각도만)
  const { controls } = props;
  const id = layer.id;
  useEffect(() => {
    const map = controls.current;
    let base: { scale: number; rotation: number } | null = null;
    let now = { scale: 1, rotation: 0 };
    map.set(id, {
      begin: () => {
        const { layer: current } = latest.current;
        base = { scale: current.scale, rotation: current.rotation };
        now = { ...base };
      },
      update: (factor, turn) => {
        if (!base) return;
        now = { scale: clamp(base.scale * factor, LAYER_SCALE.min, LAYER_SCALE.max), rotation: base.rotation + turn };
        scale.setValue(now.scale);
        rotation.setValue(now.rotation);
      },
      end: () => {
        if (!base) return;
        base = null;
        const { layer: current, onChange } = latest.current;
        onChange({ ...current, scale: now.scale, rotation: now.rotation });
      },
    });
    return () => {
      map.delete(id);
    };
  }, [controls, id, scale, rotation]);

  // 밖에서 값이 바뀌면 (사진 크기 변화 등) 맞춘다
  useEffect(() => {
    center.setValue({ x: layer.x * size, y: layer.y * size });
    scale.setValue(layer.scale);
    rotation.setValue(layer.rotation);
  }, [center, scale, rotation, layer.x, layer.y, layer.scale, layer.rotation, size]);

  // 핸들러는 손가락이 움직일 때만 latest.current 를 읽는다 (렌더 중에는 읽지 않는다).
  // eslint-disable-next-line react-hooks/refs
  const [responder] = useState(() => {
    let g: Gesture | null = null;
    let reported = false;

    const rebase = (gesture: Gesture, touches: Point[]) => {
      gesture.base = { ...gesture.now, touches };
    };

    const finish = (released: boolean) => {
      const gesture = g;
      g = null;
      if (!gesture) return;
      const { layer: current, size: side, onDrag, onChange, onRemove, onTap } = latest.current;
      if (reported) onDrag(null);
      reported = false;
      if (released && gesture.overTrash) {
        Animated.parallel([
          Animated.timing(scale, { toValue: gesture.now.scale * 0.2, duration: 160, useNativeDriver: false }),
          Animated.timing(fade, { toValue: 0, duration: 160, useNativeDriver: false }),
        ]).start(() => onRemove(current.id));
        return;
      }
      fade.setValue(1);
      if (!gesture.moved) {
        if (released && Date.now() - gesture.t0 < TAP_MS) onTap(current, gesture.wasSelected);
        return;
      }
      const { x, y, scale: s, rotation: r } = gesture.now;
      onChange({ ...current, x: x / side, y: y / side, scale: s, rotation: r });
    };

    return PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      // 끄는 중에 스크롤 등에 뺏기지 않는다
      onPanResponderTerminationRequest: () => false,
      onShouldBlockNativeResponder: () => true,
      onPanResponderGrant: (e) => {
        const { layer: current, size: side, photoRef, selected, onSelect } = latest.current;
        const now = { x: current.x * side, y: current.y * side, scale: current.scale, rotation: current.rotation };
        const gesture: Gesture = { t0: Date.now(), moved: false, wasSelected: selected, origin: null, now, base: { ...now, touches: touchesOf(e) }, overTrash: false };
        g = gesture;
        if (!selected) onSelect(current.id);
        photoRef.current?.measureInWindow((x, y) => {
          gesture.origin = { x, y };
        });
      },
      onPanResponderMove: (e) => {
        const gesture = g;
        if (!gesture) return;
        const { size: side, trash, onDrag } = latest.current;
        const touches = touchesOf(e);
        // 손가락 수가 바뀌면 지금 자리에서 다시 잰다
        if (touches.length !== gesture.base.touches.length) rebase(gesture, touches);
        const b = gesture.base;
        if (touches.length === 1) {
          const dx = touches[0].x - b.touches[0].x;
          const dy = touches[0].y - b.touches[0].y;
          if (!gesture.moved && Math.hypot(dx, dy) < TAP_SLOP) return;
          gesture.now.x = clamp(b.x + dx, 0, side);
          gesture.now.y = clamp(b.y + dy, 0, side);
        } else {
          const [p, q] = touches;
          const [p0, q0] = b.touches;
          const d0 = Math.hypot(q0.x - p0.x, q0.y - p0.y) || 1;
          gesture.now.scale = clamp((b.scale * Math.hypot(q.x - p.x, q.y - p.y)) / d0, LAYER_SCALE.min, LAYER_SCALE.max);
          gesture.now.rotation = b.rotation + angleOf(p, q) - angleOf(p0, q0);
          gesture.now.x = clamp(b.x + (p.x + q.x - p0.x - q0.x) / 2, 0, side);
          gesture.now.y = clamp(b.y + (p.y + q.y - p0.y - q0.y) / 2, 0, side);
        }
        gesture.moved = true;
        center.setValue({ x: gesture.now.x, y: gesture.now.y });
        scale.setValue(gesture.now.scale);
        rotation.setValue(gesture.now.rotation);

        const o = gesture.origin;
        const over = touches.length === 1 && !!o && Math.hypot(touches[0].x - o.x - trash.x, touches[0].y - o.y - trash.y) < trash.radius;
        if (!reported || over !== gesture.overTrash) {
          reported = true;
          if (over !== gesture.overTrash) {
            gesture.overTrash = over;
            Animated.timing(fade, { toValue: over ? 0.35 : 1, duration: 120, useNativeDriver: false }).start();
            if (over) Haptics.selectionAsync().catch(() => {});
          }
          onDrag({ overTrash: over });
        }
      },
      onPanResponderRelease: () => finish(true),
      onPanResponderTerminate: () => finish(false),
    });
  });

  const rotate = rotation.interpolate({ inputRange: [0, 360], outputRange: ['0deg', '360deg'] });

  return (
    <Animated.View
      style={[styles.frame, { left: -size, top: -size, width: size * 2, height: size * 2, transform: center.getTranslateTransform() }]}>
      <Animated.View
        {...responder.panHandlers}
        style={[styles.grip, noBrowserGestures, props.selected && styles.selected, { opacity: fade, transform: [{ rotate }, { scale }, { scale: pop }] }]}>
        {layer.kind === 'bar' ? (
          <View accessibilityLabel="눈 가리개" style={{ width: BAR.width * size, height: BAR.height * size, backgroundColor: '#000' }} />
        ) : (
          <Animated.Text
            style={[
              styles.text,
              { fontSize: TEXT.size * size, lineHeight: TEXT.size * TEXT.lineHeight * size, maxWidth: TEXT.maxWidth * size, paddingHorizontal: 0.02 * size },
            ]}>
            {layer.text}
          </Animated.Text>
        )}
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  // 가운데가 (0,0)인 사진 두 배 크기 틀을 요소 가운데로 옮긴다. 회전·크기는 안쪽에서 가운데 기준으로.
  frame: { position: 'absolute', alignItems: 'center', justifyContent: 'center', pointerEvents: 'box-none' },
  // 얇은 가리개도 잡기 쉽게 둘레를 조금 넓힌다. 테두리는 고를 때만 보인다 (보내기 전에 고르기를 푼다).
  grip: { padding: 8, borderWidth: 1.5, borderStyle: 'dashed', borderColor: 'transparent', borderRadius: 6 },
  selected: { borderColor: 'rgba(255,255,255,0.9)' },
  text: {
    color: '#fff',
    fontWeight: '800',
    textAlign: 'center',
    letterSpacing: -0.2,
    textShadowColor: 'rgba(0,0,0,0.55)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 6,
  },
});

/**
 * 사진 빈 곳. 고른 요소가 있으면 여기서 두 손가락으로 그 요소의 크기·각도를 바꾼다.
 * 짧게 누르면 고르기를 푼다. 요소들보다 아래에 깔아서 요소를 누르면 요소가 먼저 받는다.
 */
export function LayerPinchArea({ controls, selectedId, onTapEmpty }: { controls: LayerControls; selectedId: number | null; onTapEmpty: () => void }) {
  const latest = useRef({ selectedId, onTapEmpty });
  useLayoutEffect(() => {
    latest.current = { selectedId, onTapEmpty };
  });

  // 핸들러는 손가락이 움직일 때만 latest.current 를 읽는다 (렌더 중에는 읽지 않는다).
  // eslint-disable-next-line react-hooks/refs
  const [responder] = useState(() => {
    let g: { t0: number; moved: boolean; start: Point; base: Point[] | null; control: LayerControl | null } | null = null;
    const release = () => {
      g?.control?.end();
      if (g) {
        g.base = null;
        g.control = null;
      }
    };
    const finish = (released: boolean) => {
      const gesture = g;
      release();
      g = null;
      if (gesture && released && !gesture.moved && Date.now() - gesture.t0 < TAP_MS) latest.current.onTapEmpty();
    };
    return PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderTerminationRequest: () => false,
      onShouldBlockNativeResponder: () => true,
      onPanResponderGrant: (e) => {
        g = { t0: Date.now(), moved: false, start: touchesOf(e)[0], base: null, control: null };
      },
      onPanResponderMove: (e) => {
        if (!g) return;
        const touches = touchesOf(e);
        if (touches.length < 2) {
          if (Math.hypot(touches[0].x - g.start.x, touches[0].y - g.start.y) > TAP_SLOP) g.moved = true;
          // 두 손가락 중 하나를 뗐으면 거기까지로 정한다
          if (g.base) release();
          return;
        }
        g.moved = true;
        if (!g.base) {
          const { selectedId: id } = latest.current;
          g.control = id === null ? null : (controls.current.get(id) ?? null);
          g.control?.begin();
          g.base = touches;
        }
        const [p, q] = touches;
        const [p0, q0] = g.base;
        g.control?.update(Math.hypot(q.x - p.x, q.y - p.y) / (Math.hypot(q0.x - p0.x, q0.y - p0.y) || 1), angleOf(p, q) - angleOf(p0, q0));
      },
      onPanResponderRelease: () => finish(true),
      onPanResponderTerminate: () => finish(false),
    });
  });

  return <View style={[StyleSheet.absoluteFill, noBrowserGestures]} {...responder.panHandlers} />;
}
