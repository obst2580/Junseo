import { Image } from 'expo-image';
import { forwardRef } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import type { Moment } from '@/lib/api';
import { absoluteUrl } from '@/lib/config';
import { SLOT_CORNER, type Template } from '@/lib/templates';

/**
 * 템플릿을 width 폭으로 그린다. 저장할 때는 이 뷰를 템플릿 원래 크기로 찍는다 (captureView).
 * 비어 있는 칸에는 번호가 뜬다.
 */
export const TemplateCanvas = forwardRef<View, { template: Template; photos: (Moment | null)[]; width: number; date?: Date }>(
  function TemplateCanvas({ template, photos, width, date = new Date() }, ref) {
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
              style={[styles.slot, { left: slot.x * s, top: slot.y * s, width: size, height: size, borderRadius: size * SLOT_CORNER }]}>
              {photo ? (
                <Image source={{ uri: absoluteUrl(photo.imageUrl) }} style={StyleSheet.absoluteFill} contentFit="cover" />
              ) : (
                <Text style={[styles.number, { fontSize: size * 0.22 }]}>{i + 1}</Text>
              )}
            </View>
          );
        })}
        {template.decor === 'dateAndLogo' && <DateAndLogo scale={s} date={date} />}
      </View>
    );
  },
);

function DateAndLogo({ scale, date }: { scale: number; date: Date }) {
  return (
    <>
      <Text style={[styles.date, { top: 190 * scale, fontSize: 64 * scale }]}>
        {date.getFullYear()}. {date.getMonth() + 1}. {date.getDate()}.
      </Text>
      <Image
        source={require('../../assets/logo-mark.png')}
        style={{ position: 'absolute', left: (1080 / 2 - 60) * scale, top: 1520 * scale, width: 120 * scale, height: 133 * scale }}
        contentFit="contain"
      />
    </>
  );
}

const styles = StyleSheet.create({
  slot: { position: 'absolute', overflow: 'hidden', backgroundColor: 'rgba(255,255,255,0.08)', alignItems: 'center', justifyContent: 'center' },
  number: { color: 'rgba(255,255,255,0.35)', fontWeight: '800' },
  date: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: '#ffffff', fontWeight: '800', letterSpacing: -0.5 },
});
