import { useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useHeaderHeight } from 'expo-router/react-navigation';

import { Button, ErrorText, Field } from '@/components/ui';
import { useAuth } from '@/lib/auth';
import { colors, radius } from '@/lib/theme';

/**
 * 계정 삭제: 무엇이 지워지는지 알리고 본인인지 한 번 더 확인한다. 되돌릴 수 없다.
 * 리리플레닛 계정은 비밀번호가 없어서 그 자리에서 리리플레닛으로 다시 로그인한다. 이메일 계정(개발용)은 비밀번호를 받는다.
 */
export default function DeleteAccountScreen() {
  const headerHeight = useHeaderHeight();
  const { me, deleteAccount } = useAuth();
  const platform = me?.loginMethod === 'platform';
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const run = async () => {
    setBusy(true);
    setError(null);
    try {
      // 성공하면 로그아웃 상태가 되어 로그인 화면으로 간다
      await deleteAccount(platform ? undefined : password);
    } catch (e) {
      setError(e instanceof Error && e.message ? e.message : '삭제하지 못했어요. 잠시 후 다시 해 주세요.');
      setBusy(false);
    }
  };

  const confirm = () => {
    const title = '정말 계정을 삭제할까요?';
    const detail = platform
      ? '사진 · 댓글 · 메시지 · 친구가 모두 지워지고 되돌릴 수 없어요. 본인 확인으로 리리플레닛 로그인 창이 열려요.'
      : '사진 · 댓글 · 메시지 · 친구가 모두 지워지고 되돌릴 수 없어요.';
    if (Platform.OS === 'web') {
      if (globalThis.confirm?.(`${title}\n${detail}`)) void run();
      return;
    }
    Alert.alert(title, detail, [
      { text: '취소', style: 'cancel' },
      { text: platform ? '확인하고 삭제' : '삭제', style: 'destructive', onPress: () => void run() },
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
        {platform ? (
          <Text style={styles.note}>
            지운 계정은 되돌릴 수 없어요. 리리플레닛 계정은 그대로 남아서, 나중에 다시 로그인하면 빈 계정으로 새로 시작해요.{'\n\n'}
            본인인지 확인하려고 삭제 직전에 리리플레닛으로 한 번 더 로그인해요.
          </Text>
        ) : (
          <>
            <Text style={styles.note}>지운 계정은 되돌릴 수 없어요. 같은 이메일로 새로 가입할 수는 있어요.</Text>
            <Field label="비밀번호 확인" value={password} onChangeText={setPassword} secureTextEntry autoComplete="password" placeholder="지금 비밀번호" />
          </>
        )}
        {error && <ErrorText>{error}</ErrorText>}
        <Button
          title={platform ? '리리플레닛으로 확인하고 삭제' : '계정 삭제'}
          variant="danger"
          onPress={confirm}
          loading={busy}
          disabled={!platform && password.length < 8}
        />
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
