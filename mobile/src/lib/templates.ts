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

export type TemplateSlot = { x: number; y: number; size: number };
export type Template = {
  id: string;
  name: string;
  width: number;
  height: number;
  backgroundColor: string;
  /** 디자인한 템플릿 그림 (사진 칸 자리는 비워 둔 그림) */
  background?: ImageSourcePropType;
  slots: TemplateSlot[];
  /** 그림 대신 코드로 그리는 장식 (날짜 · 로고) */
  decor?: 'dateAndLogo';
};

// 템플릿을 받기 전까지 쓰는 임시 템플릿 하나 (인스타 스토리 9:16, 2×2)
export const TEMPLATES: Template[] = [
  {
    id: 'basic',
    name: '임시 템플릿',
    width: 1080,
    height: 1920,
    backgroundColor: '#0e0d0c',
    slots: [
      { x: 60, y: 360, size: 465 },
      { x: 555, y: 360, size: 465 },
      { x: 60, y: 855, size: 465 },
      { x: 555, y: 855, size: 465 },
    ],
    decor: 'dateAndLogo',
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
