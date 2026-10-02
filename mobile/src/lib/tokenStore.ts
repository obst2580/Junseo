import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

const KEY = 'junseo.accessToken';

/**
 * 로그인 토큰 저장소 (앱: 키체인, 웹: localStorage).
 * 키체인이 실패해도(권한 없는 빌드, 기기 잠금 중 등) 앱이 멈추지 않게 오류는 「저장된 것 없음」으로 넘긴다 —
 * 읽기가 실패하면 로그인 화면이 뜨고, 쓰기가 실패해도 이번 실행 동안은 로그인 상태가 유지된다.
 */
export const tokenStore = {
  async get(): Promise<string | null> {
    try {
      if (Platform.OS === 'web') return globalThis.localStorage?.getItem(KEY) ?? null;
      return await SecureStore.getItemAsync(KEY);
    } catch (e) {
      console.warn('토큰을 읽지 못했어요', e);
      return null;
    }
  },
  async set(token: string | null): Promise<void> {
    try {
      if (Platform.OS === 'web') {
        if (token) globalThis.localStorage?.setItem(KEY, token);
        else globalThis.localStorage?.removeItem(KEY);
        return;
      }
      if (token) await SecureStore.setItemAsync(KEY, token);
      else await SecureStore.deleteItemAsync(KEY);
    } catch (e) {
      console.warn('토큰을 저장하지 못했어요', e);
    }
  },
};
