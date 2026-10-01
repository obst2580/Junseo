import { GothicA1_800ExtraBold } from '@expo-google-fonts/gothic-a1/800ExtraBold';
import { useFonts } from 'expo-font';
import { DarkTheme, Stack, ThemeProvider } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { ActivityIndicator, AppState, View } from 'react-native';

import { ZoomProvider } from '@/components/PinchZoom';
import { AuthProvider, useAuth } from '@/lib/auth';
import { events } from '@/lib/events';
import { listenPush, registerPush } from '@/lib/push';
import { realtime } from '@/lib/realtime';
import { colors, glow, motion } from '@/lib/theme';
import { widgetBridge } from '@/lib/widgetBridge';

const theme = {
  ...DarkTheme,
  colors: { ...DarkTheme.colors, background: colors.bg, card: colors.bg, primary: colors.accent, text: colors.text, border: colors.bg },
};

export default function RootLayout() {
  // 사진에 얹는 글자 모양용: 궁서체(은 궁서, GPL — assets/fonts/README.md) · 길쭉(고딕 A1, OFL).
  // 기다리지 않는다 — 다 불러오기 전에는 기본 글꼴로 보인다.
  useFonts({ UnGungseo: require('../../assets/fonts/UnGungseo.ttf'), GothicA1_800ExtraBold });
  return (
    <ThemeProvider value={theme}>
      <AuthProvider>
        <StatusBar style="light" />
        {/* 사진 상세에서 두 손가락으로 키운 사진을 화면 맨 위에 띄운다 */}
        <ZoomProvider>
          <RootStack />
        </ZoomProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}

function RootStack() {
  const { ready, me } = useAuth();
  const signedIn = !!me;

  useEffect(() => {
    if (!signedIn) return;
    registerPush().catch(() => {});
    // 채팅 실시간 신호 (메시지·읽음). 앱이 뒤로 가면 끊고 앞으로 나오면 다시 잇는다.
    realtime.start();
    const stopPush = listenPush((data) => {
      if (data.type === 'message' || data.type === 'group-message') events.emit('messages');
      else events.emit('moments');
    });
    // 앱이 앞으로 나올 때마다 위젯을 갱신한다. 이때는 WidgetKit 예산이 차감되지 않는다.
    const sub = AppState.addEventListener('change', (state) => {
      if (state !== 'active') return;
      widgetBridge.reload();
      events.emit('moments');
      events.emit('messages');
    });
    return () => {
      stopPush();
      sub.remove();
      realtime.stop();
    };
  }, [signedIn]);

  if (!ready) {
    return (
      <View style={[glow, { flex: 1, alignItems: 'center', justifyContent: 'center' }]}>
        <ActivityIndicator color={colors.accent} />
      </View>
    );
  }

  return (
    <Stack
      screenOptions={{
        // 모든 화면 바탕은 그라데이션. 헤더는 그 위에 투명하게 뜬다 (각 화면이 헤더 높이만큼 내려서 시작).
        headerTransparent: true,
        headerTintColor: colors.text,
        headerTitleStyle: { fontWeight: '700' },
        headerShadowVisible: false,
        headerBackButtonDisplayMode: 'minimal',
        contentStyle: glow,
        // 화면 넘어갈 때 「밀기」, 길이 ×1.8 (iOS 는 simple_push 만 길이를 바꿀 수 있다)
        animation: 'simple_push',
        animationDuration: motion.ms('screen'),
      }}>
      <Stack.Protected guard={signedIn}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false, title: '' }} />
        <Stack.Screen name="moments/[id]" options={{ title: '' }} />
        <Stack.Screen name="friends" options={{ title: '친구' }} />
        <Stack.Screen name="messages/[peerId]" options={{ title: '' }} />
        <Stack.Screen name="messages/group/[id]" options={{ title: '' }} />
        <Stack.Screen name="profile" options={{ title: '내 정보' }} />
        <Stack.Screen name="widget-guide" options={{ title: '위젯 추가하기', presentation: 'modal' }} />
        <Stack.Screen name="templates/index" options={{ title: '오늘 템플릿' }} />
        <Stack.Screen name="templates/[id]" options={{ title: '' }} />
      </Stack.Protected>
      <Stack.Protected guard={!signedIn}>
        <Stack.Screen name="login" options={{ headerShown: false }} />
        <Stack.Screen name="signup" options={{ title: '' }} />
      </Stack.Protected>
    </Stack>
  );
}
