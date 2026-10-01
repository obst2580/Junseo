import { useEffect, useRef, useState } from 'react';
import { Modal, Platform, Pressable, StyleSheet, Text, TextInput, View, type TextStyle } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { NK } from '@/lib/nkText';
import { cleanLayerText, FONT_FAMILY, TEXT, TEXT_FONTS, type TextFont } from '@/lib/photoLayers';
import { colors, radius } from '@/lib/theme';

// 입력하는 동안 보이는 모양 (사진 위 모양과 비슷하게)
const INPUT_LOOK: Record<TextFont, TextStyle> = {
  plain: { fontWeight: '800' },
  nk: { fontFamily: FONT_FAMILY.nk, fontSize: 30, color: NK.color, textShadowColor: 'transparent' },
};
const CHIP_LOOK: Record<TextFont, TextStyle> = {
  plain: { fontWeight: '800' },
  nk: { fontFamily: FONT_FAMILY.nk, fontSize: 16, color: '#ff3b3b' },
};

/**
 * 사진에 넣을 글자를 입력하는 화면. 위에서 글자 모양(기본·북한)을 고른다.
 * 완료·줄바꿈 키·바깥 누르기로 닫는다. 비운 채 닫으면 onDone('') — 고치던 글자라면 지운다.
 */
export function TextLayerEditor({ initial, initialFont, onDone }: { initial: string; initialFont: TextFont; onDone: (text: string, font: TextFont) => void }) {
  const insets = useSafeAreaInsets();
  const [text, setText] = useState(initial);
  const [font, setFont] = useState(initialFont);
  const closed = useRef(false);
  // 열자마자 들어오는 클릭(웹에서 탭 뒤에 따라오는 click)은 바깥 누르기로 치지 않는다
  const backdropReadyAt = useRef(Infinity);
  useEffect(() => {
    backdropReadyAt.current = Date.now() + 300;
  }, []);
  const finish = () => {
    if (closed.current) return;
    closed.current = true;
    onDone(cleanLayerText(text), font);
  };

  return (
    <Modal transparent animationType="fade" visible onRequestClose={finish} statusBarTranslucent>
      <View style={styles.root}>
        <Pressable style={StyleSheet.absoluteFill} onPress={() => Date.now() >= backdropReadyAt.current && finish()} accessibilityLabel="닫기" />
        <View style={[styles.top, { marginTop: insets.top + 10 }]}>
          <View style={styles.chips} accessibilityRole="radiogroup" accessibilityLabel="글자 모양">
            {TEXT_FONTS.map((f) => {
              const on = f.key === font;
              return (
                <Pressable
                  key={f.key}
                  onPress={() => setFont(f.key)}
                  style={[styles.chip, on && styles.chipOn]}
                  accessibilityRole="radio"
                  aria-checked={on}>
                  <Text style={[styles.chipText, CHIP_LOOK[f.key]]}>{f.label}</Text>
                </Pressable>
              );
            })}
          </View>
          <Pressable onPress={finish} style={styles.done} accessibilityRole="button">
            <Text style={styles.doneText}>완료</Text>
          </Pressable>
        </View>
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
          style={[styles.input, INPUT_LOOK[font]]}
        />
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: 'rgba(0,0,0,0.78)' },
  top: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8, marginHorizontal: 16 },
  chips: { flexDirection: 'row', gap: 6 },
  chip: { height: 34, paddingHorizontal: 12, borderRadius: radius.pill, borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.35)', backgroundColor: 'rgba(0,0,0,0.25)', justifyContent: 'center' },
  chipOn: { borderColor: '#fff', backgroundColor: 'rgba(255,255,255,0.18)' },
  chipText: { color: '#fff', fontSize: 15 },
  done: { height: 36, paddingHorizontal: 18, borderRadius: radius.pill, backgroundColor: colors.accent, justifyContent: 'center' },
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
