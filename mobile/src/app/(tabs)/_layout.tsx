import { Tabs } from 'expo-router/js-tabs';

import { TabBar } from '@/components/TabBar';
import { colors } from '@/lib/theme';

// 홈(사진 모아보기) · 카메라 · 챗. 앱은 가운데 카메라에서 시작한다.
export default function TabsLayout() {
  return (
    <Tabs
      initialRouteName="index"
      tabBar={(props) => <TabBar {...props} />}
      screenOptions={{
        headerStyle: { backgroundColor: colors.bg },
        headerTintColor: colors.text,
        headerTitleAlign: 'left',
        headerTitleStyle: { fontWeight: '800', fontSize: 24 },
        headerShadowVisible: false,
        sceneStyle: { backgroundColor: colors.bg },
      }}>
      <Tabs.Screen name="home" options={{ title: '홈' }} />
      <Tabs.Screen name="index" options={{ title: '카메라', headerShown: false }} />
      <Tabs.Screen name="messages" options={{ title: '챗' }} />
    </Tabs>
  );
}
