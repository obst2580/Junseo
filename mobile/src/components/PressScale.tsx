import { useState, type ReactNode } from 'react';
import { Animated, Easing, Platform, Pressable, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';

import { motion } from '@/lib/theme';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

/** 저장한 디자인의 「빠르게」 곡선 */
export const motionEasing = Easing.bezier(...motion.curve);

/**
 * 누르는 동안 살짝 줄어드는 버튼 (디자인 랩의 .tap: 3%, 빠르게 · ×1.8).
 * style 은 함수가 아니라 값으로 넘긴다. children 은 ({ pressed }) => … 도 된다.
 */
export function PressScale({
  style,
  children,
  onPressIn,
  onPressOut,
  ...rest
}: Omit<PressableProps, 'style' | 'children'> & { style?: StyleProp<ViewStyle>; children?: PressableProps['children'] | ReactNode }) {
  const [scale] = useState(() => new Animated.Value(1));
  const to = (toValue: number) =>
    Animated.timing(scale, { toValue, duration: motion.ms('press'), easing: motionEasing, useNativeDriver: Platform.OS !== 'web' }).start();
  return (
    <AnimatedPressable
      {...rest}
      onPressIn={(e) => {
        to(motion.press);
        onPressIn?.(e);
      }}
      onPressOut={(e) => {
        to(1);
        onPressOut?.(e);
      }}
      style={[style, { transform: [{ scale }] }]}>
      {children as PressableProps['children']}
    </AnimatedPressable>
  );
}
