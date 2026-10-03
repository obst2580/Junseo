import { useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useHeaderHeight } from 'expo-router/react-navigation';

import { Button, ErrorText, Field } from '@/components/ui';
import { ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { colors, radius } from '@/lib/theme';

/** 계정 삭제: 무엇이 지워지는지 알리고, 비밀번호를 한 번 더 받는다. 되돌릴 수 없다. */
export default function DeleteAccountScreen() {
  const headerHeight = useHeaderHeight();
  const { deleteAccount } = useAuth();
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const run = async () => {
    setBusy(true);
    setError(null);
    try {
      // 성공하면 로그아웃 상태가 되어 로그인 화면으로 간다
      await deleteAccount(password);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '삭제하지 못했어요. 잠시 후 다시 해 주세요.');
      setBusy(false);
    }
  };

  const confirm = () => {
    const title = '정말 계정을 삭제할까요?';
    const detail = '사진 · 댓글 · 메시지 · 친구가 모두 지워지고 되돌릴 수 없어요.';
    if (Platform.OS === 'web') {
      if (globalThis.confirm?.(`${title}\n${detail}`)) void run();
      return;
    }
    Alert.alert(title, detail, [
      { text: '취소', style: 'cancel' },
      { text: '삭제', style: 'destructive', onPress: () => void run() },
    ]);
  };

  return (
    <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={[styles.flex, { paddingTop: headerHeight }]}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <Text style={styles.title}>계정을 삭제하면{'\n'}바로 지워져요</Text>
        <View style={styles.box}>
          {[
            '내가 보낸 사진 (친구들의 위젯과 히스토리에서도 사라져요)',
            '내가 단 반응 · 댓글',
            '1:1 메시지와 단챗 메시지',
            '친구 관계와 초대 코드',
          ].map((t) => (
            <Text key={t} style={styles.item}>
              · {t}
            </Text>
          ))}
        </View>
        <Text style={styles.note}>지운 계정은 되돌릴 수 없어요. 같은 이메일로 새로 가입할 수는 있어요.</Text>
        <Field label="비밀번호 확인" value={password} onChangeText={setPassword} secureTextEntry autoComplete="password" placeholder="지금 비밀번호" />
        {error && <ErrorText>{error}</ErrorText>}
        <Button title="계정 삭제" variant="danger" onPress={confirm} loading={busy} disabled={password.length < 8} />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 24, gap: 16, maxWidth: 560, width: '100%', alignSelf: 'center' },
  title: { color: colors.text, fontSize: 24, fontWeight: '800', lineHeight: 32 },
  box: { padding: 16, borderRadius: radius.card, backgroundColor: colors.surface, gap: 6 },
  item: { color: colors.text, fontSize: 15, lineHeight: 22 },
  note: { color: colors.textDim, fontSize: 14, lineHeight: 20 },
});
