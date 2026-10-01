import * as Application from 'expo-application';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { router, type Href } from 'expo-router';
import { Platform } from 'react-native';

import { api } from './api';
import { APNS_ENVIRONMENT } from './config';
import { widgetBridge } from './widgetBridge';

let registeredToken: string | null = null;

// 앱이 켜져 있을 때 온 알림도 배너로 보여준다.
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

async function apnsEnvironment(): Promise<'development' | 'production'> {
  const env = await Application.getIosPushNotificationServiceEnvironmentAsync().catch(() => null);
  return env === 'production' || env === 'development' ? env : APNS_ENVIRONMENT;
}

/** 알림 권한을 받고 APNs 기기 토큰을 서버에 등록한다. 사진이 위젯에 빨리 뜨는 핵심 경로다. */
export async function registerPush(): Promise<'granted' | 'denied' | 'unavailable'> {
  if (Platform.OS !== 'ios' || !Device.isDevice) return 'unavailable';

  let { status } = await Notifications.getPermissionsAsync();
  if (status !== 'granted') {
    ({ status } = await Notifications.requestPermissionsAsync({
      ios: { allowAlert: true, allowSound: true, allowBadge: false },
    }));
  }
  if (status !== 'granted') return 'denied';

  const environment = await apnsEnvironment();
  const { data } = await Notifications.getDevicePushTokenAsync();
  registeredToken = String(data);
  await api.registerDevice(registeredToken, 'app', environment);

  // iOS 26 위젯 푸시 토큰: 위젯 확장이 직접 등록하지만, 실패했으면 앱이 대신 올린다.
  const widgetToken = widgetBridge.pendingWidgetPushToken();
  if (widgetToken) {
    await api.registerDevice(widgetToken, 'widget', environment);
    widgetBridge.markWidgetPushTokenRegistered(widgetToken);
  }
  return 'granted';
}

export async function unregisterPush() {
  if (registeredToken) await api.unregisterDevice(registeredToken);
  registeredToken = null;
}

type PushData = { type?: string; momentId?: number | string; peerId?: number | string };

function hrefFor(data: PushData): Href | null {
  if (data.type === 'message' && data.peerId) return `/messages/${data.peerId}`;
  if (data.momentId) return `/moments/${data.momentId}`;
  return null;
}

/** 알림을 눌렀을 때 해당 화면으로 보내고, 앱이 켜져 있을 때 온 알림으로 위젯을 갱신한다. */
export function listenPush(onReceived: (data: PushData) => void) {
  const open = (response: Notifications.NotificationResponse | null) => {
    const href = response && hrefFor(response.notification.request.content.data as PushData);
    if (href) router.push(href);
  };
  open(Notifications.getLastNotificationResponse());

  const tapSub = Notifications.addNotificationResponseReceivedListener(open);
  const receiveSub = Notifications.addNotificationReceivedListener((n) => {
    widgetBridge.reload();
    onReceived(n.request.content.data as PushData);
  });
  return () => {
    tapSub.remove();
    receiveSub.remove();
  };
}
