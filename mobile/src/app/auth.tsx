import { Redirect, router, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, ErrorText } from '@/components/ui';
import { useAuth } from '@/lib/auth';
import { NATIVE_RETURN } from '@/lib/platformLogin';
import { colors } from '@/lib/theme';

export default function AuthReturn() {
  const { code, state } = useLocalSearchParams<{ code?: string; state?: string }>();
  const { me, finishSignIn } = useAuth();
  const [failure, setFailure] = useState<{ url: string; message: string } | null>(null);
  const url = typeof code === 'string' && typeof state === 'string'
    ? `${NATIVE_RETURN}?code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}` : null;
  const error = url ? (failure?.url === url ? failure.message : null)
    : '로그인 결과가 없어요. 앱에서 다시 로그인해 주세요.';

  useEffect(() => {
    if (me || !url) return;
    let active = true;
    void finishSignIn(url).catch((e: unknown) => {
      if (active) setFailure({ url, message: e instanceof Error ? e.message : '로그인을 완료하지 못했어요. 다시 시작해 주세요.' });
    });
    return () => { active = false; };
  }, [url, me, finishSignIn]);

  // Do not send a signed-in user to /login: that route is removed by Stack.Protected.
  if (me) return <Redirect href={me.needsOnboarding ? '/signup' : '/(tabs)'} />;

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.content}>
        {error ? <>
          <ErrorText>{error}</ErrorText>
          <Button title="다시 로그인하기" onPress={() => router.replace('/login')} />
        </> : <>
          <ActivityIndicator color={colors.accent} />
          <Text style={styles.message}>로그인을 마무리하고 있어요.</Text>
        </>}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  content: { flex: 1, justifyContent: 'center', padding: 24, gap: 20 },
  message: { color: colors.text, textAlign: 'center' },
});
