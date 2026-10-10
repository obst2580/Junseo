// 게임 모드 목록. 엔진과 프롬프트는 상태의 setup.mode로 모드를 찾는다.
import kingdom from './kingdom.js';
import business from './business.js';

export const MODES = { kingdom, business };

export function getMode(id) {
  return MODES[id] || kingdom;
}
