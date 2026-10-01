import { Platform, StyleSheet, Text, type TextStyle } from 'react-native';

import { FONT_FAMILY, TEXT, type TextFont } from '@/lib/photoLayers';

// 한글을 글자 중간이 아니라 낱말 사이에서 줄바꿈한다 (iOS 는 lineBreakStrategyIOS)
const keepWords = (Platform.OS === 'web' ? { wordBreak: 'keep-all' } : {}) as TextStyle;

/**
 * 사진 위 글자 한 덩어리. 화면에 보이는 그대로 사진에 합성된다 (view-shot).
 * 기본은 흰 글자에 어두운 그림자, 궁서체는 검은 글자에 밝은 그림자 (어두운 사진에서도 보이게).
 */
export function LayerText({ text, font, fontSize, maxWidth }: { text: string; font: TextFont; fontSize: number; maxWidth: number }) {
  return (
    <Text
      lineBreakStrategyIOS="hangul-word"
      style={[
        styles.base,
        keepWords,
        font === 'gungseo' ? styles.gungseo : styles.plain,
        { fontSize, lineHeight: fontSize * TEXT.lineHeight, maxWidth, paddingHorizontal: fontSize * 0.25 },
      ]}>
      {text}
    </Text>
  );
}

const styles = StyleSheet.create({
  base: { textAlign: 'center', textShadowRadius: 6 },
  plain: {
    color: '#fff',
    fontWeight: '800',
    letterSpacing: -0.2,
    textShadowColor: 'rgba(0,0,0,0.55)',
    textShadowOffset: { width: 0, height: 1 },
  },
  // 직접 넣은 글꼴은 굵기를 따로 주면 iOS 가 기본 글꼴로 바꿔 버린다
  gungseo: { fontFamily: FONT_FAMILY.gungseo, color: '#000', textShadowColor: 'rgba(255,255,255,0.6)', textShadowOffset: { width: 0, height: 0 } },
});
