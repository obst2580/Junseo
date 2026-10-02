import { Image } from 'expo-image';
import { memo, useMemo, useState } from 'react';
import { View } from 'react-native';

import { warpTiles, type Quad, type Tile } from '@/lib/warp';

/**
 * 2D 변형 (x, y) → (a·x + c·y + tx, b·x + d·y + ty) 을 이동 · 회전 · 기울임 · 크기로 풀어 쓴다
 * (matrix 는 플랫폼마다 받는 모양이 달라서 쓰지 않는다).
 */
function transformOf({ a, b, c, d, tx, ty }: Tile) {
  const t = Math.atan2(b, a);
  const cos = Math.cos(t), sin = Math.sin(t);
  const sy = -c * sin + d * cos;
  return [
    { translateX: tx },
    { translateY: ty },
    { rotate: `${t}rad` },
    { skewX: `${Math.atan2(c * cos + d * sin, sy)}rad` },
    { scaleX: Math.hypot(a, b) },
    { scaleY: sy },
  ];
}

type Props = {
  uri: string;
  quad: Quad;
  aspect: number;
  grid?: number;
  /** 빛번짐용: 흐림 정도(사진 폭 대비)와 흰빛 섞기(0~1) */
  blur?: number;
  wash?: number;
};

/** 사진을 네 꼭짓점 사각형에 원근으로 붙인다 (계산은 lib/warp.ts). 웹은 WarpedPhoto.web.tsx. */
export const WarpedPhoto = memo(function WarpedPhoto({ uri, quad, aspect, grid = 10, blur = 0, wash = 0 }: Props) {
  // 사진의 가로/세로 (카메라 사진은 정사각형, 템플릿 결과물은 4:5 등) — 불러온 뒤 알아낸다
  const [ratio, setRatio] = useState(1);
  const [srcWidth, setSrcWidth] = useState(1080);
  const warp = useMemo(() => warpTiles(quad, aspect, ratio, grid), [quad, aspect, ratio, grid]);
  return (
    <View pointerEvents="none" style={{ position: 'absolute', left: 0, top: 0 }}>
      {warp.tiles.map((t) => (
        <View
          key={t.key}
          style={{ position: 'absolute', left: 0, top: 0, width: warp.w, height: warp.h, overflow: 'hidden', transformOrigin: 'left top', transform: transformOf(t) }}>
          <Image
            source={{ uri }}
            style={{ position: 'absolute', left: t.left, top: t.top, width: warp.photoW, height: warp.photoH }}
            contentFit="fill"
            // 흐림은 원본 그림에 걸린다 (조각마다 같은 흐린 그림을 나눠 보여 줘서 이음새가 없다)
            blurRadius={blur ? Math.max(1, Math.round(blur * srcWidth)) : undefined}
            onLoad={
              t.key === '0-0'
                ? (e) => {
                    if (!e.source.width || !e.source.height) return;
                    setRatio(e.source.width / e.source.height);
                    setSrcWidth(e.source.width);
                  }
                : undefined
            }
          />
          {wash > 0 && <View style={{ position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, backgroundColor: `rgba(255,255,255,${wash})` }} />}
        </View>
      ))}
    </View>
  );
});
