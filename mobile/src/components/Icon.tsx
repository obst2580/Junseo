import type { StyleProp, ViewStyle } from 'react-native';
import Svg, { Circle, Path, Rect } from 'react-native-svg';

import { colors, icon as iconToken } from '@/lib/theme';

/**
 * 선으로 그린 아이콘 (디자인 랩과 같은 그림, 24×24 칸).
 * 굵기·크기는 저장한 디자인 값(theme.icon)을 따른다.
 */
type Shape =
  | { d: string; fill?: 'color'; stroke?: 'none' | 'ink' }
  | { circle: [number, number, number]; fill?: 'color'; stroke?: 'none' | 'ink' }
  | { rect: [number, number, number, number, number] };

const p = (d: string): Shape => ({ d });

const ICONS = {
  down: [p('M7 10l5 5 5-5')],
  up: [p('M7 14l5-5 5 5')],
  next: [p('M9.5 6l6 6-6 6')],
  back: [p('M14.5 5.5L8 12l6.5 6.5')],
  checkOn: [{ circle: [12, 12, 9], fill: 'color', stroke: 'none' }, { d: 'M8 12.2l2.7 2.7L16.2 9.4', stroke: 'ink' }],
  checkOff: [{ circle: [12, 12, 9] }],
  people: [
    { circle: [9, 8.5, 3.2] },
    p('M3.5 19c.7-3 2.9-4.6 5.5-4.6s4.8 1.6 5.5 4.6'),
    { circle: [16.6, 9.2, 2.5] },
    p('M15.6 14.4c2.3 0 4.2 1.5 4.9 4.6'),
  ],
  chat: [
    p('M4.5 5.5h15a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H10l-4.5 3.5v-3.5h-1a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z'),
    p('M8.5 11h.01M12 11h.01M15.5 11h.01'),
  ],
  chats: [
    p('M3.5 5.5h11a1 1 0 0 1 1 1v6.5a1 1 0 0 1-1 1H8.5l-3.5 2.8V14H4.5a1 1 0 0 1-1-1V6.5a1 1 0 0 1 1-1z'),
    p('M15.5 9h4a1 1 0 0 1 1 1v6.5a1 1 0 0 1-1 1h-1v2.8l-3.5-2.8H11a1 1 0 0 1-1-1V14'),
  ],
  // 카메라 전환: 원을 도는 두 화살표 (작게 보여도 또렷하게 — 굵은 선에서도 뭉개지지 않는 단순한 모양)
  flip: [p('M4.5 12a7.5 7.5 0 0 1 12.8-5.3L19.5 9'), p('M19.5 4.5V9H15'), p('M19.5 12a7.5 7.5 0 0 1-12.8 5.3L4.5 15'), p('M4.5 19.5V15H9')],
  // 플래시: 켜면 채운 번개, 끄면 번개 + 사선
  flashOn: [{ d: 'M13.2 3L5.8 13.2h5.7L10.8 21l7.4-10.2h-5.7z', fill: 'color' }],
  flashOff: [p('M13.2 3L5.8 13.2h5.7L10.8 21l7.4-10.2h-5.7z'), p('M4 4l16 16')],
  camera: [p('M4 8.5a2 2 0 0 1 2-2h2l1.5-2h5l1.5 2h2a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z'), { circle: [12, 12.5, 3.5] }],
  home: [p('M4 11l8-7 8 7v8.5a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z')],
  close: [p('M6 6l12 12M18 6L6 18')],
  // 더 보기 (···): 신고 · 차단 메뉴
  more: [
    { circle: [5.5, 12, 1.8], fill: 'color', stroke: 'none' },
    { circle: [12, 12, 1.8], fill: 'color', stroke: 'none' },
    { circle: [18.5, 12, 1.8], fill: 'color', stroke: 'none' },
  ],
  x: [{ circle: [12, 12, 8.5] }, p('M9.3 9.3l5.4 5.4M14.7 9.3l-5.4 5.4')],
  plus: [p('M12 6v12M6 12h12')],
  plane: [p('M20.5 3.5L3.5 10.2l6.8 2.7 2.7 6.8z'), p('M10.3 12.9l4.5-4.5')],
  send: [p('M12 19V6M6.5 11.5L12 6l5.5 5.5')],
  images: [{ rect: [3.5, 7, 13.5, 13.5, 2.5] }, p('M7 3.5h11a2.5 2.5 0 0 1 2.5 2.5v11'), p('M5.5 18l3.5-3.5 2.5 2.5 2-2 2.5 2.5')],
  aperture: [
    { circle: [12, 12, 8.5] },
    p('M13.96 8.6L18.84 17.05M10.04 8.6L19.79 8.6M8.07 12.0L12.95 3.55M10.04 15.4L5.16 6.95M13.96 15.4L4.21 15.4M15.93 12.0L11.05 20.45'),
  ],
  exit: [p('M14 4.5h3.5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H14'), p('M10 8l-4 4 4 4M6 12h9')],
  trash: [p('M4.5 7h15M10 11v6M14 11v6M6.5 7l.8 11.2a2 2 0 0 0 2 1.8h5.4a2 2 0 0 0 2-1.8L17.5 7M9.5 7V5a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v2')],
  share: [p('M12 4v11M7.5 8.5L12 4l4.5 4.5'), p('M5 12.5V18a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5.5')],
  copy: [{ rect: [8.5, 8.5, 11, 11, 2.5] }, p('M15.5 8.5V6.5a2 2 0 0 0-2-2h-7a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h2')],
  // 앱에만 있는 것 (같은 선 굵기·모서리로 그렸다)
  template: [{ rect: [4, 4, 16, 16, 3] }, p('M4 11.5h16M11.5 11.5V20')],
  apps: [{ rect: [4.5, 4.5, 6, 6, 1.5] }, { rect: [13.5, 4.5, 6, 6, 1.5] }, { rect: [4.5, 13.5, 6, 6, 1.5] }, { rect: [13.5, 13.5, 6, 6, 1.5] }],
  eyeOff: [
    p('M10.6 6.1A10 10 0 0 1 12 6c5 0 8.5 4.2 9.5 6-.5.9-1.6 2.5-3.2 3.8M6.6 7.6C4.6 9 3.2 10.9 2.5 12c1 1.8 4.5 6 9.5 6 1.6 0 3-.4 4.3-1.1'),
    p('M9.9 9.9a3 3 0 0 0 4.2 4.2M3.5 3.5l17 17'),
  ],
} satisfies Record<string, Shape[]>;

