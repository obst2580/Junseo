import { ExtensionStorage } from '@bacons/apple-targets';
import { Platform } from 'react-native';

import { API_BASE_URL, APNS_ENVIRONMENT, APP_GROUP, WIDGET_KIND } from './config';

// 위젯과 알림 확장은 앱과 별도 프로세스라서, 서버에 붙는 데 필요한 값을 App Group 저장소로 넘겨준다.
// 키 이름은 targets/_shared/WidgetShared.swift 의 SharedStore 와 같아야 한다.
const Keys = {
  accessToken: 'accessToken',
  apiBaseUrl: 'apiBaseUrl',
  apnsEnvironment: 'apnsEnvironment',
  userId: 'userId',
  widgetPushToken: 'widgetPushToken',
  widgetPushTokenRegistered: 'widgetPushTokenRegistered',
} as const;

const storage = Platform.OS === 'ios' ? new ExtensionStorage(APP_GROUP) : null;

export const widgetBridge = {
  signIn(accessToken: string, userId: number) {
    if (!storage) return;
    storage.set(Keys.accessToken, accessToken);
    storage.set(Keys.apiBaseUrl, API_BASE_URL);
    storage.set(Keys.apnsEnvironment, APNS_ENVIRONMENT);
    storage.set(Keys.userId, userId);
    this.reload();
  },

  signOut() {
    if (!storage) return;
    storage.remove(Keys.accessToken);
    storage.remove(Keys.userId);
    storage.remove(Keys.widgetPushTokenRegistered);
    this.reload();
  },

  /** 앱이 화면에 떠 있을 때의 갱신 요청은 WidgetKit 예산에서 차감되지 않는다. */
  reload() {
    if (Platform.OS === 'ios') ExtensionStorage.reloadWidget(WIDGET_KIND);
  },

  /** iOS 26 위젯 확장이 받은 위젯 푸시 토큰. 확장이 서버 등록에 실패했을 때 앱이 대신 올린다. */
  pendingWidgetPushToken(): string | null {
    if (!storage) return null;
    const token = storage.get(Keys.widgetPushToken);
    if (!token || storage.get(Keys.widgetPushTokenRegistered) === token) return null;
    return token;
  },

  markWidgetPushTokenRegistered(token: string) {
    storage?.set(Keys.widgetPushTokenRegistered, token);
  },
};
