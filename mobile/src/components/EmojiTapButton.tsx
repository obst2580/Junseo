import * as Haptics from 'expo-haptics';
import { useEffect, useRef, useState } from 'react';
import { Animated, Easing, Platform, Pressable, StyleSheet } from 'react-native';

import { motionEasing } from '@/components/PressScale';
import { colors, motion } from '@/lib/theme';

const native = Platform.OS !== 'web';

// 이만큼 누르고 있으면 커지기 시작한다. 이보다 짧으면 한 번 누른 것.
const HOLD_MS = 280;
const CHARGE_MS = 650;
const CHARGED_SCALE = 1.55;
/** 꾹 눌렀다 떼면 한 번에 보내는 개수 */
export const BURST_TAPS = 3;

type Particle = { id: number; value: Animated.Value; dx: number; dy: number; rot: number; burst: boolean };

/** 한 번 누를 때 날아오르는 이모지 수 (디자인 「이모지 반응: 날아오르기」) */
const FLOAT_COUNT = 6;
const REACTION_MS = motion.ms('reaction');

let nextParticleId = 0;

/**
 * 여러 번 누를 수 있는 이모지 버튼. 누를 때마다 살짝 커졌다 돌아오고, 같은 이모지 여섯 개가 위로 흩날리며 날아오른다.
 * 꾹 누르면 점점 커지다가, 떼는 순간 터지면서 같은 이모지 3개가 한 번에 올라간다.
 * 손가락이 버튼 밖으로 나가거나 스크롤로 취소되면 아무것도 보내지 않고 원래 크기로 돌아간다.
 * 초록 테두리는 누르고 있는 동안에만 뜬다. 몇 번 눌렀는지·눌렀는지 여부는 버튼에 남기지 않는다.
 */
