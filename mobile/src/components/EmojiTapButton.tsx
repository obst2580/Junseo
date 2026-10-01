import { useState } from 'react';
import { Animated, Easing, Platform, Pressable, StyleSheet } from 'react-native';

import { colors } from '@/lib/theme';

const native = Platform.OS !== 'web';

type Particle = { id: number; value: Animated.Value; drift: number };

/**
 * 여러 번 누를 수 있는 이모지 버튼. 누를 때마다 톡 튀고, 같은 이모지가 하나씩 떠올라 사라진다.
 * 초록 테두리는 누르는 순간에만 떴다가 사라진다. 몇 번 눌렀는지·눌렀는지 여부는 버튼에 남기지 않는다.
 */
export function EmojiTapButton({ emoji, disabled, onTap }: { emoji: string; disabled?: boolean; onTap: (emoji: string) => void }) {
  const [scale] = useState(() => new Animated.Value(1));
  const [ring] = useState(() => new Animated.Value(0));
  const [particles, setParticles] = useState<Particle[]>([]);

  const pressIn = () => {
    if (disabled) return;
    ring.stopAnimation();
    ring.setValue(1);
  };
  const pressOut = () => {
    Animated.timing(ring, { toValue: 0, duration: 380, easing: Easing.out(Easing.quad), useNativeDriver: native }).start();
  };

  const press = () => {
    if (disabled) return;
    scale.stopAnimation();
    scale.setValue(0.82);
    Animated.spring(scale, { toValue: 1, friction: 3, tension: 260, useNativeDriver: native }).start();

    const particle: Particle = { id: Date.now() + Math.random(), value: new Animated.Value(0), drift: (Math.random() - 0.5) * 36 };
    setParticles((list) => [...list.slice(-8), particle]);
    Animated.timing(particle.value, { toValue: 1, duration: 750, easing: Easing.out(Easing.cubic), useNativeDriver: native }).start(() =>
      setParticles((list) => list.filter((p) => p.id !== particle.id)),
    );
    onTap(emoji);
  };

  return (
    <Pressable onPress={press} onPressIn={pressIn} onPressOut={pressOut} accessibilityRole="button" accessibilityLabel={`${emoji} 반응 보내기`} hitSlop={4}>
      <Animated.View style={[styles.button, { transform: [{ scale }] }]}>
        <Animated.Text style={styles.emoji}>{emoji}</Animated.Text>
        <Animated.View pointerEvents="none" style={[styles.ring, { opacity: ring }]} />
      </Animated.View>
      {particles.map((p) => (
        <Animated.Text
          key={p.id}
          pointerEvents="none"
          style={[
            styles.particle,
            {
              opacity: p.value.interpolate({ inputRange: [0, 0.2, 1], outputRange: [0, 1, 0] }),
              transform: [
                { translateY: p.value.interpolate({ inputRange: [0, 1], outputRange: [0, -86] }) },
                { translateX: p.value.interpolate({ inputRange: [0, 1], outputRange: [0, p.drift] }) },
                { scale: p.value.interpolate({ inputRange: [0, 0.3, 1], outputRange: [0.6, 1.25, 0.9] }) },
              ],
            },
          ]}>
          {emoji}
        </Animated.Text>
      ))}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: { width: 54, height: 54, borderRadius: 27, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.surface },
  ring: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, borderRadius: 27, borderWidth: 2, borderColor: colors.accent },
  emoji: { fontSize: 26 },
  particle: { position: 'absolute', top: 10, left: 0, right: 0, textAlign: 'center', fontSize: 24 },
});
