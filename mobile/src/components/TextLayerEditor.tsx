import { useRef, useState } from 'react';
import { Modal, Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { cleanLayerText, TEXT } from '@/lib/photoLayers';
import { colors, radius } from '@/lib/theme';

/**
 * 사진에 넣을 글자를 입력하는 화면. 완료·줄바꿈 키·바깥 누르기로 닫는다.
 * 비운 채 닫으면 onDone('') — 고치던 글자라면 지운다.
 */
export function TextLayerEditor({ initial, onDone }: { initial: string; onDone: (text: string) => void }) {
  const insets = useSafeAreaInsets();
  const [text, setText] = useState(initial);
  const closed = useRef(false);
  const finish = () => {
    if (closed.current) return;
    closed.current = true;
    onDone(cleanLayerText(text));
  };

  return (
    <Modal transparent animationType="fade" visible onRequestClose={finish} statusBarTranslucent>
      <View style={styles.root}>
        <Pressable style={StyleSheet.absoluteFill} onPress={finish} accessibilityLabel="닫기" />
        <Pressable onPress={finish} style={[styles.done, { marginTop: insets.top + 10 }]} accessibilityRole="button">
          <Text style={styles.doneText}>완료</Text>
        </Pressable>
        <TextInput
          value={text}
          onChangeText={setText}
          autoFocus
          multiline
          maxLength={TEXT.maxLength}
          returnKeyType="done"
          submitBehavior="blurAndSubmit"
          {...(Platform.OS === 'web' ? { blurOnSubmit: true } : null)}
          onSubmitEditing={finish}
          placeholder="글자를 입력하세요"
          placeholderTextColor="rgba(255,255,255,0.45)"
          selectionColor={colors.accent}
          accessibilityLabel="사진에 넣을 글자"
          style={styles.input}
        />
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: 'rgba(0,0,0,0.62)' },
  done: { alignSelf: 'flex-end', marginRight: 16, height: 36, paddingHorizontal: 18, borderRadius: radius.pill, backgroundColor: colors.accent, justifyContent: 'center' },
  doneText: { color: colors.accentText, fontSize: 15, fontWeight: '800' },
  input: {
    marginTop: '30%',
    marginHorizontal: 20,
    color: '#fff',
    fontSize: 28,
    fontWeight: '800',
    lineHeight: 32,
    textAlign: 'center',
    letterSpacing: -0.2,
    textShadowColor: 'rgba(0,0,0,0.55)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 6,
  },
});
