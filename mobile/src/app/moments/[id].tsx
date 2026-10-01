import Ionicons from '@expo/vector-icons/Ionicons';
import * as Haptics from 'expo-haptics';
import { Image } from 'expo-image';
import { router, Stack, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  useWindowDimensions,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Avatar, Empty, ErrorText, IconButton } from '@/components/ui';
import { api, ApiError, type Comment, type MomentDetail } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { absoluteUrl } from '@/lib/config';
import { events } from '@/lib/events';
import { timeAgo } from '@/lib/format';
import { colors, QUICK_EMOJIS, radius } from '@/lib/theme';
import { widgetBridge } from '@/lib/widgetBridge';

type Mode = 'comment' | 'reply';

function confirm(title: string, onConfirm: () => void) {
  if (Platform.OS === 'web') {
    if (globalThis.confirm?.(title)) onConfirm();
    return;
  }
  Alert.alert(title, undefined, [
    { text: '취소', style: 'cancel' },
    { text: '삭제', style: 'destructive', onPress: onConfirm },
  ]);
}

export default function MomentScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const momentId = Number(id);
  const { me } = useAuth();
  const { width } = useWindowDimensions();
  const size = Math.min(width - 24, 520);

  const [moment, setMoment] = useState<MomentDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [mode, setMode] = useState<Mode>('comment');
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [notice, setNotice] = useState<{ text: string; error: boolean } | null>(null);

  const load = useCallback(async () => {
    try {
      setMoment(await api.moment(momentId));
    } catch (e) {
      setError(e instanceof ApiError && e.status === 404 ? '볼 수 없는 사진이에요.' : '불러오지 못했어요.');
    }
  }, [momentId]);

  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );
  useEffect(() => events.on('moments', () => void load()), [load]);

  if (error) return <Empty icon="eye-off-outline" title={error} />;
  if (!moment || !me) return <ActivityIndicator color={colors.accent} style={{ marginTop: 40 }} />;

  const mine = moment.sender.id === me.id;
  const changed = () => {
    widgetBridge.reload();
    events.emit('moments');
  };

  const toggleReaction = async (emoji: string) => {
    Haptics.selectionAsync().catch(() => {});
    try {
      if (moment.myReaction === emoji) await api.unreact(moment.id);
      else await api.react(moment.id, emoji);
      await load();
      changed();
    } catch (e) {
      setNotice({ text: e instanceof ApiError ? e.message : '반응을 남기지 못했어요.', error: true });
    }
  };

  const submit = async () => {
    const body = text.trim();
    if (!body) return;
    setSending(true);
    setNotice(null);
    try {
      if (mode === 'comment') {
        await api.comment(moment.id, body);
        await load();
        changed();
      } else {
        await api.reply(moment.id, body);
        events.emit('messages');
        setNotice({ text: `${moment.sender.displayName}님에게만 보냈어요. 메시지에서 이어서 이야기할 수 있어요.`, error: false });
      }
      setText('');
    } catch (e) {
      setNotice({ text: e instanceof ApiError ? e.message : '보내지 못했어요.', error: true });
    } finally {
      setSending(false);
    }
  };

  const removeComment = (c: Comment) => {
    if (c.author.id !== me.id && !mine) return;
    confirm('댓글을 삭제할까요?', async () => {
      await api.deleteComment(c.id).catch(() => {});
      await load();
      changed();
    });
  };

  const removeMoment = () =>
    confirm('사진을 삭제할까요? 친구들의 위젯과 히스토리에서도 사라져요.', async () => {
      await api.deleteMoment(moment.id).catch(() => {});
      changed();
      router.back();
    });

  return (
    <SafeAreaView style={styles.flex} edges={['bottom']}>
      <Stack.Screen
        options={{
          title: mine ? '내 사진' : moment.sender.displayName,
          headerRight: mine ? () => <IconButton icon="trash-outline" size={20} onPress={removeMoment} label="사진 삭제" /> : undefined,
        }}
      />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.flex} keyboardVerticalOffset={90}>
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <Image source={{ uri: absoluteUrl(moment.imageUrl) }} style={[styles.photo, { width: size, height: size }]} contentFit="cover" transition={150} />

          <View style={styles.senderRow}>
            <Avatar id={moment.sender.id} name={moment.sender.displayName} size={28} />
            <Text style={styles.senderName}>{mine ? '나' : moment.sender.displayName}</Text>
            <Text style={styles.time}>{timeAgo(moment.createdAt)}</Text>
          </View>

          {moment.reactions.length > 0 && (
            <View style={styles.reactionSummary}>
              {moment.reactions.map((r) => (
                <View key={r.emoji} style={styles.reactionPill}>
                  <Text style={styles.reactionEmoji}>{r.emoji}</Text>
                  <Text style={styles.reactionCount}>{r.count}</Text>
                </View>
              ))}
            </View>
          )}

          {!mine && (
            <View style={styles.emojiRow}>
              {QUICK_EMOJIS.map((e) => (
                <Pressable key={e} onPress={() => toggleReaction(e)} style={[styles.emojiButton, moment.myReaction === e && styles.emojiButtonActive]}>
                  <Text style={styles.emoji}>{e}</Text>
                </Pressable>
              ))}
            </View>
          )}

          <View style={styles.comments}>
            <Text style={styles.sectionTitle}>댓글 {moment.comments.length > 0 ? moment.comments.length : ''}</Text>
            {moment.comments.length === 0 && <Text style={styles.dim}>첫 댓글을 남겨 보세요. 친구들 위젯에서 사진 아래에 떠요.</Text>}
            {moment.comments.map((c) => (
              <Pressable key={c.id} onLongPress={() => removeComment(c)} style={styles.comment}>
                <Avatar id={c.author.id} name={c.author.displayName} size={26} />
                <View style={{ flex: 1 }}>
                  <Text style={styles.commentText}>
                    <Text style={styles.commentAuthor}>{c.author.id === me.id ? '나' : c.author.displayName} </Text>
                    {c.text}
                  </Text>
                  <Text style={styles.commentTime}>{timeAgo(c.createdAt)}</Text>
                </View>
              </Pressable>
            ))}
          </View>
        </ScrollView>

        <View style={styles.composer}>
          {!mine && (
            <View style={styles.modeRow}>
              {(['comment', 'reply'] as const).map((m) => (
                <Pressable key={m} onPress={() => setMode(m)} style={[styles.modeChip, mode === m && styles.modeChipActive]}>
                  <Text style={[styles.modeText, mode === m && styles.modeTextActive]}>
                    {m === 'comment' ? '댓글 · 모두에게' : `답장 · ${moment.sender.displayName}님에게만`}
                  </Text>
                </Pressable>
              ))}
            </View>
          )}
          {notice && (notice.error ? <ErrorText>{notice.text}</ErrorText> : <Text style={styles.ok}>{notice.text}</Text>)}
          <View style={styles.inputRow}>
            <TextInput
              value={text}
              onChangeText={setText}
              placeholder={mode === 'comment' ? '댓글 달기' : '답장 보내기'}
              placeholderTextColor={colors.textFaint}
              maxLength={mode === 'comment' ? 100 : 500}
              style={styles.input}
              onSubmitEditing={submit}
              returnKeyType="send"
            />
            <Pressable onPress={submit} disabled={sending || !text.trim()} style={[styles.sendButton, (!text.trim() || sending) && { opacity: 0.4 }]}>
              {sending ? <ActivityIndicator color={colors.accentText} /> : <Ionicons name="arrow-up" size={20} color={colors.accentText} />}
            </Pressable>
          </View>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: colors.bg },
  content: { alignItems: 'center', padding: 12, gap: 14 },
  photo: { borderRadius: radius.photo, backgroundColor: colors.surface },
  senderRow: { flexDirection: 'row', alignItems: 'center', gap: 8, alignSelf: 'stretch', paddingHorizontal: 8 },
  senderName: { color: colors.text, fontSize: 16, fontWeight: '700' },
  time: { color: colors.textDim, fontSize: 14 },
  reactionSummary: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, alignSelf: 'stretch', paddingHorizontal: 8 },
  reactionPill: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 10, height: 30, borderRadius: radius.pill, backgroundColor: colors.surfaceHigh },
  reactionEmoji: { fontSize: 15 },
  reactionCount: { color: colors.text, fontSize: 13, fontWeight: '700' },
  emojiRow: { flexDirection: 'row', justifyContent: 'space-between', alignSelf: 'stretch', paddingHorizontal: 4 },
  emojiButton: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.surface },
  emojiButtonActive: { backgroundColor: colors.accent },
  emoji: { fontSize: 24 },
  comments: { alignSelf: 'stretch', gap: 12, paddingHorizontal: 8, paddingBottom: 12 },
  sectionTitle: { color: colors.text, fontSize: 15, fontWeight: '800' },
  dim: { color: colors.textDim, fontSize: 14 },
  ok: { color: colors.accent, fontSize: 13, textAlign: 'center' },
  comment: { flexDirection: 'row', gap: 10, alignItems: 'flex-start' },
  commentText: { color: colors.text, fontSize: 15, lineHeight: 21 },
  commentAuthor: { fontWeight: '800' },
  commentTime: { color: colors.textFaint, fontSize: 12, marginTop: 2 },
  composer: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.border, padding: 10, gap: 8 },
  modeRow: { flexDirection: 'row', gap: 8 },
  modeChip: { paddingHorizontal: 12, height: 30, justifyContent: 'center', borderRadius: radius.pill, backgroundColor: colors.surface },
  modeChipActive: { backgroundColor: colors.text },
  modeText: { color: colors.textDim, fontSize: 13, fontWeight: '600' },
  modeTextActive: { color: colors.bg },
  inputRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  input: { flex: 1, height: 44, borderRadius: 22, paddingHorizontal: 16, backgroundColor: colors.surface, color: colors.text, fontSize: 15 },
  sendButton: { width: 44, height: 44, borderRadius: 22, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center' },
});
