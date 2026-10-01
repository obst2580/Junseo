import Ionicons from '@expo/vector-icons/Ionicons';
import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Avatar, Button, ErrorText, Field } from '@/components/ui';
import { api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { colors, radius } from '@/lib/theme';

export default function ProfileScreen() {
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
    <ScrollView style={styles.flex} contentContainerStyle={styles.content}>
      <View style={styles.header}>
        <Avatar id={me.id} name={me.displayName} size={72} />
        <Text style={styles.email}>{me.email}</Text>
      </View>

      <Field label="이름" value={name} onChangeText={setName} maxLength={20} />
      {error && <ErrorText>{error}</ErrorText>}
      <Button title="저장" onPress={save} loading={saving} disabled={!name.trim() || name.trim() === me.displayName} />

      <Pressable style={styles.row} onPress={() => router.push('/widget-guide')}>
        <Ionicons name="apps" size={20} color={colors.accent} />
        <Text style={styles.rowText}>홈 화면에 위젯 추가하기</Text>
        <Ionicons name="chevron-forward" size={18} color={colors.textFaint} />
      </Pressable>
      <Pressable style={styles.row} onPress={() => router.push('/friends')}>
        <Ionicons name="people" size={20} color={colors.accent} />
        <Text style={styles.rowText}>
          친구 관리 ({me.friendCount}/{me.friendLimit})
        </Text>
        <Ionicons name="chevron-forward" size={18} color={colors.textFaint} />
      </Pressable>

      <Button title="로그아웃" variant="danger" onPress={() => void signOut()} style={{ marginTop: 24 }} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg },
  content: { padding: 20, gap: 14, maxWidth: 560, width: '100%', alignSelf: 'center' },
  header: { alignItems: 'center', gap: 10, marginBottom: 12 },
  email: { color: colors.textDim, fontSize: 14 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 16, borderRadius: radius.card, backgroundColor: colors.surface },
  rowText: { flex: 1, color: colors.text, fontSize: 16, fontWeight: '600' },
});
