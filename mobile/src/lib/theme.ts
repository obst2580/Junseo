export const colors = {
  bg: '#000000',
  surface: '#141414',
  surfaceHigh: '#222222',
  border: '#2c2c2c',
  text: '#ffffff',
  textDim: '#9a9a9a',
  textFaint: '#5c5c5c',
  accent: '#FFC83D',
  accentText: '#1a1300',
  danger: '#ff5a5a',
} as const;

export const radius = {
  photo: 44,
  card: 20,
  pill: 999,
} as const;

// 이모지 반응. 서버(ReactionEmojis.java)도 이 다섯 개만 받는다.
export const QUICK_EMOJIS = ['❤️', '😂', '😢', '👍', '🖕'] as const;

const AVATAR_COLORS = ['#FF8A65', '#4FC3F7', '#AED581', '#BA68C8', '#FFD54F', '#4DB6AC', '#F06292', '#7986CB'];

export function avatarColor(userId: number): string {
  return AVATAR_COLORS[Math.abs(userId) % AVATAR_COLORS.length];
}
