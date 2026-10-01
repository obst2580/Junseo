import Constants from 'expo-constants';
import mobileAds, { AdEventType, RewardedAd, RewardedAdEventType, TestIds } from 'react-native-google-mobile-ads';

// 실제 광고 단위는 app.config.js 의 ADMOB_IOS_REWARDED_ID. 비어 있으면 Google 테스트 광고가 나온다.
const unitId = ((Constants.expoConfig?.extra ?? {}) as { admobRewardedId?: string }).admobRewardedId || TestIds.REWARDED;

let ready: Promise<unknown> | null = null;

/**
 * 보상형 광고를 끝까지 보면 true, 중간에 닫으면 false.
 * 맞춤 광고를 쓰지 않아서 추적 허용(ATT) 창은 띄우지 않는다.
 */
export async function showRewardedAd(): Promise<boolean> {
  ready ??= mobileAds().initialize();
  await ready;
  return new Promise<boolean>((resolve, reject) => {
    const ad = RewardedAd.createForAdRequest(unitId, { requestNonPersonalizedAdsOnly: true });
    let earned = false;
    const offs: (() => void)[] = [];
    const finish = (run: () => void) => {
      offs.forEach((off) => off());
      run();
    };
    offs.push(
      ad.addAdEventListener(RewardedAdEventType.LOADED, () => {
        ad.show().catch((e) => finish(() => reject(e)));
      }),
      ad.addAdEventListener(RewardedAdEventType.EARNED_REWARD, () => {
        earned = true;
      }),
      ad.addAdEventListener(AdEventType.CLOSED, () => finish(() => resolve(earned))),
      ad.addAdEventListener(AdEventType.ERROR, (e) => finish(() => reject(e))),
    );
    ad.load();
  });
}
