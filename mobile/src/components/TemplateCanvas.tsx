import { Image } from 'expo-image';
import { forwardRef } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import Svg, { Polygon } from 'react-native-svg';

import { WarpedPhoto } from '@/components/WarpedPhoto';
import type { Moment } from '@/lib/api';
import { absoluteUrl } from '@/lib/config';
import { isQuadSlot, SLOT_CORNER, type Template } from '@/lib/templates';
import { growQuad, type Quad } from '@/lib/warp';
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
          if (isQuadSlot(slot)) {
            const quad = slot.quad.map(([x, y]) => [x * s, y * s]) as unknown as Quad;
            return photo ? (
              <WarpedPhoto key={i} uri={absoluteUrl(photo.imageUrl)} quad={growQuad(quad, (slot.grow ?? 0) * s)} aspect={slot.aspect} />
            ) : (
              <EmptyQuad key={i} quad={quad} number={i + 1} width={width} height={template.height * s} />
            );
          }
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

/** 비스듬한 빈 칸: 밝은 회색 + 초록 점선 + 번호 (네모 칸과 같은 모양새) */
function EmptyQuad({ quad, number, width, height }: { quad: Quad; number: number; width: number; height: number }) {
  const cx = (quad[0][0] + quad[1][0] + quad[2][0] + quad[3][0]) / 4;
  const cy = (quad[0][1] + quad[1][1] + quad[2][1] + quad[3][1]) / 4;
  const span = Math.min(Math.hypot(quad[1][0] - quad[0][0], quad[1][1] - quad[0][1]), Math.hypot(quad[3][0] - quad[0][0], quad[3][1] - quad[0][1]));
  const stroke = Math.max(2, span * 0.012);
  return (
    <>
      <Svg width={width} height={height} style={StyleSheet.absoluteFill} pointerEvents="none">
        <Polygon
          points={quad.map(([x, y]) => `${x},${y}`).join(' ')}
          fill="#2e3140"
          stroke={colors.accent}
          strokeWidth={stroke}
          strokeDasharray={`${stroke * 3},${stroke * 2}`}
          strokeLinejoin="round"
        />
      </Svg>
      <View pointerEvents="none" style={[styles.quadNumber, { left: cx - span / 2, top: cy - span / 2, width: span, height: span }]}>
        <Text style={[styles.number, { fontSize: span * 0.22 }]}>{number}</Text>
      </View>
    </>
  );
}

const styles = StyleSheet.create({
  quadNumber: { position: 'absolute', alignItems: 'center', justifyContent: 'center' },
  slot: { position: 'absolute', overflow: 'hidden', backgroundColor: 'rgba(255,255,255,0.08)', alignItems: 'center', justifyContent: 'center' },
  empty: { backgroundColor: '#2e3140', borderStyle: 'dashed', borderColor: colors.accent },
  number: { color: colors.accent, fontWeight: '800' },
});
