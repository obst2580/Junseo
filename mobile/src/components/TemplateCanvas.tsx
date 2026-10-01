import { Image } from 'expo-image';
import { forwardRef } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import type { Moment } from '@/lib/api';
import { absoluteUrl } from '@/lib/config';
import { SLOT_CORNER, type Template } from '@/lib/templates';
import { colors } from '@/lib/theme';

/**
 * 템플릿을 width 폭으로 그린다. 저장할 때는 이 뷰를 템플릿 원래 크기로 찍는다 (captureView).
 * 비어 있는 칸에는 번호가 뜬다.
 */
export const TemplateCanvas = forwardRef<View, { template: Template; photos: (Moment | null)[]; width: number }>(
  function TemplateCanvas({ template, photos, width }, ref) {
    const s = width / template.width;
    return (
      <View
        ref={ref}
        collapsable={false}
        style={{ width, height: template.height * s, backgroundColor: template.backgroundColor, overflow: 'hidden' }}>
        {template.background && <Image source={template.background} style={StyleSheet.absoluteFill} contentFit="fill" />}
        {template.slots.map((slot, i) => {
          const photo = photos[i];
          const size = slot.size * s;
          return (
            <View
              key={i}
              style={[
                styles.slot,
                { left: slot.x * s, top: slot.y * s, width: size, height: size, borderRadius: (slot.radius ?? slot.size * SLOT_CORNER) * s },
                // 빈 칸은 어느 바탕(검정·파랑) 위에서도 보이게 밝은 회색 + 초록 점선. 그림 속 예시 사진도 가린다.
                !photo && [styles.empty, { borderWidth: Math.max(2, size * 0.012) }],
              ]}>
              {photo ? (
                <Image source={{ uri: absoluteUrl(photo.imageUrl) }} style={StyleSheet.absoluteFill} contentFit="cover" />
              ) : (
                <Text style={[styles.number, { fontSize: size * 0.22 }]}>{i + 1}</Text>
              )}
            </View>
          );
        })}
        {template.overlay && <Image source={template.overlay} style={StyleSheet.absoluteFill} contentFit="fill" pointerEvents="none" />}
      </View>
    );
  },
);

const styles = StyleSheet.create({
  slot: { position: 'absolute', overflow: 'hidden', backgroundColor: 'rgba(255,255,255,0.08)', alignItems: 'center', justifyContent: 'center' },
  empty: { backgroundColor: '#2e3140', borderStyle: 'dashed', borderColor: colors.accent },
  number: { color: colors.accent, fontWeight: '800' },
});