export type IconName = keyof typeof ICONS;

export function Icon({
  name,
  size = iconToken.size,
  color = colors.text,
  strokeWidth = iconToken.stroke,
  style,
}: {
  name: IconName;
  size?: number;
  color?: string;
  strokeWidth?: number;
  style?: StyleProp<ViewStyle>;
}) {
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" style={style} pointerEvents="none">
      {(ICONS[name] as Shape[]).map((s, i) => {
        if ('rect' in s) {
          const [x, y, w, h, r] = s.rect;
          return <Rect key={i} x={x} y={y} width={w} height={h} rx={r} {...line(color, strokeWidth)} />;
        }
        const look = {
          ...line(s.stroke === 'ink' ? colors.accentText : color, strokeWidth),
          ...(s.stroke === 'none' ? { stroke: 'none' } : {}),
          ...(s.fill === 'color' ? { fill: color } : {}),
        };
        if ('circle' in s) {
          const [cx, cy, r] = s.circle;
          return <Circle key={i} cx={cx} cy={cy} r={r} {...look} />;
        }
        return <Path key={i} d={s.d} {...look} />;
      })}
    </Svg>
  );
}

const line = (stroke: string, strokeWidth: number) =>
  ({ stroke, strokeWidth, fill: 'none', strokeLinecap: 'round', strokeLinejoin: 'round' }) as const;
