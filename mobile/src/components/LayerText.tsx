import { Fragment, useState } from 'react';
import { StyleSheet, Text, View, type TextStyle } from 'react-native';

import { NK, nkWords } from '@/lib/nkText';
import { FONT_FAMILY, TEXT, type TextFont } from '@/lib/photoLayers';

// 글자를 둘레로 조금씩 밀어 겹쳐서 굵게 만든다 (RN 글자에는 외곽선이 없다)
const ring = (r: number) => Array.from({ length: 8 }, (_, i) => [Math.cos((i * Math.PI) / 4) * r, Math.sin((i * Math.PI) / 4) * r] as const);

/**
 * 사진 위 글자 한 덩어리. 화면에 보이는 그대로 사진에 합성된다 (view-shot).
 * fontSize 는 기본 글자 크기, maxWidth 는 줄바꿈 폭.
 */
export function LayerText({ text, font, fontSize, maxWidth }: { text: string; font: TextFont; fontSize: number; maxWidth: number }) {
  if (font === 'nk') return <NkText text={text} big={(fontSize * NK.size) / TEXT.size} maxWidth={maxWidth} />;
  return (
    <Text style={[styles.plain, { fontSize, lineHeight: fontSize * TEXT.lineHeight, maxWidth, paddingHorizontal: fontSize * 0.25 }]}>{text}</Text>
  );
}

/**
 * 북한 구호 글씨. 조각마다 크기·자간이 다른 글자를 한 줄에 이어 쓴다 (같은 기준선).
 * 덩어리 전체를 가로로 condense 배 줄이고, 줄어든 만큼 양옆 여백을 당겨 보이는 폭과 자리를 맞춘다.
 */
function NkText({ text, big, maxWidth }: { text: string; big: number; maxWidth: number }) {
  const [width, setWidth] = useState(0);
  const words = nkWords(text);
  const body = words.map((word, wi) => (
    <Fragment key={wi}>
      {wi > 0 && <Text style={{ fontSize: big, letterSpacing: (NK.gap - NK.spaceWidth) * big }}> </Text>}
      {word.map((p, pi) => {
        const px = big * p.scale;
        const chars = [...p.text];
        // 자간은 글자 뒤에 붙는다. 조각 끝 글자 뒤는 낱말 안이면 기본 자간, 낱말 끝이면 0.
        const tail = pi < word.length - 1 ? NK.track * px : 0;
        return (
          <Fragment key={pi}>
            {chars.length > 1 && <Text style={{ fontSize: px, letterSpacing: p.track * px }}>{chars.slice(0, -1).join('')}</Text>}
            <Text style={{ fontSize: px, letterSpacing: tail }}>{chars.at(-1)}</Text>
          </Fragment>
        );
      })}
    </Fragment>
  ));
  const pad = big * NK.bold * 2;
  const face: TextStyle[] = [styles.nk, { lineHeight: big * NK.lineHeight, maxWidth: maxWidth / NK.condense, padding: pad }];
  return (
    <View
      onLayout={(e) => setWidth(e.nativeEvent.layout.width)}
      style={{ transform: [{ scaleX: NK.condense }], marginHorizontal: (-width * (1 - NK.condense)) / 2 }}>
      <Text style={face}>{body}</Text>
      {ring(big * NK.bold).map(([dx, dy], i) => (
        <Text key={i} style={[face, styles.layer, { transform: [{ translateX: dx }, { translateY: dy }] }]}>
          {body}
        </Text>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  plain: {
    color: '#fff',
    fontWeight: '800',
    textAlign: 'center',
    letterSpacing: -0.2,
    textShadowColor: 'rgba(0,0,0,0.55)',
    textShadowOffset: { width: 0, height: 1 },
    textShadowRadius: 6,
  },
  // 직접 넣은 글꼴은 굵기를 따로 주면 iOS 가 기본 글꼴로 바꿔 버린다
  nk: { fontFamily: FONT_FAMILY.nk, color: NK.color, textAlign: 'center' },
  layer: { position: 'absolute', top: 0, left: 0, right: 0 },
});
