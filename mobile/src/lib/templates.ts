import type { ImageSourcePropType } from 'react-native';

import type { Quad } from '@/lib/warp';

import { api, type Moment } from '@/lib/api';
import { radius } from '@/lib/theme';

/**
 * 오늘 사진 템플릿.
 * 사진 칸은 카메라 화면과 똑같이 정사각형이고, 모서리 굴곡도 카메라 화면과 같은 비율이다
 * (폭 366 화면에서 모서리 60 → 한 변의 약 16%).
 *
 * 템플릿 하나 = 바탕(색 또는 그림) + 사진 칸 위치. 좌표는 템플릿 크기(px) 기준이다.
 * 그림으로 받은 템플릿은 background 에 넣고, 사진이 들어갈 칸의 위치만 slots 에 적으면 된다.
 */
export const SLOT_CORNER = radius.photo / 366;

/** 정사각형 칸. radius: 그림에 그려진 칸의 모서리(px). 없으면 카메라 화면 비율(SLOT_CORNER). */
export type SquareSlot = { x: number; y: number; size: number; radius?: number };
/**
 * 빛번짐: 흐리게 한 사진을 판보다 grow(px) 만큼 크게 사진 밑에 깐다. 앞장 그림이 판에서 멀어질수록 바탕을 다시 덮어서
 * 사진 색 빛이 판 둘레(테두리 · 벽)에만 은은하게 번진다. blur: 흐림 정도(사진 폭 대비), wash: 흰빛을 섞는 정도(0~1).
 */
export type SlotGlow = { grow: number; blur: number; wash: number };
/**
 * 네 꼭짓점 칸 (왼위 · 오위 · 오아래 · 왼아래). 액자 · 광고판 · 버스 옆면처럼 비스듬히 보이는 판에 사진을 원근으로 붙인다.
 * aspect: 그 판의 실제 가로/세로 — 정사각형 사진을 이 비율로 가운데 잘라 붙인다.
 * grow: 사진을 바깥으로 이만큼(px) 더 키운다. 넘친 부분은 앞장 그림(overlay)의 판 테두리가 덮는다.
 */
export type QuadSlot = { quad: Quad; aspect: number; grow?: number; glow?: SlotGlow };
export type TemplateSlot = SquareSlot | QuadSlot;
export const isQuadSlot = (slot: TemplateSlot): slot is QuadSlot => 'quad' in slot;
export type Template = {
  id: string;
  name: string;
  width: number;
  height: number;
  backgroundColor: string;
  /** 디자인한 템플릿 그림 (사진 칸 자리에 예시 사진이 있어도 된다 — 사진이 덮는다) */
  background?: ImageSourcePropType;
  /** 사진 위에 얹는 그림 (투명 PNG, 템플릿 크기 그대로). 사진 위로 올라가는 제목 글자 등 */
  overlay?: ImageSourcePropType;
  slots: TemplateSlot[];
};