export function EmojiTapButton({ emoji, disabled, onTap }: { emoji: string; disabled?: boolean; onTap: (emoji: string, count: number) => void }) {
  const [scale] = useState(() => new Animated.Value(1));
  const [ring] = useState(() => new Animated.Value(0));
  const [wave] = useState(() => new Animated.Value(0));
  const [wobble] = useState(() => new Animated.Value(0));
  const [particles, setParticles] = useState<Particle[]>([]);
  const holdTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const shake = useRef<Animated.CompositeAnimation | null>(null);
  // 다 모았는지. 손을 떼면 onPressOut 다음에 onPress 가 와서 이걸 보고 터뜨린다.
  const charged = useRef(false);

  useEffect(() => {
    const timer = holdTimer;
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  const stopHold = () => {
    if (holdTimer.current) clearTimeout(holdTimer.current);
    holdTimer.current = null;
    shake.current?.stop();
    shake.current = null;
    wobble.setValue(0);
  };

  const launch = (count: number) => {
    const burst = count > 1;
    const added = Array.from({ length: burst ? count : FLOAT_COUNT }, (_, i): Particle => {
      // 날아오르기: 위로 흩어지며 기울어진다 (디자인 랩과 같은 범위)
      if (!burst)
        return {
          id: nextParticleId++,
          value: new Animated.Value(0),
          dx: (Math.random() - 0.5) * 90,
          dy: -(110 + Math.random() * 110),
          rot: (Math.random() - 0.5) * 50,
          burst,
        };
      // 위쪽 부채꼴로 고르게 퍼진다 (3개면 -130°~-50°)
      const angle = ((-90 + (i / (count - 1) - 0.5) * Math.min(140, 40 * (count - 1)) + (Math.random() - 0.5) * 12) * Math.PI) / 180;
      const dist = 70 + Math.random() * 60;
      return { id: nextParticleId++, value: new Animated.Value(0), dx: Math.cos(angle) * dist, dy: Math.sin(angle) * dist, rot: 0, burst };
    });
    setParticles((list) => [...list.slice(-18), ...added]);
    added.forEach((p, i) => {
      Animated.timing(p.value, {
        toValue: 1,
        duration: burst ? 900 + Math.random() * 250 : REACTION_MS * 1.7 + i * 60,
        easing: burst ? Easing.out(Easing.cubic) : Easing.bezier(0.2, 0.7, 0.3, 1),
        useNativeDriver: native,
      }).start(() => setParticles((list) => list.filter((x) => x.id !== p.id)));
    });
  };

  const pressIn = () => {
    if (disabled) return;
    charged.current = false;
    stopHold();
    ring.stopAnimation();
    ring.setValue(1);
    holdTimer.current = setTimeout(() => {
      holdTimer.current = null;
      charged.current = true;
      Haptics.selectionAsync().catch(() => {});
      scale.stopAnimation();
      Animated.timing(scale, { toValue: CHARGED_SCALE, duration: CHARGE_MS, easing: Easing.out(Easing.quad), useNativeDriver: native }).start(({ finished }) => {
        if (!finished || !charged.current) return;
        // 다 커지면 터지기 직전처럼 떤다
        shake.current = Animated.loop(
          Animated.sequence([
            Animated.timing(wobble, { toValue: 1, duration: 55, useNativeDriver: native }),
            Animated.timing(wobble, { toValue: -1, duration: 110, useNativeDriver: native }),
            Animated.timing(wobble, { toValue: 0, duration: 55, useNativeDriver: native }),
          ]),
        );
        shake.current.start();
      });
    }, HOLD_MS);
  };

  const pressOut = () => {
    Animated.timing(ring, { toValue: 0, duration: 380, easing: Easing.out(Easing.quad), useNativeDriver: native }).start();
    const wasCharging = charged.current;
    stopHold();
    // 버튼 위에서 뗐으면 곧바로 onPress 가 터뜨리고, 취소됐으면 이대로 원래 크기로 돌아간다.
    if (wasCharging) Animated.spring(scale, { toValue: 1, friction: 5, tension: 160, useNativeDriver: native }).start();
  };

  const press = () => {
    if (disabled) return;
    const burst = charged.current;
    charged.current = false;
    stopHold();
    scale.stopAnimation();
    if (burst) {
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy).catch(() => {});
      Animated.sequence([
        Animated.timing(scale, { toValue: 1.9, duration: 70, easing: Easing.out(Easing.quad), useNativeDriver: native }),
        Animated.spring(scale, { toValue: 1, friction: 4, tension: 180, useNativeDriver: native }),
      ]).start();
      wave.setValue(0);
      Animated.timing(wave, { toValue: 1, duration: 480, easing: Easing.out(Easing.cubic), useNativeDriver: native }).start();
      launch(BURST_TAPS);
      onTap(emoji, BURST_TAPS);
      return;
    }
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
    // 1 → 1.25 → 1 (빠르게 곡선, 반응 길이의 0.8)
    const d = REACTION_MS * 0.8;
    scale.setValue(1);
    Animated.sequence([
      Animated.timing(scale, { toValue: 1.25, duration: d * 0.3, easing: motionEasing, useNativeDriver: native }),
      Animated.timing(scale, { toValue: 1, duration: d * 0.7, easing: motionEasing, useNativeDriver: native }),
    ]).start();
    launch(1);
    onTap(emoji, 1);
  };

  const rotate = wobble.interpolate({ inputRange: [-1, 1], outputRange: ['-6deg', '6deg'] });

  return (
    <Pressable
      onPress={press}
      onPressIn={pressIn}
      onPressOut={pressOut}
      style={particles.length ? styles.raised : undefined}
      accessibilityRole="button"
      accessibilityLabel={`${emoji} 반응 보내기`}
      accessibilityHint={`꾹 누르면 ${BURST_TAPS}개를 한 번에 보내요`}
      hitSlop={4}>
      <Animated.View
        pointerEvents="none"
        style={[
          styles.wave,
          {
            opacity: wave.interpolate({ inputRange: [0, 0.01, 1], outputRange: [0, 0.9, 0] }),
            transform: [{ scale: wave.interpolate({ inputRange: [0, 1], outputRange: [1, 2.4] }) }],
          },
        ]}
      />
      <Animated.View style={[styles.button, { transform: [{ scale }, { rotate }] }]}>
        <Animated.Text style={styles.emoji}>{emoji}</Animated.Text>
        <Animated.View pointerEvents="none" style={[styles.ring, { opacity: ring }]} />
      </Animated.View>
      {particles.map((p) => (
        <Animated.Text key={p.id} pointerEvents="none" style={[styles.particle, particleMotion(p)]}>
          {emoji}
        </Animated.Text>
      ))}
    </Pressable>
  );
}

function particleMotion({ value, dx, dy, rot, burst }: Particle) {
  if (!burst) {
    return {
      opacity: value.interpolate({ inputRange: [0, 0.3, 1], outputRange: [0, 1, 0] }),
      transform: [
        { translateX: value.interpolate({ inputRange: [0, 0.3, 1], outputRange: [0, dx * 0.4, dx] }) },
        { translateY: value.interpolate({ inputRange: [0, 0.3, 1], outputRange: [0, dy * 0.35, dy] }) },
        { scale: value.interpolate({ inputRange: [0, 0.3, 1], outputRange: [0.5, 1.25, 0.9] }) },
        { rotate: value.interpolate({ inputRange: [0, 0.3, 1], outputRange: ['0deg', `${rot / 2}deg`, `${rot}deg`] }) },
      ],
    };
  }
  // 터지듯 빠르게 퍼진 다음, 위로 천천히 떠오르며 사라진다
  return {
    opacity: value.interpolate({ inputRange: [0, 0.08, 0.7, 1], outputRange: [0, 1, 1, 0] }),
    transform: [
      { translateX: value.interpolate({ inputRange: [0, 0.25, 1], outputRange: [0, dx * 0.75, dx] }) },
      { translateY: value.interpolate({ inputRange: [0, 0.25, 1], outputRange: [0, dy * 0.75, dy - 70] }) },
      { scale: value.interpolate({ inputRange: [0, 0.2, 1], outputRange: [0.4, 1.35, 0.85] }) },
    ],
  };
}

const styles = StyleSheet.create({
  raised: { zIndex: 1 },
  button: { width: 54, height: 54, borderRadius: 27, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.surface },
  ring: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, borderRadius: 27, borderWidth: 2, borderColor: colors.accent },
  wave: { position: 'absolute', top: 0, left: 0, width: 54, height: 54, borderRadius: 27, borderWidth: 3, borderColor: colors.accent },
  emoji: { fontSize: 26 },
  particle: { position: 'absolute', top: 10, left: 0, right: 0, textAlign: 'center', fontSize: 24 },
});
