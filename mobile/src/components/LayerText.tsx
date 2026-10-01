import { Fragment, useState } from 'react';
import { Platform, StyleSheet, Text, View, type TextStyle } from 'react-native';

import { FONT_FAMILY, TALL, TEXT, type TextFont } from '@/lib/photoLayers';

// 한글을 글자 중간이 아니라 낱말 사이에서 줄바꿈한다 (iOS 는 lineBreakStrategyIOS)
const keepWords = (Platform.OS === 'web' ? { wordBreak: 'keep-all' } : {}) as TextStyle;

/**
 * 사진 위 글자 한 덩어리. 화면에 보이는 그대로 사진에 합성된다 (view-shot).
 * 모든 모양이 흰 글자에 어두운 그림자이고 글꼴만 다르다. 길쭉은 세로로 늘린다.
 */
export function LayerText({ text, font, fontSize, maxWidth }: { text: string; font: TextFont; fontSize: number; maxWidth: number }) {
  const style = [styles.base, keepWords, styles[font], { fontSize, lineHeight: fontSize * TEXT.lineHeight, maxWidth, paddingHorizontal: fontSize * 0.25 }];
  if (font === 'tall') return <TallText text={text} style={style} fontSize={fontSize} />;
  return (
    <Text lineBreakStrategyIOS="hangul-word" style={style}>
      {text}
    </Text>
  );
}

/** 세로로 늘린 만큼 위아래 여백을 늘려서, 고르기 테두리와 자리가 보이는 크기에 맞게 한다. */
function TallText({ text, style, fontSize }: { text: string; style: (TextStyle | false)[]; fontSize: number }) {
  const [height, setHeight] = useState(0);
  return (
    <View
      onLayout={(e) => setHeight(e.nativeEvent.layout.height)}
      style={{ transform: [{ scaleY: TALL.stretch }], marginVertical: (height * (TALL.stretch - 1)) / 2 }}>
      <Text lineBreakStrategyIOS="hangul-word" style={style}>
        {text.split(' ').map((word, i) => (
          <Fragment key={i}>
            {i > 0 && <Text style={{ letterSpacing: TALL.wordGap * fontSize }}> </Text>}
            {word}
          </Fragment>
        ))}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  base: {
    color: '#fff',
    fontWeight: '800',
    textAlign: 'center',
    letterSpacing: -0.2,
    textShadowColor: 'rgba(0,0,0,0.55)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 6,
  },
  plain: {},
  // 직접 넣은 글꼴은 굵기를 따로 주면 iOS 가 기본 글꼴로 바꿔 버린다
  gungseo: { fontFamily: FONT_FAMILY.gungseo, fontWeight: 'normal', letterSpacing: 0 },
  tall: { fontFamily: FONT_FAMILY.tall, fontWeight: 'normal', letterSpacing: 0 },
});
