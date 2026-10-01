/**
 * 「북한」 글자 모양: 북한 선전 구호(「동무는 천리마를 탔는가?」)처럼 쓴다.
 * 참고 사진과 글자마다 견줘 맞춘 값이다. 디자인 랩도 같은 값을 쓴다.
 * - 글꼴: 은 궁서 (궁서체). 궁체 붓글씨라 획 굵기의 강약과 뾰족한 획 끝이 참고 글씨와 같다
 * - 조사(는·를…)와 물음으로 끝나는 마지막 낱말(탔는가?)은 큰 글자의 0.68배
 * - 마지막 큰 낱말(천리마)은 1.06배에 자간을 0.11em 더 넓게
 * - 은 궁서는 글자 양옆 여백이 넓어서 낱말 안 자간 -0.08em, 낱말 사이 0.28em, 가로 0.96배
 *   → 글자 사이 간격이 참고와 0~3px 안으로 맞는다
 * - 한쪽 2.5%씩 굵게, 붉은색 (240,0,0)
 */
export const NK = {
  /** 큰 글자 크기 (사진 한 변 대비) */
  size: 0.08,
  small: 0.68,
  hero: 1.06,
  heroTrack: 0.03,
  track: -0.08,
  gap: 0.28,
  bold: 0.025,
  lineHeight: 1.35,
  condense: 0.96,
  color: '#f00000',
  /** 은 궁서 띄어쓰기 폭 (em) */
  spaceWidth: 0.225,
};

/** 크기(scale: 큰 글자 대비)와 자간(track: em)이 같은 글자 조각 */
export type NkPiece = { text: string; scale: number; track: number };

const PARTICLES = ['에게서', '으로서', '으로써', '에서는', '에게는', '이라고', '에게', '께서', '에서', '으로', '처럼', '까지', '부터', '마저', '조차', '보다', '하고', '라고', '이며', '이나', '은', '는', '이', '가', '을', '를', '의', '에', '도', '로', '와', '과', '만'];
const QUESTION = /(는가|은가|는지|느냐|냐|니|나|까|요)$/;

/** 띄어쓰기 단위로 나눈 낱말마다 조각 목록 */
export function nkWords(text: string): NkPiece[][] {
  const words = text.split(' ').filter(Boolean);
  // 문장부호 조각 → 바로 앞 조각 (크기를 따른다)
  const follows = new Map<NkPiece, NkPiece>();
  const out = words.map((word, i) => {
    const [, core = '', punct = ''] = word.match(/^(.*?)([?!.,~…]*)$/) ?? [];
    const pieces: NkPiece[] = [];
    if (core) {
      if (i === words.length - 1 && words.length > 1 && QUESTION.test(core)) {
        pieces.push({ text: core, scale: NK.small, track: NK.track });
      } else {
        const particle = PARTICLES.find((p) => core.length > p.length && core.endsWith(p));
        if (particle) {
          pieces.push({ text: core.slice(0, -particle.length), scale: 1, track: NK.track }, { text: particle, scale: NK.small, track: NK.track });
        } else {
          pieces.push({ text: core, scale: 1, track: NK.track });
        }
      }
    }
    if (punct) {
      const mark = { text: punct, scale: 1, track: 0 };
      const before = pieces.at(-1);
      if (before) follows.set(mark, before);
      pieces.push(mark);
    }
    return pieces;
  });
  // 마지막 큰 낱말을 더 크고 넓게. 문장부호는 바로 앞 글자 크기를 따른다.
  const hero = out.flat().filter((p) => p.scale === 1 && !follows.has(p)).at(-1);
  if (hero) Object.assign(hero, { scale: NK.hero, track: NK.heroTrack });
  for (const [mark, before] of follows) mark.scale = before.scale;
  return out;
}
