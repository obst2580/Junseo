import { useCallback, useMemo, useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { colors, radius } from '@/lib/theme';

export type SheetOption = { label: string; destructive?: boolean; onPress: () => void };
export type SheetSpec = { title?: string; message?: string; options: SheetOption[] };

/**
 * 아래에서 올라오는 선택지 (신고 · 차단 메뉴 등). 앱 · 웹이 같은 모양이다.
 * 선택지를 누르면 닫히고 그 동작을 한다 — 그 동작이 다른 시트를 열면 이어서 그 시트가 뜬다.
 */
export function useActionSheet() {
  const insets = useSafeAreaInsets();
  const [spec, setSpec] = useState<SheetSpec | null>(null);
  const close = useCallback(() => setSpec(null), []);
  const element = useMemo(
    () => (
      <Modal visible={!!spec} transparent animationType="fade" onRequestClose={close}>
        <View style={styles.root}>
          <Pressable style={StyleSheet.absoluteFill} onPress={close} accessibilityRole="button" accessibilityLabel="닫기">
            <View style={styles.backdrop} />
          </Pressable>
          <View style={[styles.sheet, { paddingBottom: insets.bottom + 10 }]} accessibilityViewIsModal>
            {(spec?.title || spec?.message) && (
              <View style={styles.head}>
                {spec?.title && <Text style={styles.title}>{spec.title}</Text>}
                {spec?.message && <Text style={styles.message}>{spec.message}</Text>}
              </View>
            )}
            <View style={styles.group}>
              {spec?.options.map((o, i) => (
                <Pressable
                  key={o.label}
                  onPress={() => {
                    setSpec(null);
                    o.onPress();
                  }}
                  style={({ pressed }) => [styles.option, i > 0 && styles.divider, pressed && styles.pressed]}
                  accessibilityRole="button">
                  <Text style={[styles.optionText, o.destructive && styles.destructive]}>{o.label}</Text>
                </Pressable>
              ))}
            </View>
            <Pressable onPress={close} style={({ pressed }) => [styles.group, styles.option, pressed && styles.pressed]} accessibilityRole="button">
              <Text style={styles.cancel}>취소</Text>
            </Pressable>
          </View>
        </View>
      </Modal>
    ),
    [spec, close, insets.bottom],
  );
  return { open: setSpec, close, element };
}

const styles = StyleSheet.create({
  root: { flex: 1, justifyContent: 'flex-end' },
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.55)' },
  sheet: { paddingHorizontal: 10, gap: 8, width: '100%', maxWidth: 560, alignSelf: 'center' },
  head: { backgroundColor: colors.sheet, borderRadius: radius.card, padding: 16, gap: 6 },
  title: { color: colors.text, fontSize: 16, fontWeight: '800', textAlign: 'center' },
  message: { color: colors.textDim, fontSize: 14, lineHeight: 20, textAlign: 'center' },
  group: { backgroundColor: colors.sheet, borderRadius: radius.card, overflow: 'hidden' },
  option: { minHeight: 54, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16 },
  divider: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.border },
  pressed: { backgroundColor: 'rgba(255,255,255,0.08)' },
  optionText: { color: colors.text, fontSize: 17, fontWeight: '600' },
  destructive: { color: colors.danger },
  cancel: { color: colors.text, fontSize: 17, fontWeight: '800' },
});
