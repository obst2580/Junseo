import type { ImageSourcePropType } from 'react-native';

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

/** radius: 그림에 그려진 칸의 모서리(px). 없으면 카메라 화면 비율(SLOT_CORNER). */
export type TemplateSlot = { x: number; y: number; size: number; radius?: number };
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
