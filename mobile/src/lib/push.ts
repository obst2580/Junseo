import * as Application from 'expo-application';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { router, useFocusEffect, type Href } from 'expo-router';
import { useCallback } from 'react';
import { AppState, Platform } from 'react-native';

import { api } from './api';
import { dmKey, groupKey } from './chats';
import { APNS_ENVIRONMENT } from './config';
import { widgetBridge } from './widgetBridge';
import { androidWidget } from './androidWidget';

let registeredToken: string | null = null;
/** 지금 화면에 떠 있는 대화 (dm:12, group:7) */
let openChat: string | null = null;

// 앱이 켜져 있을 때 온 알림도 배너로 보여준다. 단, 지금 보고 있는 대화의 메시지는 화면에 이미 뜨므로 띄우지 않는다.
Notifications.setNotificationHandler({
  handleNotification: async (n) => {
    const here = openChat !== null && chatKeyOf(n.request.content.data as PushData) === openChat;
    return { shouldShowBanner: !here, shouldShowList: !here, shouldPlaySound: !here, shouldSetBadge: false };
  },
});

async function apnsEnvironment(): Promise<'development' | 'production'> {
  const env = await Application.getIosPushNotificationServiceEnvironmentAsync().catch(() => null);
  return env === 'production' || env === 'development' ? env : APNS_ENVIRONMENT;
}

/** 알림 권한을 받고 APNs 기기 토큰을 서버에 등록한다. 사진이 위젯에 빨리 뜨는 핵심 경로다. */
export async function registerPush(): Promise<'granted' | 'denied' | 'unavailable'> {
  if (Platform.OS === 'web' || (Platform.OS === 'ios' && !Device.isDevice)) return 'unavailable';
  if (Platform.OS === 'android') {
    if (!Constants.expoConfig?.extra?.androidPushConfigured) return 'unavailable';
    await Notifications.setNotificationChannelAsync('junseo', {
      name: '사진과 메시지', importance: Notifications.AndroidImportance.HIGH,
      vibrationPattern: [0, 250, 250, 250], lightColor: '#29ff01',
    });
  }

  let { status } = await Notifications.getPermissionsAsync();
  if (status !== 'granted') {
    ({ status } = await Notifications.requestPermissionsAsync({
      ios: { allowAlert: true, allowSound: true, allowBadge: false },
    }));
  }
  if (status !== 'granted') return 'denied';

  const environment = Platform.OS === 'android' ? 'production' : await apnsEnvironment();
  const { data } = await Notifications.getDevicePushTokenAsync();
  registeredToken = String(data);
  await api.registerDevice(registeredToken, 'app', environment, Platform.OS === 'android' ? 'android' : 'ios');
  androidWidget?.rememberPushToken(registeredToken);

  // iOS 26 위젯 푸시 토큰: 위젯 확장이 직접 등록하지만, 실패했으면 앱이 대신 올린다.
  const widgetToken = widgetBridge.pendingWidgetPushToken();
  if (widgetToken) {
    await api.registerDevice(widgetToken, 'widget', environment);
    widgetBridge.markWidgetPushTokenRegistered(widgetToken);
  }
  return 'granted';
}

export async function unregisterPush() {
  const token = registeredToken ?? androidWidget?.pushToken();
  if (token) await api.unregisterDevice(token);
  registeredToken = null;
}

type PushData = { type?: string; momentId?: number | string; peerId?: number | string; groupId?: number | string };

function chatKeyOf(data: PushData | undefined): string | null {
  if (data?.type === 'message' && data.peerId) return dmKey(Number(data.peerId));
  if (data?.type === 'group-message' && data.groupId) return groupKey(Number(data.groupId));
  return null;
}

/** 이 대화로 와 있던 알림을 알림 센터에서 걷어 낸다 (방에 들어와 읽었으니) */
async function clearChatNotifications(key: string) {
  if (Platform.OS === 'web') return;
  const shown = await Notifications.getPresentedNotificationsAsync().catch(() => []);
  await Promise.all(
    shown
      .filter((n) => chatKeyOf(n.request.content.data as PushData) === key)
      .map((n) => Notifications.dismissNotificationAsync(n.request.identifier).catch(() => {})),
  );
}

/** 대화 화면이 쓴다: 보고 있는 동안 그 방 알림은 띄우지 않고, 들어오거나 앱으로 돌아올 때 쌓인 알림을 지운다. */
export function useOpenChat(key: string) {
  useFocusEffect(
    useCallback(() => {
      openChat = key;
      clearChatNotifications(key);
      const sub = AppState.addEventListener('change', (state) => state === 'active' && clearChatNotifications(key));
      return () => {
        sub.remove();
        if (openChat === key) openChat = null;
      };
    }, [key]),
  );
}

function hrefFor(data: PushData): Href | null {
  if (data.type === 'message' && data.peerId) return `/messages/${data.peerId}`;
  if (data.type === 'group-message' && data.groupId) return `/messages/group/${data.groupId}`;
  if (data.momentId) return `/moments/${data.momentId}`;
  return null;
}

/** 알림을 눌렀을 때 해당 화면으로 보내고, 앱이 켜져 있을 때 온 알림으로 위젯을 갱신한다. */
export function listenPush(onReceived: (data: PushData) => void) {
  // 웹(개발용 미리보기)에는 알림 응답 API 가 없다.
  if (Platform.OS === 'web') return () => {};
  const open = (response: Notifications.NotificationResponse | null) => {
    const href = response && hrefFor(response.notification.request.content.data as PushData);
    if (href) router.push(href);
  };
  open(Notifications.getLastNotificationResponse());

  const tapSub = Notifications.addNotificationResponseReceivedListener(open);
  const tokenSub = Notifications.addPushTokenListener((token) => {
    const next = String(token.data);
    void (async () => {
      const environment = Platform.OS === 'android' ? 'production' : await apnsEnvironment();
      await api.registerDevice(next, 'app', environment, Platform.OS === 'android' ? 'android' : 'ios');
      registeredToken = next;
      androidWidget?.rememberPushToken(next);
    })().catch(() => {});
  });
  const receiveSub = Notifications.addNotificationReceivedListener((n) => {
    widgetBridge.reload();
    onReceived(n.request.content.data as PushData);
  });
  return () => {
    tapSub.remove();
    tokenSub.remove();
    receiveSub.remove();
  };
}
