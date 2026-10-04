import { memo, useEffect, useMemo, useState } from 'react';

import { warpTiles, type Quad } from '@/lib/warp';

type Props = {
  uri: string;
  quad: Quad;
  aspect: number;
  grid?: number;
  /** 빛번짐용: 흐림 정도(사진 폭 대비)와 흰빛 섞기(0~1) */
  blur?: number;
  wash?: number;
};

const SOFT_WIDTH = 256;

/**
 * 흐리고 밝게 만든 사진 (빛번짐용). 웹 저장(html2canvas)은 CSS filter 를 그리지 않아서 그림 자체를 캔버스로 만든다.
 * 캔버스 filter 가 없는 브라우저는 작게 줄였다 키워서 흐린다.
 */
function softened(img: HTMLImageElement, blur: number, wash: number): string {
  const ratio = img.naturalWidth / img.naturalHeight;
  const W = SOFT_WIDTH;
  const H = Math.max(1, Math.round(W / ratio));
  const c = document.createElement('canvas');
  c.width = W;
  c.height = H;
  const ctx = c.getContext('2d');
  if (!ctx) throw new Error('no canvas');
  if (blur) {
    const px = blur * W;
    ctx.filter = `blur(${px}px)`;
    if (ctx.filter !== 'none') {
      // 가장자리까지 사진으로 채우도록 조금 크게 그린다 (흐림이 바깥의 투명을 끌어오지 않게)
      ctx.drawImage(img, -2 * px, -2 * px, W + 4 * px, H + 4 * px);
      ctx.filter = 'none';
    } else {
      const s = document.createElement('canvas');
      s.width = Math.max(2, Math.round(1 / blur / 1.5));
      s.height = Math.max(2, Math.round(s.width / ratio));
      s.getContext('2d')?.drawImage(img, 0, 0, s.width, s.height);
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(s, 0, 0, W, H);
    }
  } else {
    ctx.drawImage(img, 0, 0, W, H);
  }
  if (wash) {
    ctx.fillStyle = `rgba(255,255,255,${wash})`;
    ctx.fillRect(0, 0, W, H);
  }
  return c.toDataURL('image/jpeg', 0.85);
}

/**
 * 웹: 조각마다 사진을 그 조각 자신의 배경 그림으로 그린다.
 * 웹 저장(html2canvas)은 변형된 칸 「안」의 그림에 변형을 두 번 걸어서, 그림을 자식으로 두면 저장본이 틀어진다.
 */
export const WarpedPhoto = memo(function WarpedPhoto({ uri, quad, aspect, grid = 10, blur = 0, wash = 0 }: Props) {
  const soft = blur > 0 || wash > 0;
  const [ratio, setRatio] = useState(1);
  const [src, setSrc] = useState<string | null>(soft ? null : uri);
  useEffect(() => {
    let alive = true;
    const img = new window.Image();
    if (soft) img.crossOrigin = 'anonymous';
    img.onload = () => {
      if (!alive || !img.naturalWidth || !img.naturalHeight) return;
      setRatio(img.naturalWidth / img.naturalHeight);
      if (!soft) return setSrc(uri);
      try {
        setSrc(softened(img, blur, wash));
      } catch {
        setSrc(null); // 캔버스로 읽을 수 없는 그림이면 빛번짐 없이 둔다
      }
    };
    img.src = uri;
    return () => {
      alive = false;
    };
  }, [uri, soft, blur, wash]);
  const warp = useMemo(() => warpTiles(quad, aspect, ratio, grid), [quad, aspect, ratio, grid]);
  if (!src) return null;
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
            backgroundImage: `url("${src}")`,
            backgroundRepeat: 'no-repeat',
            backgroundSize: `${warp.photoW}px ${warp.photoH}px`,
            backgroundPosition: `${t.left}px ${t.top}px`,
          }}
        />
      ))}
    </div>
  );
});