export const TEMPLATES: Template[] = [
  // 뉴스 앵커 옆 화면에 사진 한 장 (2160×2700, 4:5). 칸 위치·모서리는 받은 그림에서 쟀다
  // (칸 101~1096 × 380~1379, 모서리 반지름 약 188). 그림 속 예시 사진을 다 덮도록 1px 넉넉하게.
  {
    id: 'news',
    name: '뉴스',
    width: 2160,
    height: 2700,
    backgroundColor: '#0b1a4a',
    background: require('../../assets/templates/news.jpg'),
    slots: [{ x: 100, y: 379, size: 999, radius: 186 }],
  },
  // 유튜브 썸네일처럼 사진 한 장 + 사진 위 제목 (1600×2000, 4:5). 칸 21~1577 × 75~1634, 모서리 반지름 약 285.
  // 제목 「24시간 동안 뻘짓하기」는 그림에서 글자만 따내고 테두리·그림자를 다시 입혀 사진 위에 얹는다.
  {
    id: 'thumbnail',
    name: '썸네일',
    width: 1600,
    height: 2000,
    backgroundColor: '#0f0f0f',
    background: require('../../assets/templates/thumbnail.jpg'),
    overlay: require('../../assets/templates/thumbnail-title.png'),
    slots: [{ x: 20, y: 74, size: 1560, radius: 283 }],
  },
  // 받은 그림들은 scripts/templates/make_templates.py 로 바탕(jpg) + 앞장(png)을 만든다 (칸 좌표 · grow 도 그 스크립트가 낸다).
  // 앞장 = 판 테두리(사진 끝을 덮는 마감) · 조명과 그늘 · 유리/광택 반사 · 이음새 · 카드 글자. 사진은 바탕과 앞장 사이에 들어간다.
  // 미술관: 금박 액자 속 (액자 안쪽 352×494, 5:7). 위에서 비추는 조명 — 액자 윗턱 그림자가 길게 지고 아래로 갈수록 어둡다.
  {
    id: 'museum',
    name: '미술관',
    width: 1226,
    height: 1532,
    backgroundColor: '#7a0a1c',
    background: require('../../assets/templates/museum.jpg'),
    overlay: require('../../assets/templates/museum-overlay.png'),
    slots: [{ quad: [[459, 459], [813, 459], [813, 955], [459, 955]], aspect: 352 / 494 }],
  },
  // 지하철 광고판: 왼쪽에서 비스듬히 본 라이트박스 (받은 그림의 위아래 검은 띠는 잘라 냈다, 2배로).
  // 사진 색 빛이 테두리 · 벽으로 번지고(glow), 유리에 천장 불빛 줄무늬가 비친다.
  {
    id: 'subway',
    name: '지하철 광고',
    width: 1344,
    height: 1040,
    backgroundColor: '#151515',
    background: require('../../assets/templates/subway.jpg'),
    overlay: require('../../assets/templates/subway-overlay.png'),
    slots: [
      {
        quad: [[207, 264.6], [1180.6, 187.2], [1178, 832], [207, 749.8]],
        aspect: 2,
        grow: 5,
        glow: { grow: 90, blur: 0.045, wash: 0.35 },
      },
    ],
  },
  // 버스 옆면 광고 2칸 (앞쪽 가로판 · 뒤쪽 세로판). 광고는 반듯한 사각형이고, 그 밖으로 삐져나온 원래 흰 판은 바탕에서 차체 색으로 칠했다.
  // 곡면 명암 · 이음새 홈 · 하늘이 비치는 광택. 받은 그림을 1.5배로.
  {
    id: 'bus',
    name: '버스 광고',
    width: 1268,
    height: 1586,
    backgroundColor: '#8a8a8a',
    background: require('../../assets/templates/bus.jpg'),
    overlay: require('../../assets/templates/bus-overlay.png'),
    slots: [
      { quad: [[61.9, 675.5], [480.5, 670.3], [468.3, 1042.2], [52.1, 933.6]], aspect: 1.55, grow: 2 },
      { quad: [[834.6, 832.3], [1183.5, 861.6], [1166.8, 1371.2], [823.7, 1254]], aspect: 0.85, grow: 2 },
    ],
  },
  // 선수 카드: 카드 그림 자리에 사진. 양옆 · 위는 카드에 스며들며 파란 테두리 빛이 감기고, 아래는 빛줄기 위에서 그늘지며 카드로 넘어간다.
  // 「97 ST」 글자와 아이콘은 사진 위에 남는다. 받은 그림을 2배로.
  {
    id: 'card',
    name: '선수 카드',
    width: 1428,
    height: 1786,
    backgroundColor: '#14182e',
    background: require('../../assets/templates/card.jpg'),
    overlay: require('../../assets/templates/card-overlay.png'),
    slots: [{ quad: [[412, 450], [1022, 450], [1022, 1120], [412, 1120]], aspect: 610 / 670 }],
  },
];

export const findTemplate = (id: string) => TEMPLATES.find((t) => t.id === id) ?? null;

/** 오늘(이 기기 시간으로 0시부터) 내가 찍어 보낸 사진과 받은 사진, 최신순 */
export async function todaysMoments(): Promise<Moment[]> {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const out: Moment[] = [];
  let cursor: string | null = null;
  // 하루치는 많아야 몇십 장이라 몇 쪽이면 끝난다
  for (let page = 0; page < 10; page++) {
    const res = await api.moments({ cursor, limit: 50 });
    for (const m of res.items) {
      if (Date.parse(m.createdAt) < start.getTime()) return out;
      out.push(m);
    }
    if (!res.nextCursor) break;
    cursor = res.nextCursor;
  }
  return out;
}
