import { useState } from 'react';
import { KeyboardAvoidingView, Linking, Platform, ScrollView, StyleSheet, Text } from 'react-native';
import { useHeaderHeight } from 'expo-router/react-navigation';

import { Button, ErrorText, Field } from '@/components/ui';
import { api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { legalUrl } from '@/lib/config';
import { colors } from '@/lib/theme';

export default function SignupScreen() {
  // 헤더가 바탕(그라데이션) 위에 투명하게 떠 있어서 그만큼 내려서 시작한다
  const headerHeight = useHeaderHeight();
  const { setMe, signOut } = useAuth();
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const valid = displayName.trim().length >= 1 && displayName.trim().length <= 20;

  const submit = async () => {
    setError(null);
    setLoading(true);
    try {
      setMe(await api.updateMe(displayName.trim()));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '이름을 저장하지 못했어요.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={[styles.flex, { paddingTop: headerHeight }]}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Text style={styles.title}>친구들이 부를{'\n'}이름을 알려 주세요</Text>
        <Field label="이름" value={displayName} onChangeText={setDisplayName} placeholder="예: 준서" maxLength={20} />
        {error && <ErrorText>{error}</ErrorText>}
        <Button title="시작하기" onPress={submit} loading={loading} disabled={!valid} />
        <Button title="다른 계정으로 로그인" onPress={() => void signOut()} disabled={loading} />
        <Text style={styles.agree}>
          가입하면{' '}
          <Text style={styles.agreeLink} onPress={() => void Linking.openURL(legalUrl('terms'))} accessibilityRole="link">
            이용약관
          </Text>
          과{' '}
          <Text style={styles.agreeLink} onPress={() => void Linking.openURL(legalUrl('privacy'))} accessibilityRole="link">
            개인정보처리방침
          </Text>
          에 동의하게 돼요. 불쾌한 콘텐츠와 괴롭힘은 허용하지 않아요. 만 14세 이상만 가입할 수 있어요.
        </Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  container: { padding: 24, gap: 16 },
  title: { color: colors.text, fontSize: 26, fontWeight: '800', lineHeight: 34, marginBottom: 12 },
  agree: { color: colors.textDim, fontSize: 13, lineHeight: 19, textAlign: 'center' },
  agreeLink: { color: colors.text, textDecorationLine: 'underline' },
});
