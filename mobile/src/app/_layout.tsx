import { DarkTheme, Stack, ThemeProvider } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { ActivityIndicator, AppState, View } from 'react-native';

import { AuthProvider, useAuth } from '@/lib/auth';
import { events } from '@/lib/events';
import { listenPush, registerPush } from '@/lib/push';
import { colors } from '@/lib/theme';
import { widgetBridge } from '@/lib/widgetBridge';

const theme = {
  ...DarkTheme,
  colors: { ...DarkTheme.colors, background: colors.bg, card: colors.bg, primary: colors.accent, text: colors.text, border: colors.bg },
};

export default function RootLayout() {
  return (
    <ThemeProvider value={theme}>
      <AuthProvider>
        <StatusBar style="light" />
        <RootStack />
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
    const stopPush = listenPush((data) => {
      if (data.type === 'message') events.emit('messages');
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
    };
  }, [signedIn]);

  if (!ready) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator color={colors.accent} />
      </View>
    );
  }

  return (
    <Stack
      screenOptions={{
        headerStyle: { backgroundColor: colors.bg },
        headerTintColor: colors.text,
        headerTitleStyle: { fontWeight: '700' },
        headerShadowVisible: false,
        headerBackButtonDisplayMode: 'minimal',
        contentStyle: { backgroundColor: colors.bg },
      }}>
      <Stack.Protected guard={signedIn}>
        <Stack.Screen name="(tabs)" options={{ headerShown: false, title: '' }} />
        <Stack.Screen name="moments/[id]" options={{ title: '' }} />
        <Stack.Screen name="friends" options={{ title: '친구' }} />
        <Stack.Screen name="messages/[peerId]" options={{ title: '' }} />
        <Stack.Screen name="profile" options={{ title: '내 정보' }} />
        <Stack.Screen name="widget-guide" options={{ title: '위젯 추가하기', presentation: 'modal' }} />
      </Stack.Protected>
      <Stack.Protected guard={!signedIn}>
        <Stack.Screen name="login" options={{ headerShown: false }} />
        <Stack.Screen name="signup" options={{ title: '' }} />
      </Stack.Protected>
    </Stack>
  );
}
