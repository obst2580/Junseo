import { Image } from 'expo-image';
import { Link } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, ErrorText, Field } from '@/components/ui';
import { ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { colors } from '@/lib/theme';

export default function LoginScreen() {
  const { signIn } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    setError(null);
    setLoading(true);
    try {
      await signIn(email, password);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '로그인하지 못했어요.');
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
          <Field
            label="이메일"
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
            autoComplete="email"
            keyboardType="email-address"
            placeholder="you@example.com"
          />
          <Field label="비밀번호" value={password} onChangeText={setPassword} secureTextEntry autoComplete="password" placeholder="8자 이상" onSubmitEditing={submit} />
          {error && <ErrorText>{error}</ErrorText>}
          <Button title="로그인" onPress={submit} loading={loading} disabled={!email || password.length < 8} />
          <Link href="/signup" style={styles.link}>
            처음이에요 · 가입하기
          </Link>
          <Link href="/forgot-password" style={[styles.link, styles.small]}>
            비밀번호를 잊었어요
          </Link>
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
  small: { fontSize: 14, color: colors.textFaint, paddingVertical: 2 },
});
