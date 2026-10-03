import { Tabs } from 'expo-router/js-tabs';

import { TabBar } from '@/components/TabBar';
import { motionEasing } from '@/components/PressScale';
import { colors, glow, motion } from '@/lib/theme';

// 홈(사진 모아보기) · 카메라 · 챗. 앱은 가운데 카메라에서 시작한다.
export default function TabsLayout() {
  return (
    <Tabs
      initialRouteName="index"
      tabBar={(props) => <TabBar {...props} />}
      screenOptions={{
        // 탭 화면마다 그라데이션 바탕을 깐다 (헤더는 그 위에 투명하게, 탭 바는 떠 있게).
        // 투명하게 두면 전에 열었던 탭이 뒤에 비친다.
        headerStyle: { backgroundColor: 'transparent' },
        headerTintColor: colors.text,
        headerTitleAlign: 'left',
        headerTitleStyle: { fontWeight: '800', fontSize: 24 },
        headerShadowVisible: false,
        sceneStyle: glow,
        // 탭을 옮길 때도 옆으로 밀린다 (빠르게 · ×1.8)
        animation: 'shift',
        transitionSpec: { animation: 'timing', config: { duration: motion.ms('screen'), easing: motionEasing } },
      }}>
      <Tabs.Screen name="home" options={{ title: '홈' }} />
      <Tabs.Screen name="index" options={{ title: '카메라', headerShown: false }} />
      <Tabs.Screen name="messages" options={{ title: '챗' }} />
    </Tabs>
  );
}
