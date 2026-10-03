import { Image } from 'expo-image';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, ErrorText } from '@/components/ui';
import { useAuth } from '@/lib/auth';
import { colors } from '@/lib/theme';

export default function LoginScreen() {
  const { signIn } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    setError(null);
    setLoading(true);
    try {
      await signIn();
    } catch (e) {
      setError(e instanceof Error ? e.message : '로그인하지 못했어요.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.container}>
        <View style={styles.hero}>
          <Image source={require('../../assets/logo-mark.png')} style={styles.logo} contentFit="contain" accessibilityLabel="로고" />
          <Text style={styles.tagline}>친한 친구의 지금이{'\n'}내 홈 화면에 뜬다</Text>
        </View>
        <View style={styles.form}>
          {error && <ErrorText>{error}</ErrorText>}
          <Button title="리리플레닛으로 로그인" onPress={submit} loading={loading} />
          <Text style={styles.link}>리리플레닛 계정으로 시작해요.{'\n'}계정이 없으면 로그인 화면에서 가입할 수 있어요.</Text>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  container: { flex: 1, paddingHorizontal: 24, justifyContent: 'center', gap: 40 },
  hero: { gap: 12 },
  // 로고 글자 (assets/logo-mark.png, 앱 아이콘과 같은 그림)
  logo: { width: 72, height: 80 },
  tagline: { color: colors.text, fontSize: 24, fontWeight: '700', lineHeight: 32 },
  form: { gap: 14 },
  link: { color: colors.textDim, fontSize: 15, textAlign: 'center', paddingVertical: 8 },
});
