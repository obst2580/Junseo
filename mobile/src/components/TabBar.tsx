import Ionicons from '@expo/vector-icons/Ionicons';
import type { BottomTabBarProps } from 'expo-router/js-tabs';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { api } from '@/lib/api';
import { events } from '@/lib/events';
import { colors, radius } from '@/lib/theme';

type IconName = React.ComponentProps<typeof Ionicons>['name'];

// 라우트 이름 → 아이콘 (선택 / 기본)
const ICONS: Record<string, [IconName, IconName]> = {
  home: ['home', 'home-outline'],
  index: ['camera', 'camera-outline'],
  messages: ['chatbubble-ellipses', 'chatbubble-ellipses-outline'],
};

/** 홈 · 카메라 · 챗. 화면 아래에 떠 있는 둥근 탭 바. 챗에는 안 읽은 메시지 수가 붙는다. */
export function TabBar({ state, descriptors, navigation, insets }: BottomTabBarProps) {
  const [unread, setUnread] = useState(0);
  const loadUnread = useCallback(() => {
    api
      .conversations()
      .then((r) => setUnread(r.items.reduce((sum, c) => sum + c.unreadCount, 0)))
      .catch(() => {});
  }, []);

  useEffect(() => {
    loadUnread();
    return events.on('messages', loadUnread);
  }, [loadUnread, state.index]);

  return (
    <View style={[styles.wrap, { paddingBottom: Math.max(insets.bottom, 12) }]}>
      <View style={styles.bar}>
        {state.routes.map((route, i) => {
          const focused = state.index === i;
          const { options } = descriptors[route.key];
          const [on, off] = ICONS[route.name] ?? ['ellipse', 'ellipse-outline'];
          const label = typeof options.title === 'string' ? options.title : route.name;
          const press = () => {
            const e = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
            if (!focused && !e.defaultPrevented) navigation.navigate(route.name, route.params);
          };
          return (
            <Pressable
              key={route.key}
              onPress={press}
              accessibilityRole="tab"
              accessibilityState={{ selected: focused }}
              accessibilityLabel={label}
              style={({ pressed }) => [styles.tab, pressed && { transform: [{ scale: 0.92 }] }]}>
              <View>
                <Ionicons name={focused ? on : off} size={24} color={focused ? colors.accent : colors.textDim} />
                {route.name === 'messages' && unread > 0 && (
                  <View style={styles.badge}>
                    <Text style={styles.badgeText}>{unread > 99 ? '99+' : unread}</Text>
                  </View>
                )}
              </View>
              <Text style={[styles.label, focused && { color: colors.accent }]}>{label}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { backgroundColor: colors.bg, paddingHorizontal: 14, paddingTop: 6 },
  bar: {
    flexDirection: 'row',
    height: 64,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
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
