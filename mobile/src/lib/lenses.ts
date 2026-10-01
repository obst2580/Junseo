// expo-camera 는 렌즈를 iOS 의 기기 이름(localizedName)으로 고른다. 이 이름은 기기 언어에 따라 바뀐다.
// 예: "Back Ultra Wide Camera" / "후면 초광각 카메라", "Back Camera" / "후면 카메라".
const ULTRA_WIDE = /ultra\s?wide|초광각|超広角|超广角|超廣角/i;
const PLAIN_WIDE = /^(back camera|후면 카메라|背面カメラ|后置相机|後置相機)$/i;

export type Zoom = 'ultra' | 'wide';

/** 광각(0.5배) 렌즈와 1배 렌즈를 찾는다. 초광각이 없으면(전면 카메라, 일부 기종) 광각 버튼을 숨긴다. */
export function pickLenses(lenses: string[]): { ultra?: string; wide?: string } {
  return {
    ultra: lenses.find((l) => ULTRA_WIDE.test(l)),
    // 못 찾으면 undefined → expo-camera 가 기본 후면 카메라(1배)를 쓴다.
    wide: lenses.find((l) => PLAIN_WIDE.test(l)),
  };
}
