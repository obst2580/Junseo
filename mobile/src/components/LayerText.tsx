import MaskedView from '@react-native-masked-view/masked-view';
import { LinearGradient } from 'expo-linear-gradient';
import { StyleSheet, Text, View, type TextStyle } from 'react-native';

import { FONT_FAMILY, TEXT, type TextFont } from '@/lib/photoLayers';

const NAVY = '#1b1d5c';
const GRADIENT = ['#2d6bff', '#8f3dff', '#ff3d9e'] as const;
// 테두리 굵기 (글자 크기 대비, 바깥쪽 끝까지)
const WHITE_EDGE = 0.11;
const NAVY_EDGE = 0.17;

// 글자를 둘레로 조금씩 밀어 겹쳐서 테두리를 만든다 (RN 글자에는 외곽선이 없다)
const ring = (r: number) => Array.from({ length: 16 }, (_, i) => [Math.cos((i * Math.PI) / 8) * r, Math.sin((i * Math.PI) / 8) * r] as const);

/**
 * 사진 위 글자 한 덩어리. 화면에 보이는 그대로 사진에 합성된다 (view-shot).
 * 예능 모양은 남색·흰 글자를 둘레로 겹쳐 테두리를 만들고, 그 위에 그라데이션을 글자 모양으로 오려 얹는다.
 * 웹에서는 MaskedView 가 오리지 못해서 그라데이션 가운데 색 한 가지로 보인다.
 */
export function LayerText({ text, font, fontSize, maxWidth }: { text: string; font: TextFont; fontSize: number; maxWidth: number }) {
  const pad = fontSize * NAVY_EDGE;
  const base: TextStyle = { fontSize, lineHeight: fontSize * TEXT.lineHeight, maxWidth, textAlign: 'center', paddingHorizontal: pad, paddingVertical: pad / 2 };

  if (font === 'chollima') {
    return <Text style={[base, styles.chollima]}>{text}</Text>;
  }
  if (font !== 'variety') {
    return <Text style={[base, styles.plain]}>{text}</Text>;
  }
  const face = [base, styles.variety];
  return (
    <View>
      {/* 자리 잡기용. 겹치는 글자들이 이 크기와 줄바꿈을 그대로 따른다. */}
      <Text style={[face, styles.hidden]}>{text}</Text>
      {ring(fontSize * NAVY_EDGE).map(([dx, dy], i) => (
        <Text key={`n${i}`} style={[face, styles.layer, { color: NAVY, transform: [{ translateX: dx }, { translateY: dy }] }]}>
          {text}
        </Text>
      ))}
      {ring(fontSize * WHITE_EDGE).map(([dx, dy], i) => (
        <Text key={`w${i}`} style={[face, styles.layer, { color: '#fff', transform: [{ translateX: dx }, { translateY: dy }] }]}>
          {text}
        </Text>
      ))}
      <MaskedView
        style={StyleSheet.absoluteFill}
        maskElement={
          <View style={StyleSheet.absoluteFill}>
            <Text style={[face, { color: GRADIENT[1] }]}>{text}</Text>
          </View>
        }>
        <LinearGradient colors={GRADIENT} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={StyleSheet.absoluteFill} />
      </MaskedView>
    </View>
  );
}

const styles = StyleSheet.create({
  plain: {
    color: '#fff',
    fontWeight: '800',
    letterSpacing: -0.2,
    textShadowColor: 'rgba(0,0,0,0.55)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 6,
  },
  // 직접 넣은 글꼴은 굵기를 따로 주면 iOS 가 기본 글꼴로 바꿔 버린다
  variety: { fontFamily: FONT_FAMILY.variety },
  chollima: { fontFamily: FONT_FAMILY.chollima, color: '#e1001a' },
  hidden: { color: 'transparent' },
  layer: { position: 'absolute', top: 0, left: 0, right: 0 },
});
