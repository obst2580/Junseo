/**
 * 찍은 사진 위에 얹는 요소 (눈 가리개 · 텍스트). 보낼 때 사진에 합성된다.
 * 위치(x, y: 가운데)와 크기는 사진 한 변에 대한 비율이라 화면 크기와 상관없다. rotation 은 도(°).
 */
type Base = { id: number; x: number; y: number; scale: number; rotation: number };
export type PhotoLayer = (Base & { kind: 'bar' }) | (Base & { kind: 'text'; text: string; font: TextFont });

/**
 * 글자 모양
 * - plain: 흰 글자 + 그림자
 * - nk: 북한 선전 구호 글씨 (lib/nkText)
 */
export type TextFont = 'plain' | 'nk';
export const TEXT_FONTS: { key: TextFont; label: string }[] = [
  { key: 'plain', label: '기본' },
  { key: 'nk', label: '북한' },
];
/** useFonts 로 불러온 이름 (루트 레이아웃) */
export const FONT_FAMILY = { nk: 'UnGungseo' } as const;

/** 기생충 포스터 같은 검은 눈 가리개 */
export const BAR = { width: 0.64, height: 0.07 };
export const TEXT = { size: 0.075, maxWidth: 0.86, lineHeight: 1.15, maxLength: 60 };
export const LAYER_SCALE = { min: 0.25, max: 4 };

let lastId = 0;

export function newBar(): PhotoLayer {
  // 셀카에서 눈이 대개 이 높이쯤에 온다
  return { id: ++lastId, kind: 'bar', x: 0.5, y: 0.42, scale: 1, rotation: 0 };
}

export function newText(text: string, font: TextFont): PhotoLayer {
  return { id: ++lastId, kind: 'text', x: 0.5, y: 0.74, scale: 1, rotation: 0, text, font };
}

/** 줄바꿈·연속 공백을 한 칸으로 (입력에서 줄바꿈 키는 '완료') */
export const cleanLayerText = (text: string) => text.replace(/\s+/g, ' ').trim();
