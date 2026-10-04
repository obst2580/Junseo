import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import { androidWidget } from './androidWidget';

const KEY = 'junseo.accessToken';
const options: SecureStore.SecureStoreOptions = {
  keychainService: 'junseo.auth',
  keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY,
};

/**
 * 앱·위젯·알림 확장은 동일한 공유 Keychain에 접근한다. 웹 미리보기는 탭 세션만 유지한다.
 * 토큰 저장에 실패하면 로그인을 완료하지 않는다.
 */
export const tokenStore = {
  async get(): Promise<string | null> {
    try {
      if (Platform.OS === 'web') {
        globalThis.localStorage?.removeItem(KEY);
        return globalThis.sessionStorage?.getItem(KEY) ?? null;
      }
      if (androidWidget) return await androidWidget.getTokenAsync();
      // 예전 버전의 앱 전용 Keychain 항목 (위젯과 나누지 않던 것). 지금은 쓰지 않으니 남겨 두지 않는다.
      await SecureStore.deleteItemAsync(KEY).catch(() => {});
      return await SecureStore.getItemAsync(KEY, options);
    } catch {
      return null;
    }
  },
  async set(token: string | null): Promise<void> {
      if (Platform.OS === 'web') {
        globalThis.localStorage?.removeItem(KEY);
        if (token) globalThis.sessionStorage?.setItem(KEY, token);
        else globalThis.sessionStorage?.removeItem(KEY);
        return;
      }
      if (androidWidget) {
        await androidWidget.setTokenAsync(token);
        await SecureStore.deleteItemAsync(KEY, options);
        await SecureStore.deleteItemAsync(KEY);
        return;
      }
      if (token) await SecureStore.setItemAsync(KEY, token, options);
      else await SecureStore.deleteItemAsync(KEY, options);
      await SecureStore.deleteItemAsync(KEY); // Remove the old app-only token.
  },
};
