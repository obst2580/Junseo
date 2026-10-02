import { memo, useEffect, useMemo, useState } from 'react';

import { warpTiles, type Quad } from '@/lib/warp';

/**
 * 웹: 조각마다 사진을 그 조각 자신의 배경 그림으로 그린다.
 * 웹 저장(html2canvas)은 변형된 칸 「안」의 그림에 변형을 두 번 걸어서, 그림을 자식으로 두면 저장본이 틀어진다.
 */
export const WarpedPhoto = memo(function WarpedPhoto({ uri, quad, aspect, grid = 10 }: { uri: string; quad: Quad; aspect: number; grid?: number }) {
  const [ratio, setRatio] = useState(1);
  useEffect(() => {
    const img = new window.Image();
    img.onload = () => img.naturalWidth && img.naturalHeight && setRatio(img.naturalWidth / img.naturalHeight);
    img.src = uri;
  }, [uri]);
  const warp = useMemo(() => warpTiles(quad, aspect, ratio, grid), [quad, aspect, ratio, grid]);
  return (
    <div style={{ position: 'absolute', left: 0, top: 0, pointerEvents: 'none' }}>
      {warp.tiles.map((t) => (
        <div
          key={t.key}
          style={{
            position: 'absolute',
            left: 0,
            top: 0,
            width: warp.w,
            height: warp.h,
            transformOrigin: '0 0',
            transform: `matrix(${t.a}, ${t.b}, ${t.c}, ${t.d}, ${t.tx}, ${t.ty})`,
            backgroundImage: `url("${uri}")`,
            backgroundRepeat: 'no-repeat',
            backgroundSize: `${warp.photoW}px ${warp.photoH}px`,
            backgroundPosition: `${t.left}px ${t.top}px`,
          }}
        />
      ))}
    </div>
  );
});
