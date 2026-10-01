import { Platform, type ViewStyle } from 'react-native';

/**
 * 디자인 랩에 저장한 디자인 (2026-10-01): 바탕 「그라데이션」, 강조색 = 로고 초록.
 * 카드·버튼은 반투명이라 바탕의 빛이 비친다. 바텀 시트처럼 다른 화면 위에 뜨는 것은 sheet(불투명)를 쓴다.
 */
export const colors = {
  bg: '#0e0d0c',
  surface: 'rgba(255,255,255,0.08)',
  surfaceHigh: 'rgba(255,255,255,0.13)',
  border: 'rgba(255,255,255,0.10)',
  text: '#ffffff',
  textDim: 'rgba(255,255,255,0.64)',
  textFaint: 'rgba(255,255,255,0.40)',
  /** 입력칸처럼 바탕보다 한 단 들어간 곳 */
  sunk: 'rgba(0,0,0,0.32)',
  /** 바텀 시트 등 위에 뜨는 판 (surface 를 bg 위에 겹친 색) */
  sheet: '#21201f',
  /** 떠 있는 탭 바 */
  bar: 'rgba(14,13,12,0.58)',
  /** 로고 초록 */
  accent: '#29FF01',
  accentText: '#1a1300',
  danger: '#ff5a5a',
} as const;

export const radius = {
  photo: 60,
  card: 20,
  /** 버튼 모양 「각진」 */
  button: 6,
  pill: 999,
} as const;

/** 아이콘: 크기 25, 선 굵기 2.75 (components/Icon.tsx) */
export const icon = { size: 25, stroke: 2.75 } as const;

/**
 * 움직임: 「빠르게」 곡선, 길이 ×1.8, 누를 때 3% 줄어든다.
 * 길이는 디자인 랩의 기준 길이(ms)에 speed 를 곱한 값이다.
 */
const SPEED = 1.8;
export const motion = {
  speed: SPEED,
  press: 0.97,
  /** cubic-bezier(0.2, 0.9, 0.1, 1) */
  curve: [0.2, 0.9, 0.1, 1] as [number, number, number, number],
  ms: (base: 'press' | 'screen' | 'reaction' | 'shutter' | 'send') =>
    Math.round({ press: 160, screen: 300, reaction: 560, shutter: 380, send: 700 }[base] * SPEED),
};

// 그라데이션 바탕: 왼쪽 위에 강조색 빛, 오른쪽 아래에 청록 빛 (디자인 랩의 homeWall 과 같은 식)
const GLOW =
  'radial-gradient(110% 70% at 15% 0%, rgba(53,98,22,1) 0%, rgba(53,98,22,0) 62%), ' +
  'radial-gradient(90% 70% at 100% 100%, rgba(22,50,58,1) 0%, rgba(22,50,58,0) 60%)';
/** 화면 바탕. 웹은 CSS 그대로, 앱은 RN 의 experimental_backgroundImage. */
export const glow: ViewStyle = {
  backgroundColor: colors.bg,
  ...(Platform.OS === 'web' ? ({ backgroundImage: GLOW } as ViewStyle) : { experimental_backgroundImage: GLOW }),
};

// 이모지 반응. 서버(ReactionEmojis.java)도 이 다섯 개만 받는다.
export const QUICK_EMOJIS = ['❤️', '😂', '😢', '👍', '🖕'] as const;

const AVATAR_COLORS = ['#FF8A65', '#4FC3F7', '#AED581', '#BA68C8', '#FFD54F', '#4DB6AC', '#F06292', '#7986CB'];

export function avatarColor(userId: number): string {
  return AVATAR_COLORS[Math.abs(userId) % AVATAR_COLORS.length];
}
