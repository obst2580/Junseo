import { requireOptionalNativeModule } from 'expo-modules-core';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

import { androidWidget } from './androidWidget';

// iOS: WidgetCenter 로 홈 화면에 놓인 위젯 수를 본다 (modules/junseo-widget/ios). 예전 빌드에는 없을 수 있다.
const iosStatus = Platform.OS === 'ios'
  ? requireOptionalNativeModule<{ installedCountAsync(): Promise<number> }>('JunseoWidgetStatus')
  : null;

/** 홈 화면에 놓인 junseo 위젯 수. 알 수 없으면 null (웹 · 예전 빌드 · iOS 가 답하지 않을 때). */
export async function installedWidgets(): Promise<number | null> {
  try {
    if (androidWidget) return androidWidget.installedCount();
    if (iosStatus) {
      const count = await iosStatus.installedCountAsync();
      return count >= 0 ? count : null;
    }
  } catch {
    // 모르면 모르는 대로 (위젯 안내는 그래도 보여 줄 수 있다)
  }
  return null;
}

/** 이 기기에서 이 사람이 위젯 안내를 마쳤거나(놓음) 「나중에」를 눌렀는지. 다른 기기 · 다른 계정은 따로. */
const doneKey = (userId: number) => `junseo.widgetSetup.${userId}`;

export async function markWidgetSetupDone(userId: number): Promise<void> {
  if (Platform.OS === 'web') return;
  await SecureStore.setItemAsync(doneKey(userId), '1').catch(() => {});
}

/**
 * 가입 · 로그인 직후 위젯 안내를 띄울지: 앱의 핵심이 홈 화면 위젯이라, 아직 하나도 놓지 않았으면 바로 놓게 한다.
 * 이미 놓았거나 「나중에」를 눌렀으면 다시 띄우지 않는다 (내 정보 → 홈 화면에 위젯 추가하기에서 언제든 열 수 있다).
 */
export async function shouldOfferWidgetSetup(userId: number): Promise<boolean> {
  if (Platform.OS === 'web') return false;
  const done = await SecureStore.getItemAsync(doneKey(userId)).catch(() => null);
  if (done) return false;
  const count = await installedWidgets();
  if (count !== null && count > 0) {
    await markWidgetSetupDone(userId);
    return false;
  }
  return true;
}
