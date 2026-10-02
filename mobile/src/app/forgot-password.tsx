import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text } from 'react-native';
import { useHeaderHeight } from 'expo-router/react-navigation';

import { Button, ErrorText, Field } from '@/components/ui';
import { api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { colors } from '@/lib/theme';

/**
 * 비밀번호 찾기: 이메일 → 메일로 받은 6자리 코드 + 새 비밀번호 → 바로 로그인.
 * 가입한 메일인지는 알려 주지 않는다 (서버도 항상 「보냈어요」).
 */
export default function ForgotPasswordScreen() {
  const headerHeight = useHeaderHeight();
  const { resetPassword } = useAuth();
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const send = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.requestPasswordReset(email.trim());
      setSent(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '메일을 보내지 못했어요.');
    } finally {
      setBusy(false);
    }
  };

  const confirm = async () => {
    setBusy(true);
    setError(null);
    try {
      await resetPassword(email, code, password);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '바꾸지 못했어요.');
      setBusy(false);
    }
  };

  return (
    <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={[styles.flex, { paddingTop: headerHeight }]}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <Text style={styles.title}>{sent ? '메일로 받은 코드를\n입력해 주세요' : '가입한 이메일을\n알려 주세요'}</Text>
        <Field
          label="이메일"
          value={email}
          onChangeText={(t) => {
            setEmail(t);
            setSent(false);
          }}
          autoCapitalize="none"
          autoComplete="email"
          keyboardType="email-address"
          placeholder="you@example.com"
        />
        {sent && (
          <>
            <Text style={styles.note}>{email.trim()} 로 6자리 코드를 보냈어요. 15분 동안 쓸 수 있어요. 메일이 안 보이면 스팸함도 확인해 주세요.</Text>
            <Field label="코드" value={code} onChangeText={setCode} keyboardType="number-pad" maxLength={6} placeholder="6자리 숫자" autoComplete="one-time-code" />
            <Field label="새 비밀번호" value={password} onChangeText={setPassword} secureTextEntry autoComplete="new-password" placeholder="8자 이상" />
          </>
        )}
        {error && <ErrorText>{error}</ErrorText>}
        {sent ? (
          <>
            <Button title="비밀번호 바꾸고 로그인" onPress={confirm} loading={busy} disabled={code.trim().length !== 6 || password.length < 8} />
            <Button title="코드 다시 받기" variant="secondary" onPress={send} disabled={busy} />
          </>
        ) : (
          <Button title="코드 받기" onPress={send} loading={busy} disabled={!/\S+@\S+\.\S+/.test(email)} />
        )}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 24, gap: 16, maxWidth: 560, width: '100%', alignSelf: 'center' },
  title: { color: colors.text, fontSize: 26, fontWeight: '800', lineHeight: 34, marginBottom: 8 },
  note: { color: colors.textDim, fontSize: 14, lineHeight: 20 },
});
