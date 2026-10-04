/**
 * 사진을 네 꼭짓점 사각형(액자 · 광고판 · 버스 옆면)에 원근으로 붙이는 계산. 앱(WarpedPhoto.tsx)과 웹(WarpedPhoto.web.tsx)이 같이 쓴다.
 * 사진을 grid×grid 조각으로 나눠 조각마다 2D 변형만 쓴다 — 3D 변형은 웹 저장(html2canvas)이 지원하지 않아서.
 * 조각이 작아서 원근이 매끄럽게 이어지고, 이웃 조각과 살짝 겹쳐 틈이 보이지 않는다.
 */
export type Point = readonly [number, number];
/** 왼쪽 위 · 오른쪽 위 · 오른쪽 아래 · 왼쪽 아래 */
export type Quad = readonly [Point, Point, Point, Point];

/** 단위 정사각형 (u, v ∈ 0~1) → 사각형 네 꼭짓점 으로 보내는 원근 변환 */
function homography(q: Quad) {
  const [[x0, y0], [x1, y1], [x2, y2], [x3, y3]] = q;
  const dx1 = x1 - x2, dx2 = x3 - x2, dx3 = x0 - x1 + x2 - x3;
  const dy1 = y1 - y2, dy2 = y3 - y2, dy3 = y0 - y1 + y2 - y3;
  const det = dx1 * dy2 - dx2 * dy1;
  const g = (dx3 * dy2 - dx2 * dy3) / det;
  const h = (dx1 * dy3 - dx3 * dy1) / det;
  const a = x1 - x0 + g * x1, b = x3 - x0 + h * x3, c = x0;
  const d = y1 - y0 + g * y1, e = y3 - y0 + h * y3, f = y0;
  return (u: number, v: number): Point => {
    const w = g * u + h * v + 1;
    return [(a * u + b * v + c) / w, (d * u + e * v + f) / w];
  };
}

/** 사각형을 중심에서 바깥으로 px 만큼 키운다 (앞장 그림이 덮는 테두리까지 사진이 닿도록) */
export function growQuad(q: Quad, px: number): Quad {
  if (!px) return q;
  const cx = (q[0][0] + q[1][0] + q[2][0] + q[3][0]) / 4;
  const cy = (q[0][1] + q[1][1] + q[2][1] + q[3][1]) / 4;
  return q.map(([x, y]) => {
    const len = Math.hypot(x - cx, y - cy) || 1;
    return [x + ((x - cx) / len) * px * 1.4, y + ((y - cy) / len) * px * 1.4] as Point;
  }) as unknown as Quad;
}

const isRect = (q: Quad) => q[0][1] === q[1][1] && q[2][1] === q[3][1] && q[0][0] === q[3][0] && q[1][0] === q[2][0];

/** 조각 하나: 크기 (w, h) 의 조각을 (a b c d tx ty) 2D 변형으로 옮기고, 그 안에 사진을 (left, top) 만큼 밀어 그린다 */
export type Tile = { key: string; a: number; b: number; c: number; d: number; tx: number; ty: number; left: number; top: number };
export type Warp = { tiles: Tile[]; w: number; h: number; photoW: number; photoH: number };

/**
 * ratio: 사진의 가로/세로. aspect: 판의 가로/세로 — 사진이 더 넓으면 양옆을, 더 길면 위아래를 가운데 기준으로 자른다.
 */
export function warpTiles(quad: Quad, aspect: number, ratio: number, grid = 10): Warp {
  const cw = ratio > aspect ? aspect / ratio : 1;
  const ch = ratio > aspect ? 1 : ratio / aspect;
  const cx = (1 - cw) / 2;
  const cy = (1 - ch) / 2;
  const n = isRect(quad) ? 1 : grid;
  // 조각을 그릴 원래 크기: 사각형에서 가장 긴 변 정도면 충분히 선명하다
  const side = Math.max(Math.hypot(quad[1][0] - quad[0][0], quad[1][1] - quad[0][1]), Math.hypot(quad[3][0] - quad[0][0], quad[3][1] - quad[0][1]));
  const SW = side;
  const SH = side / aspect;
  const photoW = SW / cw;
  const photoH = photoW / ratio;
  const tw = SW / n;
  const th = SH / n;
  const P = homography(quad);
  const tiles: Tile[] = [];
  for (let j = 0; j < n; j++) {
    for (let i = 0; i < n; i++) {
      const [x00, y00] = P(i / n, j / n);
      const [x10, y10] = P((i + 1) / n, j / n);
      const [x01, y01] = P(i / n, (j + 1) / n);
      tiles.push({
        key: `${i}-${j}`,
        a: (x10 - x00) / tw,
        b: (y10 - y00) / tw,
        c: (x01 - x00) / th,
        d: (y01 - y00) / th,
        tx: x00,
        ty: y00,
        left: -(cx * photoW + i * tw),
        top: -(cy * photoH + j * th),
      });
    }
  }
  // 조각 크기 + 이웃과 겹칠 여유 (1px 정도)
  const pad = n > 1 ? 1.2 : 0;
  return { tiles, w: tw + pad, h: th + pad, photoW, photoH };
}
