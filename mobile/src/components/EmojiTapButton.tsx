import { useState } from 'react';
import { Animated, Easing, Platform, Pressable, StyleSheet, Text, View } from 'react-native';

import { colors } from '@/lib/theme';

const native = Platform.OS !== 'web';

type Particle = { id: number; value: Animated.Value; drift: number };

/**
 * 여러 번 누를 수 있는 이모지 버튼. 누를 때마다 톡 튀고, 같은 이모지가 하나씩 떠올라 사라진다.
 * 내가 누른 횟수는 오른쪽 위 배지로 보여준다.
 */
export function EmojiTapButton({ emoji, mine, disabled, onTap }: { emoji: string; mine: number; disabled?: boolean; onTap: (emoji: string) => void }) {
  const [scale] = useState(() => new Animated.Value(1));
  const [particles, setParticles] = useState<Particle[]>([]);

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
    <Pressable onPress={press} accessibilityRole="button" accessibilityLabel={`${emoji} 반응, 지금까지 ${mine}번`} hitSlop={4}>
      <Animated.View style={[styles.button, mine > 0 && styles.active, { transform: [{ scale }] }]}>
        <Text style={styles.emoji}>{emoji}</Text>
        {mine > 0 && (
          <View style={styles.badge}>
            <Text style={styles.badgeText}>{mine}</Text>
          </View>
        )}
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
  active: { backgroundColor: colors.surfaceHigh, borderWidth: 2, borderColor: colors.accent },
  emoji: { fontSize: 26 },
  badge: {
    position: 'absolute',
    top: -4,
    right: -4,
    minWidth: 20,
    height: 20,
    borderRadius: 10,
    paddingHorizontal: 5,
    backgroundColor: colors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { color: colors.accentText, fontSize: 11, fontWeight: '800', fontVariant: ['tabular-nums'] },
  particle: { position: 'absolute', top: 10, left: 0, right: 0, textAlign: 'center', fontSize: 24 },
});
