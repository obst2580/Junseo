import { BlurView } from 'expo-blur';
import type { BottomTabBarProps } from 'expo-router/js-tabs';
import { useCallback, useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { PressScale } from '@/components/PressScale';
import { Icon, type IconName } from '@/components/Icon';
import { api } from '@/lib/api';
import { events } from '@/lib/events';
import { colors, radius } from '@/lib/theme';

// 라우트 이름 → 아이콘 (고른 탭은 강조색)
const ICONS: Record<string, IconName> = { home: 'home', index: 'camera', messages: 'chat' };

const BAR_HEIGHT = 64;
const BAR_TOP = 6;
const bottomGap = (inset: number) => Math.max(inset, 12);

/**
 * 탭 바는 화면 위에 떠 있다 (디자인 랩처럼 바탕 그라데이션이 탭 바 뒤까지 이어진다).
 * 탭 화면은 내용이 탭 바에 가리지 않게 이만큼 아래를 비운다.
 */
export function useTabBarSpace() {
  return BAR_TOP + BAR_HEIGHT + bottomGap(useSafeAreaInsets().bottom);
}

/** 홈 · 카메라 · 챗. 화면 아래에 떠 있는 둥근 탭 바. 챗에는 안 읽은 메시지 수가 붙는다. */
export function TabBar({ state, descriptors, navigation, insets }: BottomTabBarProps) {
  const [unread, setUnread] = useState(0);
  const loadUnread = useCallback(() => {
    api
      .conversations()
      .then((r) => setUnread([...r.items, ...r.groups].reduce((sum, c) => sum + c.unreadCount, 0)))
      .catch(() => {});
  }, []);

  useEffect(() => {
    loadUnread();
    return events.on('messages', loadUnread);
  }, [loadUnread, state.index]);

  return (
    <View style={[styles.wrap, { paddingBottom: bottomGap(insets.bottom) }]}>
      {/* 뒤에 지나가는 사진이 비치되 흐릿하게 (디자인 랩의 blur(20px)) */}
      <BlurView intensity={40} tint="dark" style={styles.bar}>
        {state.routes.map((route, i) => {
          const focused = state.index === i;
          const { options } = descriptors[route.key];
          const name = ICONS[route.name] ?? 'checkOff';
          const label = typeof options.title === 'string' ? options.title : route.name;
          const press = () => {
            const e = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
            if (!focused && !e.defaultPrevented) navigation.navigate(route.name, route.params);
          };
          return (
            <PressScale
              key={route.key}
              onPress={press}
              accessibilityRole="tab"
              accessibilityState={{ selected: focused }}
              accessibilityLabel={label}
              style={styles.tab}>
              <View>
                <Icon name={name} size={24} color={focused ? colors.accent : colors.textDim} />
                {route.name === 'messages' && unread > 0 && (
                  <View style={styles.badge}>
                    <Text style={styles.badgeText}>{unread > 99 ? '99+' : unread}</Text>
                  </View>
                )}
              </View>
              <Text style={[styles.label, focused && { color: colors.accent }]}>{label}</Text>
            </PressScale>
          );
        })}
      </BlurView>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { position: 'absolute', left: 0, right: 0, bottom: 0, paddingHorizontal: 14, paddingTop: BAR_TOP },
  bar: {
    flexDirection: 'row',
    height: BAR_HEIGHT,
    borderRadius: radius.pill,
    overflow: 'hidden',
    backgroundColor: colors.bar,
    borderWidth: StyleSheet.hairlineWidth,
    borderColor: colors.border,
  },
  tab: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 3 },
  label: { color: colors.textDim, fontSize: 11, fontWeight: '700' },
  badge: {
    position: 'absolute',
    top: -4,
    right: -10,
    minWidth: 17,
    height: 17,
    borderRadius: 9,
    paddingHorizontal: 4,
    backgroundColor: colors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { color: colors.accentText, fontSize: 10.5, fontWeight: '800' },
});
