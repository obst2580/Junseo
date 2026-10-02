import { router } from 'expo-router';
import { useState } from 'react';
import { Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useHeaderHeight } from 'expo-router/react-navigation';

import { Icon } from '@/components/Icon';
import { Avatar, Button, ErrorText, Field } from '@/components/ui';
import { api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { legalUrl } from '@/lib/config';
import { colors, radius } from '@/lib/theme';

export default function ProfileScreen() {
  // 헤더가 바탕(그라데이션) 위에 투명하게 떠 있어서 그만큼 내려서 시작한다
  const headerHeight = useHeaderHeight();
  const { me, setMe, signOut } = useAuth();
  const [name, setName] = useState(me?.displayName ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (!me) return null;

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      setMe(await api.updateMe(name.trim()));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '저장하지 못했어요.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <ScrollView style={[styles.flex, { paddingTop: headerHeight }]} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <Avatar id={me.id} name={me.displayName} size={72} />
        <Text style={styles.email}>{me.email}</Text>
      </View>

      <Field label="이름" value={name} onChangeText={setName} maxLength={20} />
      {error && <ErrorText>{error}</ErrorText>}
      <Button title="저장" onPress={save} loading={saving} disabled={!name.trim() || name.trim() === me.displayName} />

      <Pressable style={styles.row} onPress={() => router.push('/widget-guide')}>
        <Icon name="apps" size={20} color={colors.accent} />
        <Text style={styles.rowText}>홈 화면에 위젯 추가하기</Text>
        <Icon name="next" size={18} color={colors.textFaint} />
      </Pressable>
      <Pressable style={styles.row} onPress={() => router.push('/friends')}>
        <Icon name="people" size={20} color={colors.accent} />
        <Text style={styles.rowText}>
          친구 관리 ({me.friendCount}/{me.friendLimit})
        </Text>
        <Icon name="next" size={18} color={colors.textFaint} />
      </Pressable>

      <Pressable style={styles.row} onPress={() => router.push('/blocked')} accessibilityRole="button">
        <Icon name="eyeOff" size={20} color={colors.textDim} />
        <Text style={styles.rowText}>차단한 사람</Text>
        <Icon name="next" size={18} color={colors.textFaint} />
      </Pressable>

      <View style={styles.links}>
        <Pressable onPress={() => void Linking.openURL(legalUrl('terms'))} hitSlop={8} accessibilityRole="link">
          <Text style={styles.link}>이용약관</Text>
        </Pressable>
        <Text style={styles.linkDot}>·</Text>
        <Pressable onPress={() => void Linking.openURL(legalUrl('privacy'))} hitSlop={8} accessibilityRole="link">
          <Text style={styles.link}>개인정보처리방침</Text>
        </Pressable>
      </View>

      <Button title="로그아웃" variant="danger" onPress={() => void signOut()} style={{ marginTop: 12 }} />
      <Pressable onPress={() => router.push('/delete-account')} hitSlop={8} accessibilityRole="button" style={styles.deleteRow}>
        <Text style={styles.deleteText}>계정 삭제</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { padding: 20, gap: 14, maxWidth: 560, width: '100%', alignSelf: 'center' },
  header: { alignItems: 'center', gap: 10, marginBottom: 12 },
  email: { color: colors.textDim, fontSize: 14 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 16, borderRadius: radius.card, backgroundColor: colors.surface },
  rowText: { flex: 1, color: colors.text, fontSize: 16, fontWeight: '600' },
  links: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: 8, marginTop: 10 },
  link: { color: colors.textDim, fontSize: 14, textDecorationLine: 'underline' },
  linkDot: { color: colors.textFaint, fontSize: 14 },
  deleteRow: { alignSelf: 'center', paddingVertical: 10 },
  deleteText: { color: colors.textFaint, fontSize: 14 },
});
